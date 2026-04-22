package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.CreateProjectDto;
import com.qsy.edifice.domain.dto.GetAllProjectListDto;
import com.qsy.edifice.domain.dto.GetMyProjectListDto;
import com.qsy.edifice.domain.dto.UpdateProjectDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.*;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目服务实现类
 */
@Slf4j
@Service
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectMemberService projectMemberService;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectStageService projectStageService;

    @Resource
    private ContractService contractService;

    @Resource
    private ProjectStageTemplateService projectStageTemplateService;

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    public Project getProjectById(Long projectId) {
        return projectMapper.selectById(projectId);
    }

    @Override
    public Project getProjectByCode(String projectCode) {
        return projectMapper.selectByProjectCode(projectCode);
    }

    @Override
    public List<Project> getProjectsByStatus(Integer projectStatus) {
        return projectMapper.selectByProjectStatus(projectStatus);
    }

    @Override
    public List<Project> getProjectsByType(Long projectType) {
        return projectMapper.selectByProjectType(projectType);
    }

    @Override
    public Page<Project> getProjectPage(Integer current, Integer pageSize) {
        return projectMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveProject(Project project) {
        return projectMapper.insert(project) > 0;
    }

    @Override
    public boolean updateProject(Project project) {
        return projectMapper.updateById(project) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProjectFull(UpdateProjectDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目ID不能为空");
        }

        // 1. 查询并更新项目基本信息
        Project project = projectMapper.selectById(dto.getProjectId());
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }

        if (StringUtils.hasText(dto.getProjectName())) {
            project.setProjectName(dto.getProjectName());
        }
        if (StringUtils.hasText(dto.getProjectCode())) {
            project.setProjectCode(dto.getProjectCode());
        }
        if (dto.getProjectType() != null) {
            project.setProjectType(dto.getProjectType());
        }
        if (dto.getProjectStatus() != null) {
            project.setProjectStatus(dto.getProjectStatus());
        }
        if (dto.getPreStartTime() != null) {
            project.setProjectStartTime(dto.getPreStartTime());
        }
        if (dto.getPreEndTime() != null) {
            project.setProjectEndTime(dto.getPreEndTime());
        }
        projectMapper.updateById(project);

        // 2. 更新合同信息
        if (dto.getContractType() != null || dto.getContractAmount() != null
                || dto.getBaseAmount() != null || dto.getBenefitRule() != null) {
            Contract contract = contractService.getContractByProjectId(dto.getProjectId());
            if (contract != null) {
                if (dto.getContractType() != null) contract.setContractType(dto.getContractType());
                if (dto.getContractAmount() != null) contract.setContractAmount(dto.getContractAmount());
                if (dto.getBaseAmount() != null) contract.setBaseAmount(dto.getBaseAmount());
                if (dto.getBenefitRule() != null) contract.setBenefitRules(dto.getBenefitRule());
                if (dto.getPreStartTime() != null) contract.setPreStartDate(dto.getPreStartTime());
                if (dto.getPreEndTime() != null) contract.setPreEndDate(dto.getPreEndTime());
                contractService.updateContract(contract);
            }
        }

        // 3. 更新成员（diff 式：仅删除变更的、只插入新增的、角色变化的则更新）
        if (dto.getProjectCharges() != null || dto.getProjectMembers() != null) {
            applyMemberDiff(dto.getProjectId(),
                    dto.getProjectCharges() != null ? dto.getProjectCharges() : Collections.emptyList(),
                    dto.getProjectMembers() != null ? dto.getProjectMembers() : Collections.emptyList());
        }
    }

    /**
     * 成员 diff 更新：保留 (userId, roleId) 相同的记录，仅处理差异
     *
     * - 经理 ID 集合和成员 ID 集合中同时出现时，以经理为准（角色优先级高）
     * - 期望集 - 当前集 = 需新增
     * - 当前集 - 期望集 = 需删除（逻辑删）
     * - 同一 userId 但 roleId 变化 = updateById
     */
    private void applyMemberDiff(Long projectId, List<Long> chargeIds, List<Long> memberIds) {
        // 期望状态：userId -> roleId（经理优先）
        Map<Long, Long> desired = new java.util.LinkedHashMap<>();
        for (Long id : memberIds) {
            if (id != null) desired.put(id, ROLE_MEMBER_ID);
        }
        for (Long id : chargeIds) {
            if (id != null) desired.put(id, ROLE_MANAGER_ID); // 覆盖成员角色
        }

        // 当前状态
        List<ProjectMember> current = projectMemberService.getProjectMembersByProjectId(projectId);
        if (current == null) current = Collections.emptyList();
        Map<Long, ProjectMember> currentByUser = current.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProjectMember::getUserId, m -> m, (a, b) -> a));

        // 1) 删除不再需要的成员
        for (ProjectMember m : current) {
            if (!desired.containsKey(m.getUserId())) {
                projectMemberService.deleteProjectMember(m.getProjectMemberId());
            }
        }

        // 2) 新增 或 更新角色
        for (Map.Entry<Long, Long> e : desired.entrySet()) {
            Long userId = e.getKey();
            Long roleId = e.getValue();
            ProjectMember existing = currentByUser.get(userId);
            if (existing == null) {
                projectMemberService.saveProjectMember(ProjectMember.builder()
                        .projectId(projectId)
                        .userId(userId)
                        .projectRole(roleId)
                        .build());
            } else if (!roleId.equals(existing.getProjectRole())) {
                existing.setProjectRole(roleId);
                projectMemberService.updateProjectMember(existing);
            }
            // 角色相同则不动，保留原 created_time 和 member_id
        }
    }

    private static final Long ROLE_MANAGER_ID = 101L;
    private static final Long ROLE_MEMBER_ID = 102L;

    @Override
    public boolean deleteProject(Long projectId) {
        return projectMapper.deleteById(projectId) > 0;
    }

    @Override
    public Page<ProjectListVo> getAllProjectPage(GetAllProjectListDto dto) {
        if (dto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        // 处理分页参数默认值
        if (dto.getCurrent() == null || dto.getCurrent() < 1) {
            dto.setCurrent(1);
        }
        if (dto.getPageSize() == null || dto.getPageSize() < 1) {
            dto.setPageSize(10);
        }

        // 构建查询条件
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();

        // 关键字搜索
        if (StringUtils.hasText(dto.getKeywords())) {
            wrapper.and(w -> w
                    .like(Project::getProjectName, dto.getKeywords())
                    .or()
                    .like(Project::getProjectCode, dto.getKeywords())
            );
        }

        // 项目状态筛选
        wrapper.eq(dto.getProjectStatus() != null, Project::getProjectStatus, dto.getProjectStatus());

        // 项目类型筛选
        wrapper.eq(dto.getProjectType() != null, Project::getProjectType, dto.getProjectType());

        // 按创建时间倒序
        wrapper.orderByDesc(Project::getCreatedTime);

        // 分页查询
        Page<Project> page = projectMapper.selectPage(new Page<>(dto.getCurrent(), dto.getPageSize()), wrapper);

        // 转换为VO
        Page<ProjectListVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<ProjectListVo> voList = page.getRecords().stream()
                .map(this::convertToListVo)
                .collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(CreateProjectDto dto, Long userId) {
        // 1. 参数校验
        if (dto == null || userId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请求参数不能为空");
        }
        if (!StringUtils.hasText(dto.getProjectName())) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目名称不能为空");
        }
        if (dto.getProjectType() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择项目类型");
        }
        if (dto.getContractType() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择合同类型");
        }
        if (dto.getContractAmount() == null || dto.getContractAmount().signum() <= 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "合同金额必须大于0");
        }
        if (dto.getContractFile() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请上传合同主文件");
        }
        if (dto.getProjectCharges() == null || dto.getProjectCharges().isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请至少选择一位项目经理");
        }

        // 2. 自动生成项目编码（如果为空）
        String projectCode = dto.getProjectCode();
        if (!StringUtils.hasText(projectCode)) {
            projectCode = "PJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }

        // 3. 插入项目
        Project project = Project.builder()
                .projectName(dto.getProjectName())
                .projectCode(projectCode)
                .projectType(dto.getProjectType())
                .projectStatus(0) // 未开始
                .isShow(1) // 默认公开
                .projectStartTime(dto.getPreStartTime())
                .projectEndTime(dto.getPreEndTime())
                .build();
        projectMapper.insert(project);
        Long projectId = project.getProjectId();

        // 4. 插入合同
        // 合同附件id列表转JSON字符串
        String otherFilesJson = null;
        if (dto.getContractOtherFiles() != null && !dto.getContractOtherFiles().isEmpty()) {
            otherFilesJson = dto.getContractOtherFiles().toString();
        }

        Contract contract = Contract.builder()
                .contractName(dto.getProjectName() + "-合同")
                .contractCode(projectCode + "-C")
                .contractType(dto.getContractType())
                .contractAmount(dto.getContractAmount())
                .contractFile(dto.getContractFile())
                .contractOtherFiles(otherFilesJson)
                .baseAmount(dto.getBaseAmount())
                .benefitRules(dto.getBenefitRule())
                .signingDate(dto.getSigningTime())
                .preStartDate(dto.getPreStartTime())
                .preEndDate(dto.getPreEndTime())
                .build();
        contract.setProjectId(projectId);
        contractService.saveContract(contract);

        // 5. 插入阶段（根据项目类型从模板生成）
        List<ProjectStageTemplate> templates = projectStageTemplateService.getEnabledByProjectTypeId(dto.getProjectType());
        if (templates != null && !templates.isEmpty()) {
            for (ProjectStageTemplate template : templates) {
                ProjectStage stage = ProjectStage.builder()
                        .projectId(projectId)
                        .stageName(template.getStageName())
                        .stageStatus(0) // 未开始
                        .stageOutput(template.getStageOutput())
                        .build();
                projectStageService.saveProjectStage(stage);
            }
        }

        // 6. 插入成员 — 合并经理和普通成员，避免重复
        // 项目经理角色id = 1，普通成员角色id = 2
        Set<Long> allMemberIds = new HashSet<>();

        // 项目经理
        for (Long chargeId : dto.getProjectCharges()) {
            ProjectMember member = ProjectMember.builder()
                    .projectId(projectId)
                    .userId(chargeId)
                    .projectRole(101L) // 项目经理
                    .build();
            projectMemberService.saveProjectMember(member);
            allMemberIds.add(chargeId);
        }

        // 普通成员
        if (dto.getProjectMembers() != null) {
            for (Long memberId : dto.getProjectMembers()) {
                if (!allMemberIds.contains(memberId)) {
                    ProjectMember member = ProjectMember.builder()
                            .projectId(projectId)
                            .userId(memberId)
                            .projectRole(102L) // 普通成员
                            .build();
                    projectMemberService.saveProjectMember(member);
                    allMemberIds.add(memberId);
                }
            }
        }

        // 创建者如果不在成员列表中，也加入
        if (!allMemberIds.contains(userId)) {
            ProjectMember creator = ProjectMember.builder()
                    .projectId(projectId)
                    .userId(userId)
                    .projectRole(101L)
                    .build();
            projectMemberService.saveProjectMember(creator);
        }

        return projectId;
    }

    @Override
    public Page<ProjectListVo> getMyProjectPage(Long userId, GetMyProjectListDto dto) {
        //1. 参数校验
        if(userId == null||dto ==null){
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        // 处理分页参数默认值
        if (dto.getCurrent() == null || dto.getCurrent() < 1) {
            dto.setCurrent(1);
        }
        if (dto.getPageSize() == null || dto.getPageSize() < 1) {
            dto.setPageSize(10);
        }

        // 2. 先查询用户参与的所有项目id
        List<Long> projectIds = projectMapper.selectProjectIdsByUserId(userId);

        if (projectIds == null || projectIds.isEmpty()) {
            return new Page<>(dto.getCurrent(), dto.getPageSize());
        }

        // 3. 构建查询条件
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Project::getProjectId, projectIds);

        // 关键字搜索
        if (StringUtils.hasText(dto.getKeywords())) {
            wrapper.and(w -> w
                    .like(Project::getProjectName, dto.getKeywords())
                    .or()
                    .like(Project::getProjectCode, dto.getKeywords())
            );
        }

        // 项目状态筛选
        wrapper.eq(dto.getProjectStatus() != null,Project::getProjectStatus, dto.getProjectStatus());


        // 4. 分页查询
        Page<Project> page = projectMapper.selectPage(new Page<>(dto.getCurrent(), dto.getPageSize()), wrapper);

        // 5. 转换为VO（使用 convertToListVo 填充关联数据）
        Page<ProjectListVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<ProjectListVo> voList = page.getRecords().stream()
                .map(this::convertToListVo)
                .collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public ProjectDetailVo getProjectDetailById(Long projectId, Long userId) {
        //1.参数校验
        if (userId == null||projectId == null) {
           throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        // 2. 查询项目基本信息
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }
        // 3. 验证用户是否参与此项目
        List<Long> projectIds = projectMapper.selectProjectIdsByUserId(userId);
        if (projectIds == null || !projectIds.contains(projectId)) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR);
        }
        return convertToDetailVo(project);
    }

    @Override
    public ProjectStatisticsVo getProjectStatistics() {
        return projectMapper.selectProjectStatistics();
    }

    @Override
    public ProjectStatisticsVo getMyProjectStatistics(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        List<Long> projectIds = projectMapper.selectProjectIdsByUserId(userId);
        ProjectStatisticsVo vo = new ProjectStatisticsVo();

        if (projectIds == null || projectIds.isEmpty()) {
            vo.setTotalCount(0L);
            vo.setNotStartedCount(0L);
            vo.setProcessingCount(0L);
            vo.setPendingAcceptanceCount(0L);
            vo.setCompletedCount(0L);
            vo.setTotalContractAmount(0.0);
            return vo;
        }

        LambdaQueryWrapper<Project> baseWrapper = new LambdaQueryWrapper<>();
        baseWrapper.in(Project::getProjectId, projectIds);

        vo.setTotalCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().in(Project::getProjectId, projectIds)));
        vo.setNotStartedCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().in(Project::getProjectId, projectIds).eq(Project::getProjectStatus, 0)));
        vo.setProcessingCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().in(Project::getProjectId, projectIds).eq(Project::getProjectStatus, 1)));
        vo.setPendingAcceptanceCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().in(Project::getProjectId, projectIds).eq(Project::getProjectStatus, 2)));
        vo.setCompletedCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().in(Project::getProjectId, projectIds).eq(Project::getProjectStatus, 4)));
        vo.setTotalContractAmount(0.0);

        return vo;
    }

    @Override
    public boolean checkProjectExists(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return false;
        }
        Project project = projectMapper.selectById(projectId);
        return project != null;
    }

    /**
     * 转换为列表VO
     */
    private ProjectListVo convertToListVo(Project project) {
        ProjectListVo vo = new ProjectListVo();
        BeanUtils.copyProperties(project,vo);

        // 项目类型
        if (project.getProjectType() != null) {
            ProjectType projectType = projectTypeService.getProjectTypeById(project.getProjectType());
            if (projectType != null) {
                vo.setProjectType(convertToProjectTypeVo(projectType));
            }
        }

        // 项目阶段
        List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(project.getProjectId());
        if (stages != null && !stages.isEmpty()) {
            // 设置最新阶段
            ProjectStage latestStage = stages.get(stages.size() - 1);
            vo.setProjectStage(convertToProjectStageVo(latestStage));
            // 设置所有阶段列表
            vo.setProjectStages(stages.stream()
                    .map(this::convertToProjectStageVo)
                    .collect(Collectors.toList()));
        }
        // 合同金额
        Contract contracts = contractService.getContractByProjectId(project.getProjectId());
        if(contracts != null ){
            vo.setContractAmount(convertToContractVo(contracts));
        }


        // 项目成员
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(project.getProjectId());
        if (members != null && !members.isEmpty()) {
            vo.setProjectMemberList(convertToMemberVoList(members));
        }

        return vo;
    }

    /**
     * 转换为详情VO
     */
    private ProjectDetailVo convertToDetailVo(Project project) {
        ProjectDetailVo vo = new ProjectDetailVo();
        BeanUtils.copyProperties(project,vo);

        // 项目类型
        if (project.getProjectType() != null) {
            ProjectType projectType = projectTypeService.getProjectTypeById(project.getProjectType());
            if (projectType != null) {
                vo.setProjectType(convertToProjectTypeVo(projectType));
            }
        }

        // 项目阶段
        List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(project.getProjectId());
        if (stages != null && !stages.isEmpty()) {
            // 设置最新阶段
            ProjectStage latestStage = stages.get(stages.size() - 1);
            vo.setProjectStage(convertToProjectStageVo(latestStage));
            // 设置所有阶段列表
            vo.setProjectStages(stages.stream()
                    .map(this::convertToProjectStageVo)
                    .collect(Collectors.toList()));
        }

        // 合同信息
        Contract contract = contractService.getContractByProjectId(project.getProjectId());
        if (contract != null) {
            vo.setContract(convertToContractVo(contract));
        }

        // 项目成员
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(project.getProjectId());
        if (members != null && !members.isEmpty()) {
            vo.setProjectMemberList(convertToMemberVoList(members));
        }

        return vo;
    }

    /**
     * 转换项目成员列表VO
     */
    private List<ProjectMemberVo> convertToMemberVoList(List<ProjectMember> members) {
        return members.stream().map(member -> {
            ProjectMemberVo vo = new ProjectMemberVo();
            vo.setProjectMemberId(member.getProjectMemberId());
            vo.setProjectId(member.getProjectId());
            vo.setUserId(member.getUserId());
            vo.setProjectRoleId(member.getProjectRole());
            // 查询用户真实姓名
            if (member.getUserId() != null) {
                SysUser user = sysUserMapper.selectById(member.getUserId());
                if (user != null) {
                    vo.setRealName(user.getRealName());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     *转换为ProjectTypeVo
     */
    private ProjectTypeVo convertToProjectTypeVo(ProjectType projectType) {
        if (projectType == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        ProjectTypeVo vo = new ProjectTypeVo();
        BeanUtils.copyProperties(projectType,vo);

        return vo;
    }

    /**
     * 转换为ProjectStageVo
     */
    private ProjectStageVo convertToProjectStageVo(ProjectStage projectStage) {
        if (projectStage == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        ProjectStageVo vo = new ProjectStageVo();
        BeanUtils.copyProperties(projectStage,vo);

        return vo;
    }
    private ContractVo convertToContractVo(Contract contract) {
        if (contract == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        ContractVo vo = new ContractVo();
        BeanUtils.copyProperties(contract,vo);

        return vo;
    }
}

