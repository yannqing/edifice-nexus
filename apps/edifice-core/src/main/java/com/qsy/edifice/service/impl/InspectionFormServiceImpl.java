package com.qsy.edifice.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.ApplyInspectionDto;
import com.qsy.edifice.domain.dto.ApprovalInspectionDto;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.excel.InspectionFormExcelData;
import com.qsy.edifice.domain.vo.*;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ContractMapper;
import com.qsy.edifice.mapper.ApprovalRecordsMapper;
import com.qsy.edifice.mapper.InspectionFormMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.ProjectStageMapper;
import com.qsy.edifice.mapper.ProjectTypeMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 验工单服务实现类
 */
@Slf4j
@Service
public class InspectionFormServiceImpl implements InspectionFormService {

    private static final int RECORD_PENDING = 0;
    private static final int RECORD_APPROVED = 1;
    private static final int RECORD_REJECTED = 2;

    @Resource
    private InspectionFormMapper inspectionFormMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    /** 直接注入 Mapper 用于批量查询，消灭 N+1 */
    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectTypeMapper projectTypeMapper;

    @Resource
    private ContractMapper contractMapper;

    @Resource
    private ProjectStageMapper projectStageMapper;

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectStageService projectStageService;

    @Resource
    private ContractService contractService;

    @Resource
    private ApprovalRecordsService approvalRecordsService;

    @Resource
    private ApprovalFlowService approvalFlowService;

    @Resource
    private BusinessRuleConfigService businessRuleConfigService;

    @Resource
    private ApprovalRecordsMapper approvalRecordsMapper;

    // ==================== 查询列表 ====================

    @Override
    public Page<InspectionFormListVo> getMyInspections(GetInspectionFormListDto dto, Long userId) {
        dto.setApplyUserId(userId);
        return queryInspections(dto);
    }

    @Override
    public Page<InspectionFormListVo> getMyPendingInspections(GetInspectionFormListDto dto, Long userId) {
        GetInspectionFormListDto query = dto == null ? new GetInspectionFormListDto() : dto;
        Integer current = query.getCurrent() != null ? query.getCurrent() : 1;
        Integer pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        List<Integer> recordStatuses = resolveApprovalRecordStatuses(query);
        LambdaQueryWrapper<ApprovalRecords> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(ApprovalRecords::getApprover, userId)
                .eq(ApprovalRecords::getBizTypeExt, ApprovalBizType.INSPECTION.getExt())
                .in(ApprovalRecords::getInspectionFormStatus, recordStatuses)
                .orderByDesc(ApprovalRecords::getUpdatedTime)
                .orderByDesc(ApprovalRecords::getCreatedTime);
        List<ApprovalRecords> records = approvalRecordsMapper.selectList(recordWrapper);

        Set<Long> inspectionIds = records.stream()
                .map(ApprovalRecords::getInspectionFormId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (inspectionIds.isEmpty()) {
            return new Page<>(current, pageSize, 0);
        }

        LambdaQueryWrapper<InspectionForm> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(InspectionForm::getInspectionFormId, inspectionIds);
        if (StringUtils.hasText(query.getInspectionFormCode())) {
            wrapper.like(InspectionForm::getInspectionFormCode, query.getInspectionFormCode());
        }
        if (StringUtils.hasText(query.getProjectId())) {
            wrapper.eq(InspectionForm::getProjectId, query.getProjectId());
        }
        if (shouldFilterInspectionStatus(query) && query.getInspectionFormStatuses() != null
                && !query.getInspectionFormStatuses().isEmpty()) {
            wrapper.in(InspectionForm::getInspectionFormStatus, query.getInspectionFormStatuses());
        } else if (shouldFilterInspectionStatus(query) && query.getInspectionFormStatus() != null) {
            wrapper.eq(InspectionForm::getInspectionFormStatus, query.getInspectionFormStatus());
        }

        List<InspectionForm> matchedForms = inspectionFormMapper.selectList(wrapper);
        Map<Long, InspectionForm> formMap = matchedForms.stream()
                .collect(Collectors.toMap(InspectionForm::getInspectionFormId, f -> f, (a, b) -> a));

        List<InspectionForm> orderedForms = records.stream()
                .map(ApprovalRecords::getInspectionFormId)
                .filter(Objects::nonNull)
                .distinct()
                .map(formMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int total = orderedForms.size();
        int fromIndex = Math.min(Math.max(current - 1, 0) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<InspectionForm> pageRecords = orderedForms.subList(fromIndex, toIndex);

        ListResolveCtx ctx = prefetchForList(pageRecords);

        List<InspectionFormListVo> voList = pageRecords.stream()
                .map(f -> convertToListVo(f, ctx))
                .collect(Collectors.toList());
        fillCurrentApprovalInfo(voList);

        Page<InspectionFormListVo> voPage = new Page<>(current, pageSize, total);
        voPage.setRecords(voList);
        return voPage;
    }

    private List<Integer> resolveApprovalRecordStatuses(GetInspectionFormListDto query) {
        if (query.getInspectionFormStatuses() != null && !query.getInspectionFormStatuses().isEmpty()) {
            boolean pendingMainStatuses = query.getInspectionFormStatuses().stream()
                    .allMatch(status -> status != null && (status == 0 || status == 1));
            if (pendingMainStatuses) return List.of(RECORD_PENDING);
        }
        Integer status = query.getInspectionFormStatus();
        if (status != null) {
            if (status == 3) return List.of(RECORD_APPROVED);
            if (status == 2) return List.of(RECORD_REJECTED);
            if (status == 0 || status == 1) return List.of(RECORD_PENDING);
        }
        return List.of(RECORD_PENDING, RECORD_APPROVED, RECORD_REJECTED);
    }

    private boolean shouldFilterInspectionStatus(GetInspectionFormListDto query) {
        if (query.getInspectionFormStatuses() != null && !query.getInspectionFormStatuses().isEmpty()) {
            return query.getInspectionFormStatuses().stream()
                    .anyMatch(status -> status != null && (status == 0 || status == 1));
        }
        Integer status = query.getInspectionFormStatus();
        return status != null && (status == 0 || status == 1);
    }

    @Override
    public Page<InspectionFormListVo> getAllInspections(GetInspectionFormListDto dto) {
        return queryInspections(dto);
    }

    /**
     * 通用验工单查询（my-list 和 all 共用）
     */
    private Page<InspectionFormListVo> queryInspections(GetInspectionFormListDto dto) {
        Integer current = dto.getCurrent() != null ? dto.getCurrent() : 1;
        Integer pageSize = dto.getPageSize() != null ? dto.getPageSize() : 10;

        LambdaQueryWrapper<InspectionForm> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(dto.getInspectionFormCode())) {
            wrapper.like(InspectionForm::getInspectionFormCode, dto.getInspectionFormCode());
        }
        if (StringUtils.hasText(dto.getProjectId())) {
            wrapper.eq(InspectionForm::getProjectId, dto.getProjectId());
        }
        if (dto.getInspectionFormStatuses() != null && !dto.getInspectionFormStatuses().isEmpty()) {
            wrapper.in(InspectionForm::getInspectionFormStatus, dto.getInspectionFormStatuses());
        } else if (dto.getInspectionFormStatus() != null) {
            wrapper.eq(InspectionForm::getInspectionFormStatus, dto.getInspectionFormStatus());
        }
        if (dto.getApplyUserId() != null) {
            wrapper.eq(InspectionForm::getApplyUserId, dto.getApplyUserId());
        }

        wrapper.orderByDesc(InspectionForm::getCreatedTime);

        Page<InspectionForm> page = inspectionFormMapper.selectPage(new Page<>(current, pageSize), wrapper);

        // 一次性批量预取本页所需的项目/类型/合同/阶段/用户，避免每行 N+1
        ListResolveCtx ctx = prefetchForList(page.getRecords());

        List<InspectionFormListVo> voList = page.getRecords().stream()
                .map(f -> convertToListVo(f, ctx))
                .collect(Collectors.toList());
        fillCurrentApprovalInfo(voList);

        Page<InspectionFormListVo> voPage = new Page<>(current, pageSize, page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 批量预取列表页需要的关联数据：1 次 SQL 查完 project / type / contract / stage / user
     */
    private ListResolveCtx prefetchForList(List<InspectionForm> forms) {
        if (forms == null || forms.isEmpty()) return ListResolveCtx.empty();

        Set<Long> projectIds = forms.stream()
                .map(f -> parseProjectId(f.getProjectId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> stageIds = forms.stream()
                .map(InspectionForm::getProjectStageId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = forms.stream()
                .map(InspectionForm::getApplyUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Project> projects = projectIds.isEmpty() ? Collections.emptyMap()
                : projectMapper.selectBatchIds(projectIds).stream()
                    .collect(Collectors.toMap(Project::getProjectId, p -> p, (a, b) -> a));

        Set<Long> typeIds = projects.values().stream()
                .map(Project::getProjectType).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProjectType> types = typeIds.isEmpty() ? Collections.emptyMap()
                : projectTypeMapper.selectBatchIds(typeIds).stream()
                    .collect(Collectors.toMap(ProjectType::getProjectTypeId, t -> t, (a, b) -> a));

        Map<Long, Contract> contracts = new HashMap<>();
        if (!projectIds.isEmpty()) {
            LambdaQueryWrapper<Contract> cw = new LambdaQueryWrapper<>();
            cw.in(Contract::getProjectId, projectIds);
            for (Contract c : contractMapper.selectList(cw)) {
                contracts.put(c.getProjectId(), c);
            }
        }

        Map<Long, ProjectStage> stages = stageIds.isEmpty() ? Collections.emptyMap()
                : projectStageMapper.selectBatchIds(stageIds).stream()
                    .collect(Collectors.toMap(ProjectStage::getProjectStageId, s -> s, (a, b) -> a));

        Map<Long, SysUser> users = userIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));

        return new ListResolveCtx(projects, types, contracts, stages, users);
    }

    private static Long parseProjectId(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 列表页批量预取的上下文 */
    private record ListResolveCtx(
            Map<Long, Project> projects,
            Map<Long, ProjectType> types,
            Map<Long, Contract> contracts,
            Map<Long, ProjectStage> stages,
            Map<Long, SysUser> users
    ) {
        static ListResolveCtx empty() {
            return new ListResolveCtx(Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
    }

    // ==================== 查询详情 ====================

    @Override
    public InspectionFormDetailVo getInspectionById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        InspectionForm form = inspectionFormMapper.selectById(id);
        if (form == null) {
            return null;
        }

        InspectionFormDetailVo vo = InspectionFormDetailVo.objToVo(form);

        // 填充项目信息
        fillProjectInfo(vo, form);

        // 填充申请人姓名
        if (form.getApplyUserId() != null) {
            SysUser user = sysUserMapper.selectById(form.getApplyUserId());
            if (user != null) {
                vo.setApplyUserName(user.getRealName());
            }
        }

        // 审批链（含层级 / 下一级审批人等通用字段）
        List<ApprovalRecordVo> chain = approvalFlowService.queryChain(
                ApprovalBizType.INSPECTION, form.getInspectionFormId());
        if (!chain.isEmpty()) {
            vo.setApprovalRecords(chain);
            chain.stream()
                    .filter(record -> Objects.equals(record.getInspectionFormStatus(), 0))
                    .findFirst()
                    .ifPresent(record -> fillCurrentApprovalInfo(vo, record));
        }

        return vo;
    }

    // ==================== 统计总览 ====================

    @Override
    public InspectionOverviewVo getInspectionOverview(Long applyUserId) {
        InspectionOverviewVo vo = new InspectionOverviewVo();
        vo.setPendingApproval(countByStatus(0, applyUserId));
        vo.setPendingFirstReview(countByStatus(1, applyUserId));
        vo.setApproved(countByStatus(3, applyUserId));
        vo.setRejected(countByStatus(2, applyUserId));
        return vo;
    }

    @Override
    public InspectionOverviewVo getMyPendingInspectionOverview(Long approverId) {
        InspectionOverviewVo vo = new InspectionOverviewVo();
        vo.setPendingApproval(0L);
        vo.setPendingFirstReview(0L);
        vo.setApproved(0L);
        vo.setRejected(0L);
        if (approverId == null) return vo;
        vo.setApproved(countDistinctApprovalRecordsByStatus(approverId, RECORD_APPROVED));
        vo.setRejected(countDistinctApprovalRecordsByStatus(approverId, RECORD_REJECTED));

        List<ApprovalRecordVo> pendingRecords = approvalFlowService.listPendingByApprover(
                approverId, ApprovalBizType.INSPECTION);
        Set<Long> inspectionIds = pendingRecords.stream()
                .map(ApprovalRecordVo::getBizId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (inspectionIds.isEmpty()) return vo;

        LambdaQueryWrapper<InspectionForm> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(InspectionForm::getInspectionFormId, inspectionIds);
        List<InspectionForm> forms = inspectionFormMapper.selectList(wrapper);
        vo.setPendingApproval(forms.stream().filter(f -> Objects.equals(f.getInspectionFormStatus(), 0)).count());
        vo.setPendingFirstReview(forms.stream().filter(f -> Objects.equals(f.getInspectionFormStatus(), 1)).count());
        return vo;
    }

    private long countDistinctApprovalRecordsByStatus(Long approverId, int recordStatus) {
        LambdaQueryWrapper<ApprovalRecords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecords::getApprover, approverId)
                .eq(ApprovalRecords::getBizTypeExt, ApprovalBizType.INSPECTION.getExt())
                .eq(ApprovalRecords::getInspectionFormStatus, recordStatus);
        return approvalRecordsMapper.selectList(wrapper).stream()
                .map(ApprovalRecords::getInspectionFormId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private long countByStatus(int status, Long applyUserId) {
        QueryWrapper<InspectionForm> w = new QueryWrapper<>();
        w.eq("inspection_form_status", status);
        if (applyUserId != null) {
            w.eq("apply_user_id", applyUserId);
        }
        return inspectionFormMapper.selectCount(w);
    }

    // ==================== 提交验工单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long applyInspection(ApplyInspectionDto dto, Long userId) {
        if (dto == null || userId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请求参数不能为空");
        }
        if (dto.getProjectId() == null || dto.getProjectStageId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目和阶段不能为空");
        }
        if (dto.getFirstApproverId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "一级审批人不能为空");
        }
        boolean requireMaterials = businessRuleConfigService.booleanValue(
                ApprovalBizType.INSPECTION.getExt(), "require_materials", true);
        if (requireMaterials && (!StringUtils.hasText(dto.getFileIds()) || "[]".equals(dto.getFileIds().trim()))) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请上传验收材料");
        }

        // 校验阶段状态：必须是 1(进行中) 才能提交验工
        ProjectStage stage = projectStageService.getProjectStageById(dto.getProjectStageId());
        if (stage == null) {
            throw new BusinessException(ErrorType.STAGE_NOT_FOUND);
        }
        if (stage.getStageStatus() != 1) {
            throw new BusinessException(ErrorType.STAGE_STATUS_INVALID,
                    "阶段[" + stage.getStageName() + "]当前状态不是[进行中]，无法提交验工");
        }

        // 校验该阶段是否有未完成的验工单
        List<InspectionForm> pendingForms = inspectionFormMapper.selectByProjectStageId(dto.getProjectStageId());
        if (pendingForms != null) {
            boolean hasPending = pendingForms.stream()
                    .anyMatch(f -> f.getInspectionFormStatus() == 0 || f.getInspectionFormStatus() == 1);
            if (hasPending) {
                throw new BusinessException(ErrorType.STAGE_HAS_PENDING_INSPECTION);
            }
        }

        // 生成验工单编号
        String code = "YG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        InspectionForm form = new InspectionForm();
        form.setInspectionFormCode(code);
        form.setProjectId(String.valueOf(dto.getProjectId()));
        form.setProjectStageId(dto.getProjectStageId());
        form.setInspectionFormDescription(dto.getInspectionFormDescription());
        form.setApplyUserId(userId);
        form.setInspectionFormStatus(0); // 待审核
        form.setFileIds(dto.getFileIds());

        inspectionFormMapper.insert(form);

        SubmitApprovalDto submit = new SubmitApprovalDto(
                ApprovalBizType.INSPECTION.getExt(),
                form.getInspectionFormId(),
                dto.getFirstApproverId(),
                dto.getInspectionFormDescription()
        );
        ApprovalRecords record = approvalFlowService.submit(submit, userId);
        log.info("验工单已提交审批 inspectionId={} firstApprover={} recordId={}",
                form.getInspectionFormId(), dto.getFirstApproverId(), record.getApprovalRecordId());

        // 阶段状态：1(进行中) → 2(待验收)
        stage.setStageStatus(2);
        projectStageService.updateProjectStage(stage);
        log.info("提交验工单，阶段状态变更为待验收: stageId={}, stageName={}", stage.getProjectStageId(), stage.getStageName());

        return form.getInspectionFormId();
    }

    // ==================== 审批验工单 ====================

    /**
     * 审批验工单（Phase 3 #3：走通用审批链）。
     *
     * 兼容旧调用方式：如果当前无待审节点（老数据直接 status=0），
     * 会先以当前操作人为首审做一次 submit，再立刻 approve——对外接口未变。
     *
     * 新能力：{@code dto.nextApproverId} 非空且 result=1 时，流转下一级，
     * 验工单 status 保持 审核中（1）；缺省则视为终审。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approvalInspection(ApprovalInspectionDto dto, Long userId) {
        if (dto == null || dto.getInspectionFormId() == null || dto.getResult() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批参数不能为空");
        }
        if (dto.getResult() != 1 && dto.getResult() != 2) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "审批结果必须为 1-通过 或 2-驳回");
        }
        if (!StringUtils.hasText(dto.getApprovalDescription())) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批意见不能为空");
        }
        dto.setApprovalDescription(dto.getApprovalDescription().trim());

        // 查询验工单
        InspectionForm form = inspectionFormMapper.selectById(dto.getInspectionFormId());
        if (form == null) {
            throw new BusinessException(ErrorType.INSPECTION_FORM_NOT_FOUND);
        }
        if (form.getInspectionFormStatus() != 0 && form.getInspectionFormStatus() != 1) {
            throw new BusinessException(ErrorType.INSPECTION_FORM_STATUS_INVALID,
                    "该验工单已审批完成，无法重复审批");
        }

        boolean pass = dto.getResult() == 1;
        boolean forwardToNext = pass && dto.getNextApproverId() != null;

        // 1. 找当前待审节点；没有则以当前操作人为首审补一条（兼容老流程）
        ApprovalRecords current = approvalFlowService.getCurrentPending(
                ApprovalBizType.INSPECTION, form.getInspectionFormId());
        if (current == null) {
            SubmitApprovalDto submit = new SubmitApprovalDto(
                    ApprovalBizType.INSPECTION.getExt(),
                    form.getInspectionFormId(),
                    userId,
                    form.getInspectionFormDescription()
            );
            current = approvalFlowService.submit(submit, form.getApplyUserId());
        }

        // 2. 执行审批
        ApproveDto approveDto = new ApproveDto(
                current.getApprovalRecordId(),
                pass,
                forwardToNext ? dto.getNextApproverId() : null,
                dto.getApprovalDescription()
        );
        ApprovalFlowService.ApprovalResult result = approvalFlowService.approve(approveDto, userId);

        // 3. 根据 ApprovalResult 更新验工单主表状态
        if (result.rejected) {
            form.setInspectionFormStatus(2); // 已驳回
        } else if (result.isFinal) {
            form.setInspectionFormStatus(3); // 已通过（终审）
        } else {
            // 流转下一级：审核中
            form.setInspectionFormStatus(1);
        }
        inspectionFormMapper.updateById(form);

        // 4. 联动阶段 / 项目状态（仅在"终审驳回"或"终审通过"时）
        if (result.rejected || result.isFinal) {
            ProjectStage stage = projectStageService.getProjectStageById(form.getProjectStageId());
            if (stage != null) {
                if (result.rejected) {
                    stage.setStageStatus(4); // 已驳回
                    log.info("验工审批驳回: stageId={}, stageName={}",
                            stage.getProjectStageId(), stage.getStageName());
                } else {
                    stage.setStageStatus(6); // 已完成
                    log.info("验工审批通过，阶段已完成: stageId={}, stageName={}",
                            stage.getProjectStageId(), stage.getStageName());
                }
                projectStageService.updateProjectStage(stage);
                projectStageService.syncProjectStatus(stage.getProjectId());
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 转换为列表VO，填充关联数据（使用批量预取的 ctx）
     */
    private InspectionFormListVo convertToListVo(InspectionForm form, ListResolveCtx ctx) {
        InspectionFormListVo vo = InspectionFormListVo.objToVo(form);

        // 项目 / 类型 / 合同
        Long projectId = parseProjectId(form.getProjectId());
        if (projectId != null) {
            Project project = ctx.projects().get(projectId);
            if (project != null) {
                vo.setProjectName(project.getProjectName());
                vo.setProjectCode(project.getProjectCode());
                if (project.getProjectType() != null) {
                    ProjectType type = ctx.types().get(project.getProjectType());
                    if (type != null) vo.setProjectTypeName(type.getProjectTypeName());
                }
                Contract contract = ctx.contracts().get(projectId);
                if (contract != null) vo.setContractAmount(contract.getContractAmount());
            }
        }

        // 阶段
        if (form.getProjectStageId() != null) {
            ProjectStage stage = ctx.stages().get(form.getProjectStageId());
            if (stage != null) {
                vo.setStageName(stage.getStageName());
                vo.setStageOutput(stage.getStageOutput());
            }
        }

        // 申请人
        if (form.getApplyUserId() != null) {
            SysUser user = ctx.users().get(form.getApplyUserId());
            if (user != null) vo.setApplyUserName(user.getRealName());
        }

        return vo;
    }

    private void fillCurrentApprovalInfo(List<InspectionFormListVo> list) {
        if (list == null || list.isEmpty()) return;
        for (InspectionFormListVo vo : list) {
            if (vo.getInspectionFormId() == null) continue;
            ApprovalRecords current = approvalFlowService.getCurrentPending(
                    ApprovalBizType.INSPECTION, vo.getInspectionFormId());
            if (current == null) continue;

            ApprovalRecordVo record = ApprovalRecordVo.builder()
                    .approvalRecordId(current.getApprovalRecordId())
                    .approver(current.getApprover())
                    .approvalLevel(current.getApprovalLevel())
                    .build();
            if (current.getApprover() != null) {
                SysUser user = sysUserMapper.selectById(current.getApprover());
                if (user != null) {
                    record.setApproverName(user.getRealName() != null ? user.getRealName() : user.getUsername());
                }
            }
            fillCurrentApprovalInfo(vo, record);
        }
    }

    private void fillCurrentApprovalInfo(InspectionFormListVo vo, ApprovalRecordVo record) {
        if (vo == null || record == null) return;
        vo.setCurrentRecordId(record.getApprovalRecordId());
        vo.setCurrentApproverId(record.getApprover());
        vo.setCurrentApproverName(record.getApproverName());
    }

    private void fillCurrentApprovalInfo(InspectionFormDetailVo vo, ApprovalRecordVo record) {
        if (vo == null || record == null) return;
        vo.setCurrentRecordId(record.getApprovalRecordId());
        vo.setCurrentApproverId(record.getApprover());
        vo.setCurrentApproverName(record.getApproverName());
    }

    /**
     * 填充详情VO的项目信息
     */
    private void fillProjectInfo(InspectionFormDetailVo vo, InspectionForm form) {
        if (StringUtils.hasText(form.getProjectId())) {
            try {
                Long projectId = Long.parseLong(form.getProjectId());
                Project project = projectService.getProjectById(projectId);
                if (project != null) {
                    vo.setProjectName(project.getProjectName());
                    vo.setProjectCode(project.getProjectCode());
                    if (project.getProjectType() != null) {
                        ProjectType type = projectTypeService.getProjectTypeById(project.getProjectType());
                        if (type != null) {
                            vo.setProjectTypeName(type.getProjectTypeName());
                        }
                    }
                    Contract contract = contractService.getContractByProjectId(projectId);
                    if (contract != null) {
                        vo.setContractAmount(contract.getContractAmount());
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("项目ID格式异常: {}", form.getProjectId());
            }
        }

        if (form.getProjectStageId() != null) {
            ProjectStage stage = projectStageService.getProjectStageById(form.getProjectStageId());
            if (stage != null) {
                vo.setStageName(stage.getStageName());
                vo.setStageOutput(stage.getStageOutput());
            }
        }
    }

    // ==================== 导出 Excel ====================

    private static final Map<Integer, String> STATUS_MAP = Map.of(
            0, "待审核", 1, "审核中", 2, "已驳回", 3, "已通过", 4, "草稿"
    );

    private static final DateTimeFormatter EXPORT_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public void exportInspections(GetInspectionFormListDto dto, HttpServletResponse response) throws IOException {
        // 1. 查询符合条件的所有验工单（不分页）
        LambdaQueryWrapper<InspectionForm> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (StringUtils.hasText(dto.getInspectionFormCode())) {
                wrapper.like(InspectionForm::getInspectionFormCode, dto.getInspectionFormCode());
            }
            if (StringUtils.hasText(dto.getProjectId())) {
                wrapper.eq(InspectionForm::getProjectId, dto.getProjectId());
            }
            if (dto.getInspectionFormStatus() != null) {
                wrapper.eq(InspectionForm::getInspectionFormStatus, dto.getInspectionFormStatus());
            }
            if (dto.getApplyUserId() != null) {
                wrapper.eq(InspectionForm::getApplyUserId, dto.getApplyUserId());
            }
        }
        wrapper.orderByDesc(InspectionForm::getCreatedTime);

        List<InspectionForm> forms = inspectionFormMapper.selectList(wrapper);

        // 2. 转换为导出数据（用本地 cache 避免同项目/阶段/用户重复查询）
        ExportCache cache = new ExportCache();
        List<InspectionFormExcelData> data = forms.stream()
                .map(f -> convertToExcelData(f, cache))
                .collect(Collectors.toList());

        // 3. 写出响应
        String fileName = "验工单数据_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        setExcelResponseHeader(response, fileName);
        EasyExcel.write(response.getOutputStream(), InspectionFormExcelData.class)
                .sheet("验工单")
                .doWrite(data);
    }

    private InspectionFormExcelData convertToExcelData(InspectionForm form, ExportCache cache) {
        InspectionFormExcelData data = new InspectionFormExcelData();
        data.setInspectionFormCode(form.getInspectionFormCode());
        data.setDescription(form.getInspectionFormDescription());
        data.setStatus(STATUS_MAP.getOrDefault(form.getInspectionFormStatus(), "未知"));

        // 项目信息
        Long projectId = null;
        if (StringUtils.hasText(form.getProjectId())) {
            try {
                projectId = Long.parseLong(form.getProjectId());
            } catch (NumberFormatException e) {
                log.warn("项目ID格式异常: {}", form.getProjectId());
            }
        }
        if (projectId != null) {
            Project project = cache.projects.computeIfAbsent(projectId, projectService::getProjectById);
            if (project != null) {
                data.setProjectName(project.getProjectName());
                data.setProjectCode(project.getProjectCode());
                if (project.getProjectType() != null) {
                    ProjectType type = cache.types.computeIfAbsent(
                            project.getProjectType(), projectTypeService::getProjectTypeById);
                    if (type != null) data.setProjectTypeName(type.getProjectTypeName());
                }
                Contract contract = cache.contracts.computeIfAbsent(
                        projectId, contractService::getContractByProjectId);
                if (contract != null && contract.getContractAmount() != null) {
                    data.setContractAmount(contract.getContractAmount());
                }
            }
        }

        // 阶段信息 + 阶段金额
        if (form.getProjectStageId() != null) {
            ProjectStage stage = cache.stages.computeIfAbsent(
                    form.getProjectStageId(), projectStageService::getProjectStageById);
            if (stage != null) {
                data.setStageName(stage.getStageName());
                data.setStageOutput(stage.getStageOutput());
                if (data.getContractAmount() != null && stage.getStageOutput() != null) {
                    BigDecimal stageAmount = data.getContractAmount()
                            .multiply(stage.getStageOutput())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    data.setStageAmount(stageAmount);
                }
            }
        }

        // 申请人
        if (form.getApplyUserId() != null) {
            SysUser user = cache.users.computeIfAbsent(form.getApplyUserId(), sysUserMapper::selectById);
            if (user != null) {
                data.setApplyUserName(user.getRealName() != null ? user.getRealName() : user.getUsername());
            }
        }

        if (form.getCreatedTime() != null) {
            data.setApplyTime(form.getCreatedTime().format(EXPORT_TIME_FMT));
        }

        return data;
    }

    private void setExcelResponseHeader(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    /**
     * 导出期间的本地缓存，避免同一次导出里同 projectId / stageId / userId 反复查库
     */
    private static final class ExportCache {
        final Map<Long, Project> projects = new HashMap<>();
        final Map<Long, ProjectStage> stages = new HashMap<>();
        final Map<Long, ProjectType> types = new HashMap<>();
        final Map<Long, Contract> contracts = new HashMap<>();
        final Map<Long, SysUser> users = new HashMap<>();
    }
}
