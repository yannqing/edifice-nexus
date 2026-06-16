package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.CreateProjectDto;
import com.qsy.edifice.domain.dto.GetAllProjectListDto;
import com.qsy.edifice.domain.dto.GetMyProjectListDto;
import com.qsy.edifice.domain.dto.GetProjectArchiveListDto;
import com.qsy.edifice.domain.dto.UpdateProjectDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.*;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.CollectionRecordMapper;
import com.qsy.edifice.mapper.ContractBenefitRevisionMapper;
import com.qsy.edifice.mapper.InspectionFormMapper;
import com.qsy.edifice.mapper.OutputValueMapper;
import com.qsy.edifice.mapper.ProjectFilesMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    @Resource
    private ProjectFilesMapper projectFilesMapper;

    @Resource
    private ProjectFilesService projectFilesService;

    @Resource
    private InspectionFormMapper inspectionFormMapper;

    @Resource
    private OutputValueMapper outputValueMapper;

    @Resource
    private CollectionRecordMapper collectionRecordMapper;

    @Resource
    private ContractBenefitRevisionMapper contractBenefitRevisionMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

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
        if (Objects.equals(project.getArchiveStatus(), ARCHIVE_STATUS_ARCHIVED)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "项目已归档，不能修改项目信息");
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
        // v0.4：benefitAmount 不在此处更新——必须走"效益修正"流程（写 revision 历史）；
        //        即使前端绕过 UI 传了 benefitAmount，也会在这里被忽略。
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
        if (dto.getBenefitAmount() != null) {
            log.warn("UpdateProjectFull 收到 benefitAmount={} 但已忽略；请走效益修正接口",
                    dto.getBenefitAmount());
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
        validateActiveUsers(desired.keySet());

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
    private static final Integer PROJECT_STATUS_ARCHIVED = 4;
    private static final Integer ARCHIVE_STATUS_ARCHIVED = 1;
    private static final Set<Integer> ARCHIVABLE_STAGE_STATUSES = Set.of(3, 6);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProject(Long projectId) {
        if (projectId == null) {
            return false;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return false;
        }

        String projectIdText = String.valueOf(projectId);
        List<Long> contractIds = selectIds("SELECT contract_id FROM contract WHERE project_id = ? AND is_delete = 0", projectId);
        List<Long> projectFileIds = selectIds("SELECT project_file_id FROM project_files WHERE project_id = ? AND is_delete = 0", projectIdText);
        List<Long> inspectionFormIds = selectIds("SELECT inspection_form_id FROM inspection_form WHERE project_id = ? AND is_delete = 0", projectIdText);
        List<Long> outputValueIds = selectIds("SELECT output_value_id FROM output_value WHERE project_id = ? AND is_delete = 0", projectId);
        List<Long> timesheetIds = selectIds("SELECT timesheet_id FROM timesheet WHERE project_id = ? AND is_delete = 0", projectId);
        List<Long> acceptanceIds = selectIds("SELECT acceptance_id FROM project_acceptance WHERE project_id = ? AND is_delete = 0", projectId);

        markApprovalRecordsDeleted(ApprovalBizType.FILE.getExt(), ApprovalBizType.FILE.getCode(), projectFileIds);
        markApprovalRecordsDeleted(ApprovalBizType.INSPECTION.getExt(), ApprovalBizType.INSPECTION.getCode(), inspectionFormIds);
        markApprovalRecordsDeleted(ApprovalBizType.OUTPUT.getExt(), ApprovalBizType.OUTPUT.getCode(), outputValueIds);
        markApprovalRecordsDeleted(ApprovalBizType.TIMESHEET.getExt(), ApprovalBizType.TIMESHEET.getCode(), timesheetIds);
        markApprovalRecordsDeleted(ApprovalBizType.ACCEPTANCE.getExt(), ApprovalBizType.ACCEPTANCE.getCode(), acceptanceIds);

        markDeletedByIds("output_value_distribution", "output_value_id", outputValueIds);
        markDeletedByIds("contract_benefit_revision", "contract_id", contractIds);

        markDeleted("contract", "project_id = ?", projectId);
        markDeleted("project_stage", "project_id = ?", projectId);
        markDeleted("project_member", "project_id = ?", projectId);
        markDeleted("project_files", "project_id = ?", projectIdText);
        markDeleted("inspection_form", "project_id = ?", projectIdText);
        markDeleted("output_value", "project_id = ?", projectId);
        markDeleted("timesheet", "project_id = ?", projectId);
        markDeleted("collection_record", "project_id = ?", projectId);
        markDeleted("project_acceptance", "project_id = ?", projectId);
        markDeleted("performance_restore", "project_id = ?", projectId);
        markDeleted("sys_user_role", "project_id = ?", projectId);

        return markDeleted("project", "project_id = ?", projectId) > 0;
    }

    private List<Long> selectIds(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, Long.class, args);
    }

    private int markDeleted(String table, String whereClause, Object... args) {
        List<Object> params = new ArrayList<>(List.of(args));
        return jdbcTemplate.update("""
                UPDATE %s
                SET is_delete = 1, updated_time = NOW()
                WHERE is_delete = 0 AND %s
                """.formatted(table, whereClause), params.toArray());
    }

    private void markDeletedByIds(String table, String idColumn, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbcTemplate.update("""
                UPDATE %s
                SET is_delete = 1, updated_time = NOW()
                WHERE is_delete = 0 AND %s IN (%s)
                """.formatted(table, idColumn, placeholders), ids.toArray());
    }

    private void markApprovalRecordsDeleted(String bizTypeExt, Integer approvalRecordType, List<Long> bizIds) {
        if (bizIds == null || bizIds.isEmpty()) {
            return;
        }
        String placeholders = bizIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>(bizIds);
        params.add(bizTypeExt);
        params.add(approvalRecordType);
        jdbcTemplate.update("""
                UPDATE approval_records
                SET is_delete = 1, updated_time = NOW()
                WHERE is_delete = 0
                  AND inspection_form_id IN (%s)
                  AND (biz_type_ext = ? OR approval_record_type = ?)
                """.formatted(placeholders), params.toArray());
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
        fillFileCounts(voList);

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
        Set<Long> requestedMemberIds = new HashSet<>();
        requestedMemberIds.addAll(dto.getProjectCharges());
        if (dto.getProjectMembers() != null) {
            requestedMemberIds.addAll(dto.getProjectMembers());
        }
        requestedMemberIds.add(userId);
        validateActiveUsers(requestedMemberIds);

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
                .archiveStatus(0)
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
                .benefitAmount(dto.getBenefitAmount())
                .benefitStatus(0)
                .signingDate(dto.getSigningTime())
                .preStartDate(dto.getPreStartTime())
                .preEndDate(dto.getPreEndTime())
                .build();
        contract.setProjectId(projectId);
        contractService.saveContract(contract);

        // 4.1 v0.4：如填了预计效益金额，自动写一条"首次录入"的修正历史，保持审计纯净
        if (dto.getBenefitAmount() != null && dto.getBenefitAmount().signum() >= 0) {
            ContractBenefitRevision firstRev = ContractBenefitRevision.builder()
                    .contractId(contract.getContractId())
                    .oldAmount(null)
                    .newAmount(dto.getBenefitAmount())
                    .deltaAmount(null)
                    .revisionReason("项目创建时首次录入")
                    .isFinal(0)
                    .operatorId(userId)
                    .build();
            contractBenefitRevisionMapper.insert(firstRev);
        }

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

    private void validateActiveUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> distinctIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (distinctIds.isEmpty()) {
            return;
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(distinctIds);
        Map<Long, SysUser> byId = users.stream()
                .collect(Collectors.toMap(SysUser::getUserId, user -> user, (a, b) -> a));
        for (Long userId : distinctIds) {
            SysUser user = byId.get(userId);
            if (user == null) {
                throw new BusinessException(ErrorType.USER_CANNOT_NULL, "项目成员不存在：" + userId);
            }
            if (!Objects.equals(user.getStatus(), 1) || !Objects.equals(user.getEmploymentStatus(), 1)) {
                String name = StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
                throw new BusinessException(ErrorType.ARGS_INVALID, "项目成员不可用或已离职：" + name);
            }
        }
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
        fillFileCounts(voList);

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public ProjectDetailVo getProjectDetailById(Long projectId) {
        //1.参数校验
        if (projectId == null) {
           throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        // 2. 查询项目基本信息
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
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
    public Page<ProjectArchiveVo> getArchivableProjectPage(GetProjectArchiveListDto dto) {
        return getProjectArchivePage(dto, false);
    }

    @Override
    public Page<ProjectArchiveVo> getArchivedProjectPage(GetProjectArchiveListDto dto) {
        return getProjectArchivePage(dto, true);
    }

    @Override
    public ProjectArchiveDetailVo getProjectArchiveDetail(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目ID不能为空");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }

        ProjectDetailVo projectDetail = convertToDetailVo(project);
        ProjectArchiveVo archiveVo = convertToArchiveVo(project);
        List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(projectId);
        Map<Long, ProjectStage> stageMap = (stages == null ? Collections.<ProjectStage>emptyList() : stages).stream()
                .collect(Collectors.toMap(ProjectStage::getProjectStageId, stage -> stage, (a, b) -> a));

        List<InspectionForm> inspectionForms = inspectionFormMapper.selectList(new LambdaQueryWrapper<InspectionForm>()
                .eq(InspectionForm::getProjectId, String.valueOf(projectId))
                .orderByDesc(InspectionForm::getCreatedTime));
        List<OutputValue> outputValues = outputValueMapper.selectList(new LambdaQueryWrapper<OutputValue>()
                .eq(OutputValue::getProjectId, projectId)
                .orderByDesc(OutputValue::getCreatedTime));
        List<CollectionRecord> collectionRecords = collectionRecordMapper.selectList(new LambdaQueryWrapper<CollectionRecord>()
                .eq(CollectionRecord::getProjectId, projectId)
                .orderByDesc(CollectionRecord::getCollectDate)
                .orderByDesc(CollectionRecord::getCreatedTime));
        List<ProjectFileVo> projectFiles = projectFilesService.listProjectFiles(projectId, null, null);

        ProjectArchiveDetailVo detail = new ProjectArchiveDetailVo();
        detail.setProject(projectDetail);
        detail.setArchive(archiveVo);
        detail.setInspections(toArchiveInspections(inspectionForms, stageMap));
        detail.setOutputValues(toArchiveOutputValues(outputValues, stageMap));
        detail.setCollections(toArchiveCollections(collectionRecords, stageMap));
        detail.setProjectFiles(projectFiles);
        detail.setSummary(buildArchiveSummary(archiveVo, inspectionForms, outputValues, collectionRecords, projectFiles));
        detail.setChecklist(buildArchiveChecklist(projectDetail, archiveVo, inspectionForms, outputValues, collectionRecords, projectFiles));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveProject(Long projectId, Long operatorId, String archiveRemark) {
        if (projectId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目ID不能为空");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }
        ProjectArchiveVo archiveVo = convertToArchiveVo(project);
        if (!Boolean.TRUE.equals(archiveVo.getArchiveReady())) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, archiveVo.getArchiveWarning());
        }
        if (Objects.equals(project.getArchiveStatus(), ARCHIVE_STATUS_ARCHIVED)) {
            return;
        }
        project.setArchiveStatus(ARCHIVE_STATUS_ARCHIVED);
        project.setArchiveTime(LocalDateTime.now());
        project.setArchiveUserId(operatorId);
        project.setArchiveRemark(StringUtils.hasText(archiveRemark) ? archiveRemark.trim() : null);
        project.setProjectStatus(PROJECT_STATUS_ARCHIVED);
        projectMapper.updateById(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unarchiveProject(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目ID不能为空");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }
        if (!Objects.equals(project.getArchiveStatus(), ARCHIVE_STATUS_ARCHIVED)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "只有已归档项目可以取消归档");
        }
        project.setArchiveStatus(0);
        project.setArchiveTime(null);
        project.setArchiveUserId(null);
        project.setArchiveRemark(null);
        project.setProjectStatus(1);
        projectMapper.updateById(project);
    }

    @Override
    public boolean checkProjectExists(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return false;
        }
        Project project = projectMapper.selectById(projectId);
        return project != null;
    }

    @Override
    public void ensureProjectNotArchived(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目ID不能为空");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }
        if (Objects.equals(project.getArchiveStatus(), ARCHIVE_STATUS_ARCHIVED)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "项目已归档，不能继续发起业务操作");
        }
    }

    private Page<ProjectArchiveVo> getProjectArchivePage(GetProjectArchiveListDto dto, boolean archived) {
        if (dto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        Integer current = dto.getCurrent() == null || dto.getCurrent() < 1 ? 1 : dto.getCurrent();
        Integer pageSize = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10 : dto.getPageSize();

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (archived) {
            wrapper.eq(Project::getArchiveStatus, ARCHIVE_STATUS_ARCHIVED);
        } else {
            wrapper.and(w -> w.ne(Project::getArchiveStatus, ARCHIVE_STATUS_ARCHIVED).or().isNull(Project::getArchiveStatus));
        }
        if (StringUtils.hasText(dto.getKeywords())) {
            String keyword = dto.getKeywords().trim();
            wrapper.and(w -> w.like(Project::getProjectName, keyword)
                    .or()
                    .like(Project::getProjectCode, keyword));
        }
        wrapper.eq(dto.getProjectType() != null, Project::getProjectType, dto.getProjectType());
        wrapper.orderByDesc(Project::getUpdatedTime).orderByDesc(Project::getCreatedTime);

        Page<Project> page = projectMapper.selectPage(new Page<>(current, pageSize), wrapper);
        Page<ProjectArchiveVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::convertToArchiveVo).collect(Collectors.toList()));
        return voPage;
    }

    private ProjectArchiveDetailVo.ArchiveSummaryVo buildArchiveSummary(
            ProjectArchiveVo archiveVo,
            List<InspectionForm> inspectionForms,
            List<OutputValue> outputValues,
            List<CollectionRecord> collectionRecords,
            List<ProjectFileVo> projectFiles) {
        ProjectArchiveDetailVo.ArchiveSummaryVo summary = new ProjectArchiveDetailVo.ArchiveSummaryVo();
        summary.setContractAmount(archiveVo.getContractAmount() == null ? BigDecimal.ZERO : archiveVo.getContractAmount());
        summary.setStageCount(archiveVo.getTotalStageCount());
        summary.setCompletedStageCount(archiveVo.getCompletedStageCount());
        summary.setInspectionCount(inspectionForms == null ? 0 : inspectionForms.size());
        summary.setOutputValueCount(outputValues == null ? 0 : outputValues.size());
        summary.setCollectionCount(collectionRecords == null ? 0 : collectionRecords.size());
        summary.setProjectFileCount(projectFiles == null ? 0 : projectFiles.size());
        summary.setTotalOutputAmount(sumOutput(outputValues, null));
        summary.setPaidOutputAmount(sumOutput(outputValues, 3));
        summary.setTotalCollectionAmount(sumCollection(collectionRecords));
        return summary;
    }

    private List<ProjectArchiveDetailVo.ArchiveChecklistItemVo> buildArchiveChecklist(
            ProjectDetailVo projectDetail,
            ProjectArchiveVo archiveVo,
            List<InspectionForm> inspectionForms,
            List<OutputValue> outputValues,
            List<CollectionRecord> collectionRecords,
            List<ProjectFileVo> projectFiles) {
        List<ProjectArchiveDetailVo.ArchiveChecklistItemVo> checklist = new ArrayList<>();
        boolean hasContract = projectDetail.getContract() != null;
        boolean hasContractFile = hasContract && projectDetail.getContract().getContractFile() != null;
        boolean stagesDone = archiveVo.getTotalStageCount() != null
                && archiveVo.getTotalStageCount() > 0
                && Objects.equals(archiveVo.getCompletedStageCount(), archiveVo.getTotalStageCount());
        boolean hasPendingInspection = inspectionForms != null && inspectionForms.stream()
                .anyMatch(form -> form.getInspectionFormStatus() != null
                        && (form.getInspectionFormStatus() == 0 || form.getInspectionFormStatus() == 1));
        boolean allInspectionPassed = inspectionForms != null && !inspectionForms.isEmpty()
                && inspectionForms.stream().allMatch(form -> Objects.equals(form.getInspectionFormStatus(), 3));
        boolean hasOutput = outputValues != null && !outputValues.isEmpty();
        boolean hasPaidOutput = outputValues != null && outputValues.stream().anyMatch(output -> Objects.equals(output.getStatus(), 3));
        BigDecimal collectionAmount = sumCollection(collectionRecords);
        BigDecimal contractAmount = archiveVo.getContractAmount() == null ? BigDecimal.ZERO : archiveVo.getContractAmount();
        boolean collectionCovered = contractAmount.signum() > 0 && collectionAmount.compareTo(contractAmount) >= 0;
        boolean hasFiles = projectFiles != null && !projectFiles.isEmpty();

        checklist.add(checkItem("contract", "合同信息", hasContract ? "pass" : "fail",
                hasContract ? "已关联项目合同" : "项目未关联合同"));
        checklist.add(checkItem("contract_file", "合同文件", hasContractFile ? "pass" : "warning",
                hasContractFile ? "已上传合同主文件" : "未发现合同主文件"));
        checklist.add(checkItem("stages", "阶段完成", stagesDone ? "pass" : "fail",
                "已完成 " + archiveVo.getCompletedStageCount() + " / " + archiveVo.getTotalStageCount() + " 个阶段"));
        checklist.add(checkItem("inspection", "验工单", !hasPendingInspection && allInspectionPassed ? "pass" : hasPendingInspection ? "fail" : "warning",
                hasPendingInspection ? "存在待处理验工单" : allInspectionPassed ? "验工单均已通过" : "未发现已通过验工单"));
        checklist.add(checkItem("output", "产值分配", hasPaidOutput ? "pass" : hasOutput ? "warning" : "warning",
                hasPaidOutput ? "存在已发放产值" : hasOutput ? "存在未完成发放的产值单" : "未发现产值分配单"));
        checklist.add(checkItem("collection", "回款记录", collectionCovered ? "pass" : collectionAmount.signum() > 0 ? "warning" : "warning",
                collectionCovered ? "累计回款已覆盖合同金额" : "累计回款：" + collectionAmount.stripTrailingZeros().toPlainString()));
        checklist.add(checkItem("project_files", "项目文件", hasFiles ? "pass" : "warning",
                hasFiles ? "项目文件 " + projectFiles.size() + " 个" : "未发现项目文件"));
        return checklist;
    }

    private ProjectArchiveDetailVo.ArchiveChecklistItemVo checkItem(String key, String name, String status, String description) {
        return new ProjectArchiveDetailVo.ArchiveChecklistItemVo(key, name, status, description);
    }

    private List<ProjectArchiveDetailVo.ArchiveInspectionVo> toArchiveInspections(
            List<InspectionForm> inspectionForms,
            Map<Long, ProjectStage> stageMap) {
        if (inspectionForms == null || inspectionForms.isEmpty()) {
            return Collections.emptyList();
        }
        return inspectionForms.stream().map(form -> {
            ProjectArchiveDetailVo.ArchiveInspectionVo vo = new ProjectArchiveDetailVo.ArchiveInspectionVo();
            vo.setInspectionFormId(form.getInspectionFormId());
            vo.setInspectionFormCode(form.getInspectionFormCode());
            vo.setProjectStageId(form.getProjectStageId());
            ProjectStage stage = form.getProjectStageId() == null ? null : stageMap.get(form.getProjectStageId());
            vo.setStageName(stage == null ? null : stage.getStageName());
            vo.setInspectionFormStatus(form.getInspectionFormStatus());
            if (form.getApplyUserId() != null) {
                SysUser user = sysUserMapper.selectById(form.getApplyUserId());
                vo.setApplyUserName(user == null ? null : StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
            }
            vo.setCreatedTime(form.getCreatedTime());
            return vo;
        }).collect(Collectors.toList());
    }

    private List<ProjectArchiveDetailVo.ArchiveOutputValueVo> toArchiveOutputValues(
            List<OutputValue> outputValues,
            Map<Long, ProjectStage> stageMap) {
        if (outputValues == null || outputValues.isEmpty()) {
            return Collections.emptyList();
        }
        return outputValues.stream().map(output -> {
            ProjectArchiveDetailVo.ArchiveOutputValueVo vo = new ProjectArchiveDetailVo.ArchiveOutputValueVo();
            vo.setOutputValueId(output.getOutputValueId());
            vo.setProjectStageId(output.getProjectStageId());
            ProjectStage stage = output.getProjectStageId() == null ? null : stageMap.get(output.getProjectStageId());
            vo.setStageName(stage == null ? null : stage.getStageName());
            vo.setQuarter(output.getQuarter());
            vo.setTotalAmount(output.getTotalAmount());
            vo.setStatus(output.getStatus());
            vo.setSubmitTime(output.getSubmitTime());
            vo.setPaidTime(output.getPaidTime());
            return vo;
        }).collect(Collectors.toList());
    }

    private List<ProjectArchiveDetailVo.ArchiveCollectionVo> toArchiveCollections(
            List<CollectionRecord> collectionRecords,
            Map<Long, ProjectStage> stageMap) {
        if (collectionRecords == null || collectionRecords.isEmpty()) {
            return Collections.emptyList();
        }
        return collectionRecords.stream().map(record -> {
            ProjectArchiveDetailVo.ArchiveCollectionVo vo = new ProjectArchiveDetailVo.ArchiveCollectionVo();
            vo.setCollectionRecordId(record.getCollectionRecordId());
            vo.setProjectStageId(record.getProjectStageId());
            ProjectStage stage = record.getProjectStageId() == null ? null : stageMap.get(record.getProjectStageId());
            vo.setStageName(stage == null ? null : stage.getStageName());
            vo.setAmount(record.getAmount());
            vo.setCollectDate(record.getCollectDate());
            vo.setRemark(record.getRemark());
            if (record.getRecordUserId() != null) {
                SysUser user = sysUserMapper.selectById(record.getRecordUserId());
                vo.setRecordUserName(user == null ? null : StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private BigDecimal sumOutput(List<OutputValue> outputValues, Integer status) {
        if (outputValues == null || outputValues.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return outputValues.stream()
                .filter(output -> status == null || Objects.equals(output.getStatus(), status))
                .map(OutputValue::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumCollection(List<CollectionRecord> collectionRecords) {
        if (collectionRecords == null || collectionRecords.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return collectionRecords.stream()
                .map(CollectionRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ProjectArchiveVo convertToArchiveVo(Project project) {
        ProjectArchiveVo vo = new ProjectArchiveVo();
        vo.setProjectId(project.getProjectId());
        vo.setProjectName(project.getProjectName());
        vo.setProjectCode(project.getProjectCode());
        vo.setProjectStatus(project.getProjectStatus());
        vo.setArchiveStatus(project.getArchiveStatus() == null ? 0 : project.getArchiveStatus());
        vo.setArchiveTime(project.getArchiveTime());
        vo.setArchiveUserId(project.getArchiveUserId());
        vo.setArchiveRemark(project.getArchiveRemark());
        vo.setProjectStartTime(project.getProjectStartTime());
        vo.setProjectEndTime(project.getProjectEndTime());
        vo.setUpdatedTime(project.getUpdatedTime());

        if (project.getArchiveUserId() != null) {
            SysUser user = sysUserMapper.selectById(project.getArchiveUserId());
            if (user != null) {
                vo.setArchiveUserName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
            }
        }

        if (project.getProjectType() != null) {
            ProjectType projectType = projectTypeService.getProjectTypeById(project.getProjectType());
            if (projectType != null) {
                vo.setProjectType(convertToProjectTypeVo(projectType));
            }
        }

        Contract contract = contractService.getContractByProjectId(project.getProjectId());
        if (contract != null) {
            ContractVo contractVo = convertToContractVo(contract);
            vo.setContract(contractVo);
            vo.setContractAmount(contract.getContractAmount() == null ? BigDecimal.ZERO : contract.getContractAmount());
        } else {
            vo.setContractAmount(BigDecimal.ZERO);
        }

        List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(project.getProjectId());
        int totalStageCount = stages == null ? 0 : stages.size();
        int completedStageCount = stages == null ? 0 : (int) stages.stream()
                .filter(stage -> ARCHIVABLE_STAGE_STATUSES.contains(stage.getStageStatus()))
                .count();
        vo.setTotalStageCount(totalStageCount);
        vo.setCompletedStageCount(completedStageCount);
        if (Objects.equals(project.getArchiveStatus(), ARCHIVE_STATUS_ARCHIVED)) {
            vo.setArchiveReady(false);
            vo.setArchiveWarning(null);
        } else if (totalStageCount == 0) {
            vo.setArchiveReady(false);
            vo.setArchiveWarning("项目未配置阶段，无法归档");
        } else if (completedStageCount < totalStageCount) {
            vo.setArchiveReady(false);
            vo.setArchiveWarning("仍有未完成阶段，无法归档");
        } else {
            vo.setArchiveReady(true);
            vo.setArchiveWarning(null);
        }

        Long fileCount = projectFilesMapper.selectCount(new LambdaQueryWrapper<ProjectFiles>()
                .eq(ProjectFiles::getProjectId, String.valueOf(project.getProjectId())));
        vo.setFileCount(fileCount == null ? 0 : fileCount.intValue());
        return vo;
    }

    /**
     * 批量填充 VO 的 fileCount：一次 SQL 取出所有相关项目的文件数。
     */
    private void fillFileCounts(List<ProjectListVo> list) {
        if (list == null || list.isEmpty()) return;
        Set<String> projectIdStrs = list.stream()
                .map(vo -> vo.getProjectId() == null ? null : String.valueOf(vo.getProjectId()))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        if (projectIdStrs.isEmpty()) {
            list.forEach(vo -> vo.setFileCount(0));
            return;
        }
        // project_files.project_id 是 varchar(64) 遗留列，这里按字符串查
        LambdaQueryWrapper<ProjectFiles> w = new LambdaQueryWrapper<>();
        w.in(ProjectFiles::getProjectId, projectIdStrs);
        List<ProjectFiles> rows = projectFilesMapper.selectList(w);
        Map<Long, Integer> countMap = new java.util.HashMap<>();
        for (ProjectFiles pf : rows) {
            if (pf.getProjectId() == null) continue;
            try {
                Long pid = Long.parseLong(pf.getProjectId());
                countMap.merge(pid, 1, Integer::sum);
            } catch (NumberFormatException ignored) {
            }
        }
        list.forEach(vo -> vo.setFileCount(countMap.getOrDefault(vo.getProjectId(), 0)));
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
