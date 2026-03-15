package com.qsy.edifice.service.impl;

import com.alibaba.excel.util.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qsy.edifice.domain.dto.*;
import com.qsy.edifice.domain.entity.Contract;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.entity.ProjectMember;
import com.qsy.edifice.domain.entity.ProjectStage;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.enums.ProjectStatusEnum;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.*;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.utils.ProjectUtils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService  {
    @Resource
    private  ContractMapper contractMapper;
    @Resource
    private  ProjectStageMapper projectStageMapper;
    @Resource
    private  ProjectMemberMapper projectMemberMapper;
    @Resource
    private  SysRoleMapper sysRoleMapper;

    // 默认角色ID（建议配置到 application.yml 或常量类）
    private static final Long PROJECT_MANAGER_ROLE_ID = 101L;
    private static final Long MEMBER_ROLE_ID = 102L;

    @Resource
    private ProjectMapper projectMapper;

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
    public boolean deleteProject(Long projectId) {
        return projectMapper.deleteById(projectId) > 0;
    }
    @Override
    public List<ProjectExportDto> getProjectsForExport(List<Long> projectIds) {
        if(projectIds == null || projectIds.isEmpty()){
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);

        }
        List<Project> projects =this.list(new LambdaQueryWrapper<Project>()
                .in(Project::getProjectId,projectIds)
                .eq(Project::getIsDelete,0)
                .orderByAsc(Project::getCreatedTime));
        return projects.stream().map(project -> {
            ProjectExportDto dto = new ProjectExportDto();
            dto.setProjectId(project.getProjectId());
            dto.setProjectName(project.getProjectName());
            dto.setProjectCode(project.getProjectCode());
            dto.setProjectType(project.getProjectType());
            dto.setProjectStatusText(ProjectStatusEnum.getTextByCode(project.getProjectStatus()));
            dto.setIsShowText(ProjectUtils.getIsShowText(project.getIsShow()));
            dto.setProjectStartTime(project.getProjectStartTime());
            dto.setProjectEndTime(project.getProjectEndTime());
            dto.setCreatedTime(project.getCreatedTime());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createProject(ProjectCreateDto dto, Long currentUserId) {
        // 1. 输入校验
        validateInput(dto, currentUserId);

        // 2. 构建项目主记录（ID 由 MyBatis-Plus 自动分配）
        Project project = buildProject(dto, currentUserId);
        Long projectId = System.currentTimeMillis();
        projectMapper.insert(project); // 插入后 projectId 已被填充
         projectId = project.getProjectId();

        // 3. 保存合同
        if (dto.getContracts() != null && !dto.getContracts().isEmpty()) {
            saveContracts(projectId, dto.getContracts());
        }

        // 4. 保存阶段
        if (dto.getStages() != null && !dto.getStages().isEmpty()) {
            saveStages(projectId, dto.getProjectStageDtos());
        }

        // 5. 保存成员
        if (dto.getMembers() != null && !dto.getMembers().isEmpty()) {
            saveMembers(projectId, dto.getMembers());
        }

        return true; // 成功返回 true
    }

    private void validateInput(ProjectCreateDto dto, Long currentUserId) {
        if (dto == null) {
            throw new IllegalArgumentException("项目创建参数不能为空");
        }
        if (currentUserId == null || currentUserId <= 0) {
            throw new IllegalArgumentException("当前用户ID无效");
        }

        // 校验项目名称
        if (StringUtils.isBlank(dto.getProjectName())) {
            throw new IllegalArgumentException("项目名称不能为空");
        }

        // 校验项目编码（唯一性可后续查库校验）
        if (StringUtils.isBlank(dto.getProjectCode())) {
            throw new IllegalArgumentException("项目编码不能为空");
        }

        // 校验时间逻辑
        if (dto.getProjectStartTime() != null && dto.getProjectEndTime() != null) {
            if (dto.getProjectEndTime().isBefore(dto.getProjectStartTime())) {
                throw new IllegalArgumentException("项目结束时间不能早于开始时间");
            }
        }

        // 可选：校验 projectType 是否有效（查字典表）
        // if (dto.getProjectType() == null || !validProjectTypes.contains(dto.getProjectType())) { ... }
    }


    private Project buildProject(ProjectCreateDto dto, Long currentUserId) {
        return Project.builder()
                // 注意：projectId 不设！由 MyBatis-Plus 的 IdType.ASSIGN_ID 自动生成
                .projectName(dto.getProjectName())
                .projectCode(dto.getProjectCode())
                .projectType(dto.getProjectType())
                .projectStatus(0) // 默认“未开始”
                .isShow(dto.getIsShow() != null ? dto.getIsShow() : 0) // 默认不公开
                .projectStartTime(dto.getProjectStartTime())
                .projectEndTime(dto.getProjectEndTime())
                // createdTime / updatedTime / isDelete 由 MyBatis-Plus 自动填充（FieldFill + @TableLogic）
                .build();
    }

    private void saveContracts(Long projectId, List<ContractDTO> contracts) {
        validateContractBusinessRules(contracts);

        List<Contract> contractList = contracts.stream().map(dto -> {
            Contract c = new Contract();

            // 主键由数据库自增（或雪花ID），此处不设
            c.setProjectId(projectId); // ← 关联当前项目

            c.setContractName(dto.getContractName());
            c.setContractCode(dto.getContractCode());
            c.setContractType(dto.getContractType());

            // 合同金额：虽然 DB 允许 NULL，但业务应强制非空
            if (dto.getContractAmount() == null || dto.getContractAmount() <= 0) {
                throw new IllegalArgumentException("合同金额必须大于0");
            }
            c.setContractAmount(dto.getContractAmount());

            // ⚠️ contract_file 是 NOT NULL！必须提供有效值
            if (dto.getContractFile() == null) {
                // 方案1：抛异常（要求前端必须先上传文件）
                throw new IllegalArgumentException("合同主文件不能为空");
                // 方案2：使用默认占位文件ID（如 -1），后续可更新
                // c.setContractFile(-1L);
            }
            c.setContractFile(dto.getContractFile());

            // JSON 字段：可为 null，MyBatis 会自动处理
            c.setContractOtherFiles(dto.getContractOtherFiles());

            // 效益合同特殊字段
            if (dto.getContractType() == 1) {
                if (dto.getBaseAmount() == null || dto.getBaseAmount() <= 0) {
                    throw new IllegalArgumentException("效益型合同必须填写基本收益金额");
                }
                if (StringUtils.isBlank(dto.getBenefitRules())) {
                    throw new IllegalArgumentException("效益型合同必须填写效益收益规则");
                }
                c.setBaseAmount(dto.getBaseAmount());
                c.setBenefitRules(dto.getBenefitRules());
            } else {
                // 清理非效益合同的冗余字段
                c.setBaseAmount(null);
                c.setBenefitRules(null);
            }

            // 时间字段（使用 DTO 提供的值，覆盖默认 CURRENT_TIMESTAMP）
            c.setSigningDate(dto.getSigningDate());
            c.setPreStartDate(dto.getPreStartDate());
            c.setPreEndDate(dto.getPreEndDate());

            // 逻辑删除 & 时间戳（由 DB 默认处理，也可在 Java 设置）

            c.setCreatedTime(LocalDateTime.now());
            // updatedTime 由 ON UPDATE CURRENT_TIMESTAMP 自动维护

            return c;
        }).collect(Collectors.toList());

        contractMapper.batchInsert(contractList);
    }


    private void validateContractBusinessRules(List<ContractDTO> contracts) {
        Set<String> codes = new HashSet<>();
        for (ContractDTO dto : contracts) {
            if (!codes.add(dto.getContractCode())) {
                throw new IllegalArgumentException("合同编号重复: " + dto.getContractCode());
            }

            // 校验时间顺序（如果前端传了）
            if (dto.getSigningDate() != null &&
                    dto.getPreStartDate() != null &&
                    dto.getSigningDate().isAfter(dto.getPreStartDate())) {
                throw new IllegalArgumentException("签订日期不能晚于预计开始日期");
            }
            if (dto.getPreStartDate() != null &&
                    dto.getPreEndDate() != null &&
                    dto.getPreStartDate().isAfter(dto.getPreEndDate())) {
                throw new IllegalArgumentException("预计开始日期不能晚于预计结束日期");
            }

            // 金额校验（已在 saveContracts 中做，可移除重复）
        }
    }
    private void saveStages(Long projectId, List<ProjectStageDto> stages) {
        if (CollectionUtils.isEmpty(stages)) {
            return;
        }

        // 校验阶段数据
        validateStages(stages);

        List<ProjectStage> stageList = stages.stream().map(dto -> {
            ProjectStage s = new ProjectStage();
            s.setProjectId(projectId);
            s.setStageName(dto.getStageName());
            s.setStageOutput(dto.getStageOutput());
            s.setStageStatus(0); // 默认“未开始”
            // created_time / updated_time / is_delete 由 DB 默认值处理，Java 不设
            return s;
        }).collect(Collectors.toList());

        projectStageMapper.batchInsert(stageList);
    }

    private void validateStages(List<ProjectStageDto> stages) {
        BigDecimal total = stages.stream()
                .map(ProjectStageDto::getStageOutput)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 允许微小浮点误差（如 99.99 ~ 100.01）
        if (total.compareTo(BigDecimal.valueOf(99.99)) < 0 ||
                total.compareTo(BigDecimal.valueOf(100.01)) > 0) {
            throw new IllegalArgumentException("所有阶段产值比例之和必须为 100%");
        }

        for (ProjectStageDto dto : stages) {
            if (dto.getStageOutput().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("阶段产值比例必须大于 0");
            }
            if (dto.getStageOutput().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("单个阶段产值比例不能超过 100%");
            }
        }
    }


    private void saveMembers(Long projectId, List<ProjectMemberDto> members) {
        if (CollectionUtils.isEmpty(members)) {
            return;
        }

        // 可选：校验 userId / projectRole 是否合法（如查用户是否存在、角色是否有效）
        validateMembers(members);

        List<ProjectMember> memberList = members.stream().map(dto -> {
            ProjectMember m = new ProjectMember();
            m.setProjectId(projectId);
            m.setUserId(dto.getUserId());
            m.setProjectRole(dto.getProjectRole());
            // created_time / is_delete 由 DB 默认值处理，Java 不设
            return m;
        }).collect(Collectors.toList());

        projectMemberMapper.batchInsert(memberList);
    }

    private void validateMembers(List<ProjectMemberDto> members) {
        // 示例：去重（一个用户只能加入一次）
        Set<Long> userIds = members.stream()
                .map(ProjectMemberDto::getUserId)
                .collect(Collectors.toSet());
        if (userIds.size() != members.size()) {
            throw new IllegalArgumentException("项目成员中存在重复用户");
        }

        // 可扩展：检查 userId 是否真实存在、projectRole 是否在允许范围内等
    }





}
