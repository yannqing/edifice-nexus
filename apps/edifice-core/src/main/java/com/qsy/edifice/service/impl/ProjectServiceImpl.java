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
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.mapper.InspectionFormMapper;
import com.qsy.edifice.mapper.OutputValueMapper;
import com.qsy.edifice.mapper.ProjectFilesMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    private FilesMapper filesMapper;

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

    @Value("${file.upload-common-url}")
    private String uploadCommonPath;

    @Value("${file.upload-prefix-url}")
    private String uploadPrefixPath;

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
    public Page<ProjectListVo> getLifecycleProjectPage(GetAllProjectListDto dto, Long userId, boolean canViewAll) {
        if (canViewAll) {
            return getAllProjectPage(dto);
        }
        if (userId == null || dto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        GetMyProjectListDto mineDto = new GetMyProjectListDto();
        mineDto.setKeywords(dto.getKeywords());
        mineDto.setProjectStatus(dto.getProjectStatus());
        mineDto.setCurrent(dto.getCurrent());
        mineDto.setPageSize(dto.getPageSize());
        return getMyProjectPage(userId, mineDto);
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
    public ProjectDetailVo getProjectDetailById(Long projectId, Long userId, boolean canViewAll) {
        //1.参数校验
        if (projectId == null) {
           throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        if (!canViewAll && userId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "用户ID不能为空");
        }
        // 2. 查询项目基本信息
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }
        if (!canViewAll && projectMemberService.getProjectMemberByProjectIdAndUserId(projectId, userId) == null) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "无权查看该项目详情");
        }
        return convertToDetailVo(project);
    }

    @Override
    public ProjectLifecycleVo getProjectLifecycleDetail(Long projectId, Long userId, boolean canViewAll) {
        if (projectId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目ID不能为空");
        }
        if (!canViewAll && userId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "用户ID不能为空");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }
        if (!canViewAll && projectMemberService.getProjectMemberByProjectIdAndUserId(projectId, userId) == null) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "无权查看该项目生命周期");
        }

        ProjectDetailVo projectDetail = convertToDetailVo(project);
        ProjectArchiveVo archiveVo = convertToArchiveVo(project);
        List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(projectId);
        if (stages == null) {
            stages = Collections.emptyList();
        }
        Map<Long, ProjectStage> stageMap = stages.stream()
                .filter(stage -> stage.getProjectStageId() != null)
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

        ProjectLifecycleVo vo = new ProjectLifecycleVo();
        vo.setProject(projectDetail);
        vo.setArchive(archiveVo);
        vo.setSummary(buildArchiveSummary(archiveVo, inspectionForms, outputValues, collectionRecords, projectFiles));
        vo.setStages(toLifecycleStages(stages, inspectionForms, outputValues, collectionRecords, projectFiles));
        vo.setEvents(buildLifecycleEvents(project, stages, inspectionForms, outputValues, collectionRecords, projectFiles, stageMap));
        vo.setRecentFiles(projectFiles == null ? Collections.emptyList() : projectFiles.stream()
                .sorted(Comparator.comparing(ProjectFileVo::getUpdatedTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .collect(Collectors.toList()));
        return vo;
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
            vo.setArchivedCount(0L);
            vo.setTotalContractAmount(0.0);
            return vo;
        }

        // 各状态统计排除已归档项目，archivedCount 单独统计
        LambdaQueryWrapper<Project> nonArchived = new LambdaQueryWrapper<>();
        nonArchived.in(Project::getProjectId, projectIds).ne(Project::getArchiveStatus, 1);

        vo.setTotalCount(projectMapper.selectCount(nonArchived));
        vo.setNotStartedCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().in(Project::getProjectId, projectIds).ne(Project::getArchiveStatus, 1).eq(Project::getProjectStatus, 0)));
        vo.setProcessingCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().in(Project::getProjectId, projectIds).ne(Project::getArchiveStatus, 1).eq(Project::getProjectStatus, 1)));
        vo.setPendingAcceptanceCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().in(Project::getProjectId, projectIds).ne(Project::getArchiveStatus, 1).eq(Project::getProjectStatus, 2)));
        vo.setCompletedCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().in(Project::getProjectId, projectIds).ne(Project::getArchiveStatus, 1).eq(Project::getProjectStatus, 4)));
        vo.setArchivedCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().in(Project::getProjectId, projectIds).eq(Project::getArchiveStatus, 1)));
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
    public void exportProjectArchivePackage(Long projectId, HttpServletResponse response) throws IOException {
        if (projectId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目ID不能为空");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }

        ProjectArchiveDetailVo detail = getProjectArchiveDetail(projectId);
        String fileName = sanitizeFileName(project.getProjectName() + "-" + project.getProjectCode() + "-归档资料");
        setZipResponseHeader(response, fileName);

        Set<String> usedEntryNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            addTextEntry(zip, usedEntryNames, "00-归档说明.txt", buildArchiveReadme(detail));

            Contract contract = contractService.getContractByProjectId(projectId);
            if (contract != null) {
                addFileEntry(zip, usedEntryNames, "合同文件/主合同", contract.getContractFile());
                List<Long> attachmentIds = parseFileIds(contract.getContractOtherFiles());
                for (int i = 0; i < attachmentIds.size(); i++) {
                    addFileEntry(zip, usedEntryNames, "合同文件/附件-" + (i + 1), attachmentIds.get(i));
                }
            }

            List<ProjectFiles> projectFileRows = projectFilesMapper.selectList(new LambdaQueryWrapper<ProjectFiles>()
                    .eq(ProjectFiles::getProjectId, String.valueOf(projectId))
                    .orderByAsc(ProjectFiles::getCreatedTime));
            for (ProjectFiles projectFile : projectFileRows) {
                String label = StringUtils.hasText(projectFile.getFileName()) ? projectFile.getFileName() : "项目文件";
                addFileEntry(zip, usedEntryNames, "项目文件/" + label, projectFile.getFileId());
            }

            List<InspectionForm> inspectionForms = inspectionFormMapper.selectList(new LambdaQueryWrapper<InspectionForm>()
                    .eq(InspectionForm::getProjectId, String.valueOf(projectId))
                    .orderByAsc(InspectionForm::getCreatedTime));
            for (InspectionForm form : inspectionForms) {
                List<Long> materialIds = parseFileIds(form.getFileIds());
                for (int i = 0; i < materialIds.size(); i++) {
                    String code = StringUtils.hasText(form.getInspectionFormCode()) ? form.getInspectionFormCode() : "验工单";
                    addFileEntry(zip, usedEntryNames, "验工材料/" + code + "/材料-" + (i + 1), materialIds.get(i));
                }
            }
            zip.finish();
        }
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

    private void addTextEntry(ZipOutputStream zip, Set<String> usedEntryNames, String entryName, String content) throws IOException {
        String normalized = nextZipEntryName(usedEntryNames, entryName);
        zip.putNextEntry(new ZipEntry(normalized));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void addFileEntry(ZipOutputStream zip, Set<String> usedEntryNames, String baseEntryName, Long fileId) throws IOException {
        if (fileId == null) {
            return;
        }
        com.qsy.edifice.domain.entity.Files file = filesMapper.selectById(fileId);
        if (file == null || !StringUtils.hasText(file.getFilePath())) {
            return;
        }
        Path physicalPath = resolvePhysicalFilePath(file);
        if (physicalPath == null || !java.nio.file.Files.exists(physicalPath) || !java.nio.file.Files.isRegularFile(physicalPath)) {
            log.warn("归档打包跳过缺失文件 fileId={} path={}", fileId, physicalPath);
            return;
        }
        String displayName = StringUtils.hasText(file.getDisplayName()) ? file.getDisplayName() : file.getFileName();
        String extension = StringUtils.hasText(file.getFileExtension()) ? "." + file.getFileExtension() : "";
        String entryName = sanitizeZipPath(baseEntryName);
        if (!entryName.toLowerCase().endsWith(extension.toLowerCase())) {
            entryName = entryName + extension;
        }
        if (StringUtils.hasText(displayName) && !baseEntryName.contains("/附件-") && !baseEntryName.contains("/材料-")) {
            int slash = entryName.lastIndexOf('/');
            String prefix = slash >= 0 ? entryName.substring(0, slash + 1) : "";
            entryName = prefix + sanitizeFileName(displayName);
        }
        entryName = nextZipEntryName(usedEntryNames, entryName);
        zip.putNextEntry(new ZipEntry(entryName));
        java.nio.file.Files.copy(physicalPath, zip);
        zip.closeEntry();
    }

    private Path resolvePhysicalFilePath(com.qsy.edifice.domain.entity.Files file) {
        if (!StringUtils.hasText(file.getFilePath())) {
            return null;
        }
        String relativePath = file.getFilePath().replace(uploadPrefixPath, "");
        return Paths.get(uploadCommonPath + relativePath);
    }

    private String nextZipEntryName(Set<String> usedEntryNames, String rawName) {
        String name = sanitizeZipPath(rawName);
        if (!usedEntryNames.contains(name)) {
            usedEntryNames.add(name);
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int index = 2;
        while (usedEntryNames.contains(base + "-" + index + ext)) {
            index++;
        }
        String next = base + "-" + index + ext;
        usedEntryNames.add(next);
        return next;
    }

    private List<Long> parseFileIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(raw);
        while (matcher.find()) {
            try {
                ids.add(Long.parseLong(matcher.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private String buildArchiveReadme(ProjectArchiveDetailVo detail) {
        ProjectDetailVo project = detail.getProject();
        ProjectArchiveDetailVo.ArchiveSummaryVo summary = detail.getSummary();
        return """
                项目归档资料包

                项目名称：%s
                项目编号：%s
                项目类型：%s
                归档时间：%s
                归档人：%s
                归档备注：%s

                合同金额：%s
                阶段完成：%s/%s
                验工单数量：%s
                产值单数量：%s
                已发放产值：%s
                回款记录数量：%s
                累计回款：%s
                项目文件数量：%s
                """.formatted(
                project.getProjectName(),
                project.getProjectCode(),
                project.getProjectType() == null ? "-" : project.getProjectType().getProjectTypeName(),
                detail.getArchive().getArchiveTime() == null ? "-" : detail.getArchive().getArchiveTime(),
                detail.getArchive().getArchiveUserName() == null ? "-" : detail.getArchive().getArchiveUserName(),
                detail.getArchive().getArchiveRemark() == null ? "-" : detail.getArchive().getArchiveRemark(),
                summary.getContractAmount(),
                summary.getCompletedStageCount(),
                summary.getStageCount(),
                summary.getInspectionCount(),
                summary.getOutputValueCount(),
                summary.getPaidOutputAmount(),
                summary.getCollectionCount(),
                summary.getTotalCollectionAmount(),
                summary.getProjectFileCount()
        );
    }

    private String sanitizeZipPath(String rawName) {
        String cleaned = rawName == null ? "未命名文件" : rawName;
        cleaned = cleaned.replace("\\", "/").replaceAll("/+", "/");
        return java.util.Arrays.stream(cleaned.split("/"))
                .filter(StringUtils::hasText)
                .map(this::sanitizeFileName)
                .collect(Collectors.joining("/"));
    }

    private String sanitizeFileName(String rawName) {
        String cleaned = rawName == null ? "未命名文件" : rawName.trim();
        cleaned = cleaned.replaceAll("[\\\\/:*?\"<>|]", "_");
        return StringUtils.hasText(cleaned) ? cleaned : "未命名文件";
    }

    private void setZipResponseHeader(HttpServletResponse response, String fileName) {
        response.setContentType("application/zip");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName + ".zip");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
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

    private List<ProjectLifecycleVo.LifecycleStageVo> toLifecycleStages(
            List<ProjectStage> stages,
            List<InspectionForm> inspectionForms,
            List<OutputValue> outputValues,
            List<CollectionRecord> collectionRecords,
            List<ProjectFileVo> projectFiles) {
        if (stages == null || stages.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<InspectionForm>> inspectionsByStage = (inspectionForms == null ? Collections.<InspectionForm>emptyList() : inspectionForms)
                .stream()
                .filter(item -> item.getProjectStageId() != null)
                .collect(Collectors.groupingBy(InspectionForm::getProjectStageId));
        Map<Long, List<OutputValue>> outputByStage = (outputValues == null ? Collections.<OutputValue>emptyList() : outputValues)
                .stream()
                .filter(item -> item.getProjectStageId() != null)
                .collect(Collectors.groupingBy(OutputValue::getProjectStageId));
        Map<Long, List<CollectionRecord>> collectionByStage = (collectionRecords == null ? Collections.<CollectionRecord>emptyList() : collectionRecords)
                .stream()
                .filter(item -> item.getProjectStageId() != null)
                .collect(Collectors.groupingBy(CollectionRecord::getProjectStageId));
        Map<Long, List<ProjectFileVo>> fileByStage = (projectFiles == null ? Collections.<ProjectFileVo>emptyList() : projectFiles)
                .stream()
                .filter(item -> item.getProjectStageId() != null)
                .collect(Collectors.groupingBy(ProjectFileVo::getProjectStageId));

        return stages.stream().map(stage -> {
            Long stageId = stage.getProjectStageId();
            List<InspectionForm> stageInspections = inspectionsByStage.getOrDefault(stageId, Collections.emptyList());
            List<OutputValue> stageOutputs = outputByStage.getOrDefault(stageId, Collections.emptyList());
            List<CollectionRecord> stageCollections = collectionByStage.getOrDefault(stageId, Collections.emptyList());
            List<ProjectFileVo> stageFiles = fileByStage.getOrDefault(stageId, Collections.emptyList());

            ProjectLifecycleVo.LifecycleStageVo vo = new ProjectLifecycleVo.LifecycleStageVo();
            vo.setProjectStageId(stageId);
            vo.setStageName(stage.getStageName());
            vo.setStageStatus(stage.getStageStatus());
            vo.setStageOutput(stage.getStageOutput());
            vo.setBenefitInclusionRatio(stage.getBenefitInclusionRatio());
            vo.setInspectionCount(stageInspections.size());
            vo.setLatestInspectionStatus(stageInspections.stream()
                    .sorted(Comparator.comparing(InspectionForm::getUpdatedTime,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(InspectionForm::getInspectionFormStatus)
                    .findFirst()
                    .orElse(null));
            vo.setOutputValueCount(stageOutputs.size());
            vo.setPaidOutputAmount(sumOutput(stageOutputs, 3));
            vo.setCollectionAmount(sumCollection(stageCollections));
            vo.setProjectFileCount(stageFiles.size());
            vo.setLatestActivityTime(latestTime(
                    stage.getUpdatedTime(),
                    latestUpdatedTime(stageInspections),
                    latestUpdatedTime(stageOutputs),
                    latestUpdatedTime(stageCollections),
                    latestUpdatedTime(stageFiles)
            ));
            return vo;
        }).collect(Collectors.toList());
    }

    private List<ProjectLifecycleVo.LifecycleEventVo> buildLifecycleEvents(
            Project project,
            List<ProjectStage> stages,
            List<InspectionForm> inspectionForms,
            List<OutputValue> outputValues,
            List<CollectionRecord> collectionRecords,
            List<ProjectFileVo> projectFiles,
            Map<Long, ProjectStage> stageMap) {
        List<ProjectLifecycleVo.LifecycleEventVo> events = new ArrayList<>();
        events.add(lifecycleEvent(
                "project:" + project.getProjectId() + ":created",
                "project",
                "立项",
                "项目已创建",
                project.getProjectName(),
                project.getProjectStatus(),
                null,
                "/project-lifecycle?projectId=" + project.getProjectId(),
                project.getCreatedTime()
        ));

        Contract contract = contractService.getContractByProjectId(project.getProjectId());
        if (contract != null) {
            events.add(lifecycleEvent(
                    "contract:" + contract.getContractId(),
                    "contract",
                    "合同",
                    "合同：" + nullableText(contract.getContractName(), contract.getContractCode()),
                    "合同金额：" + moneyText(contract.getContractAmount()),
                    contract.getContractType(),
                    null,
                    "/project-lifecycle?projectId=" + project.getProjectId(),
                    contract.getSigningDate() != null ? contract.getSigningDate()
                            : contract.getCreatedTime() != null ? contract.getCreatedTime()
                            : contract.getUpdatedTime()
            ));
        }

        for (ProjectStage stage : stages == null ? Collections.<ProjectStage>emptyList() : stages) {
            events.add(lifecycleEvent(
                    "stage:" + stage.getProjectStageId(),
                    "stage",
                    "阶段",
                    "阶段：" + stage.getStageName(),
                    "当前状态：" + stageStatusLabel(stage.getStageStatus()),
                    stage.getStageStatus(),
                    null,
                    "/project-lifecycle?projectId=" + project.getProjectId(),
                    stage.getUpdatedTime() == null ? stage.getCreatedTime() : stage.getUpdatedTime()
            ));
        }

        for (InspectionForm form : inspectionForms == null ? Collections.<InspectionForm>emptyList() : inspectionForms) {
            SysUser applyUser = form.getApplyUserId() == null ? null : sysUserMapper.selectById(form.getApplyUserId());
            ProjectStage stage = form.getProjectStageId() == null ? null : stageMap.get(form.getProjectStageId());
            events.add(lifecycleEvent(
                    "inspection:" + form.getInspectionFormId(),
                    "inspection",
                    "验工",
                    "验工单：" + nullableText(form.getInspectionFormCode(), "-"),
                    (stage == null ? "" : "阶段：" + stage.getStageName() + " · ") + inspectionStatusLabel(form.getInspectionFormStatus()),
                    form.getInspectionFormStatus(),
                    displayUserName(applyUser),
                    "/inspection-approval?detailId=" + form.getInspectionFormId(),
                    form.getUpdatedTime() == null ? form.getCreatedTime() : form.getUpdatedTime()
            ));
        }

        for (OutputValue output : outputValues == null ? Collections.<OutputValue>emptyList() : outputValues) {
            ProjectStage stage = output.getProjectStageId() == null ? null : stageMap.get(output.getProjectStageId());
            events.add(lifecycleEvent(
                    "output:" + output.getOutputValueId(),
                    "output",
                    "产值",
                    "产值分配：" + nullableText(output.getQuarter(), "-"),
                    (stage == null ? "" : "阶段：" + stage.getStageName() + " · ") + outputStatusLabel(output.getStatus()),
                    output.getStatus(),
                    null,
                    "/output-value?detailId=" + output.getOutputValueId(),
                    output.getPaidTime() != null ? output.getPaidTime()
                            : output.getApprovedTime() != null ? output.getApprovedTime()
                            : output.getSubmitTime() != null ? output.getSubmitTime()
                            : output.getUpdatedTime()
            ));
        }

        for (CollectionRecord record : collectionRecords == null ? Collections.<CollectionRecord>emptyList() : collectionRecords) {
            SysUser recordUser = record.getRecordUserId() == null ? null : sysUserMapper.selectById(record.getRecordUserId());
            ProjectStage stage = record.getProjectStageId() == null ? null : stageMap.get(record.getProjectStageId());
            events.add(lifecycleEvent(
                    "collection:" + record.getCollectionRecordId(),
                    "collection",
                    "回款",
                    "回款记录",
                    (stage == null ? "未关联阶段" : "阶段：" + stage.getStageName()) + " · 金额：" + moneyText(record.getAmount()),
                    null,
                    displayUserName(recordUser),
                    "/project-lifecycle?projectId=" + project.getProjectId(),
                    record.getCreatedTime()
            ));
        }

        for (ProjectFileVo file : projectFiles == null ? Collections.<ProjectFileVo>emptyList() : projectFiles) {
            events.add(lifecycleEvent(
                    "file:" + file.getProjectFileId(),
                    "file",
                    "文件",
                    "项目文件：" + nullableText(file.getFileName(), "-"),
                    nullableText(file.getStageName(), "未关联阶段") + " · " + fileStatusLabel(file.getApprovalStatus()),
                    file.getApprovalStatus(),
                    file.getUploadUserName(),
                    "/project-files/approval?detailId=" + file.getProjectFileId(),
                    file.getUpdatedTime() == null ? file.getCreatedTime() : file.getUpdatedTime()
            ));
        }

        if (Objects.equals(project.getArchiveStatus(), ARCHIVE_STATUS_ARCHIVED)) {
            SysUser archiveUser = project.getArchiveUserId() == null ? null : sysUserMapper.selectById(project.getArchiveUserId());
            events.add(lifecycleEvent(
                    "archive:" + project.getProjectId(),
                    "archive",
                    "归档",
                    "项目已归档",
                    nullableText(project.getArchiveRemark(), "项目生命周期已完成"),
                    project.getArchiveStatus(),
                    displayUserName(archiveUser),
                    "/project-lifecycle?projectId=" + project.getProjectId(),
                    project.getArchiveTime()
            ));
        }

        return events.stream()
                .filter(event -> event.getOccurredTime() != null)
                .sorted(Comparator.comparing(ProjectLifecycleVo.LifecycleEventVo::getOccurredTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(100)
                .collect(Collectors.toList());
    }

    private ProjectLifecycleVo.LifecycleEventVo lifecycleEvent(
            String eventId,
            String eventType,
            String eventTypeLabel,
            String title,
            String content,
            Integer status,
            String operatorName,
            String link,
            LocalDateTime occurredTime) {
        ProjectLifecycleVo.LifecycleEventVo vo = new ProjectLifecycleVo.LifecycleEventVo();
        vo.setEventId(eventId);
        vo.setEventType(eventType);
        vo.setEventTypeLabel(eventTypeLabel);
        vo.setTitle(title);
        vo.setContent(content);
        vo.setStatus(status);
        vo.setOperatorName(operatorName);
        vo.setLink(link);
        vo.setOccurredTime(occurredTime);
        return vo;
    }

    private <T> LocalDateTime latestUpdatedTime(List<T> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.stream()
                .map(row -> {
                    if (row instanceof InspectionForm form) return form.getUpdatedTime();
                    if (row instanceof OutputValue output) return output.getUpdatedTime();
                    if (row instanceof CollectionRecord record) return record.getUpdatedTime();
                    if (row instanceof ProjectFileVo file) return file.getUpdatedTime();
                    return null;
                })
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private LocalDateTime latestTime(LocalDateTime... values) {
        return java.util.Arrays.stream(values)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private String stageStatusLabel(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "未开始";
            case 1 -> "进行中";
            case 2 -> "待验收";
            case 3 -> "已验收";
            case 4 -> "已驳回";
            case 5 -> "待分配";
            case 6 -> "已完成";
            default -> "未知";
        };
    }

    private String inspectionStatusLabel(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "审核中";
            case 2 -> "已驳回";
            case 3 -> "已通过";
            case 4 -> "草稿";
            default -> "未知";
        };
    }

    private String outputStatusLabel(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待确认";
            case 1 -> "待审核";
            case 2 -> "已审批";
            case 3 -> "已发放";
            default -> "未知";
        };
    }

    private String fileStatusLabel(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待提交";
            case 1 -> "审批中";
            case 2 -> "通过";
            case 3 -> "驳回";
            default -> "未知";
        };
    }

    private String displayUserName(SysUser user) {
        if (user == null) return null;
        if (StringUtils.hasText(user.getRealName())) return user.getRealName();
        if (StringUtils.hasText(user.getUsername())) return user.getUsername();
        return String.valueOf(user.getUserId());
    }

    private String nullableText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String moneyText(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
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
