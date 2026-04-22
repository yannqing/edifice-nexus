package com.qsy.edifice.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.ApplyInspectionDto;
import com.qsy.edifice.domain.dto.ApprovalInspectionDto;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.excel.InspectionFormExcelData;
import com.qsy.edifice.domain.vo.*;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ContractMapper;
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

    // ==================== 查询列表 ====================

    @Override
    public Page<InspectionFormListVo> getMyInspections(GetInspectionFormListDto dto, Long userId) {
        dto.setApplyUserId(userId);
        return queryInspections(dto);
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
        if (dto.getInspectionFormStatus() != null) {
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

        // 填充审批记录
        List<ApprovalRecords> records = approvalRecordsService.getApprovalRecordsByInspectionFormId(form.getInspectionFormId());
        if (records != null && !records.isEmpty()) {
            List<ApprovalRecordVo> recordVos = records.stream().map(r -> {
                ApprovalRecordVo rv = ApprovalRecordVo.builder()
                        .approvalRecordId(r.getApprovalRecordId())
                        .approver(r.getApprover())
                        .approvalDescription(r.getApprovalDescription())
                        .inspectionFormStatus(r.getInspectionFormStatus())
                        .createdTime(r.getCreatedTime())
                        .build();
                // 查审批人姓名
                if (r.getApprover() != null) {
                    SysUser approverUser = sysUserMapper.selectById(r.getApprover());
                    if (approverUser != null) {
                        rv.setApproverName(approverUser.getRealName());
                    }
                }
                return rv;
            }).collect(Collectors.toList());
            vo.setApprovalRecords(recordVos);
        }

        return vo;
    }

    // ==================== 统计总览 ====================

    @Override
    public InspectionOverviewVo getInspectionOverview() {
        InspectionOverviewVo vo = new InspectionOverviewVo();
        vo.setPendingApproval(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 0)));
        vo.setPendingFirstReview(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 1)));
        vo.setApproved(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 3)));
        vo.setRejected(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 2)));
        return vo;
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

        // 阶段状态：1(进行中) → 2(待验收)
        stage.setStageStatus(2);
        projectStageService.updateProjectStage(stage);
        log.info("提交验工单，阶段状态变更为待验收: stageId={}, stageName={}", stage.getProjectStageId(), stage.getStageName());

        return form.getInspectionFormId();
    }

    // ==================== 审批验工单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approvalInspection(ApprovalInspectionDto dto, Long userId) {
        if (dto == null || dto.getInspectionFormId() == null || dto.getResult() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批参数不能为空");
        }

        // 查询验工单
        InspectionForm form = inspectionFormMapper.selectById(dto.getInspectionFormId());
        if (form == null) {
            throw new BusinessException(ErrorType.INSPECTION_FORM_NOT_FOUND);
        }

        // 只有待审核(0)和审核中(1)的验工单可以审批
        if (form.getInspectionFormStatus() != 0 && form.getInspectionFormStatus() != 1) {
            throw new BusinessException(ErrorType.INSPECTION_FORM_STATUS_INVALID, "该验工单已审批完成，无法重复审批");
        }

        // 创建审批记录
        ApprovalRecords record = new ApprovalRecords();
        record.setApprovalRecordType(1); // 验工审批
        record.setInspectionFormId(dto.getInspectionFormId());
        record.setApprover(userId);
        record.setApprovalDescription(dto.getApprovalDescription());
        record.setInspectionFormStatus(dto.getResult()); // 1-通过 2-拒绝
        approvalRecordsService.saveApprovalRecords(record);

        // 更新验工单状态
        if (dto.getResult() == 1) {
            form.setInspectionFormStatus(3); // 已通过
        } else if (dto.getResult() == 2) {
            form.setInspectionFormStatus(2); // 已驳回
        }
        inspectionFormMapper.updateById(form);

        // 联动更新阶段状态
        ProjectStage stage = projectStageService.getProjectStageById(form.getProjectStageId());
        if (stage != null) {
            if (dto.getResult() == 1) {
                // 审批通过：阶段 → 6(已完成)
                stage.setStageStatus(6);
                projectStageService.updateProjectStage(stage);
                log.info("验工审批通过，阶段已完成: stageId={}, stageName={}", stage.getProjectStageId(), stage.getStageName());
            } else if (dto.getResult() == 2) {
                // 审批驳回：阶段 → 4(已驳回)
                stage.setStageStatus(4);
                projectStageService.updateProjectStage(stage);
                log.info("验工审批驳回: stageId={}, stageName={}", stage.getProjectStageId(), stage.getStageName());
            }

            // 同步项目状态
            projectStageService.syncProjectStatus(stage.getProjectId());
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
