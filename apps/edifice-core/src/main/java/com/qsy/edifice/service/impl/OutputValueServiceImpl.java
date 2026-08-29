package com.qsy.edifice.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateOutputValueDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.excel.OutputValueExcelData;
import com.qsy.edifice.domain.model.OutputAllocationContext;
import com.qsy.edifice.domain.vo.OutputValuePreviewVo;
import com.qsy.edifice.domain.vo.OutputValueVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.OutputValueAdjustmentDetailMapper;
import com.qsy.edifice.mapper.OutputValueDistributionMapper;
import com.qsy.edifice.mapper.OutputValueMapper;
import com.qsy.edifice.mapper.OutputValueWorkPoolMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.*;
import com.qsy.edifice.service.support.BenefitAdjustmentAllocator;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * 产值分配服务实现（v0.4）
 *
 * 核心公式（v0.4 修订）：
 *   基本收费：本期 totalAmount = contract.contract_amount × stage.stage_output / 100
 *   基本+效益：本期 totalAmount = contract.base_amount × stage.stage_output / 100
 *                              + contract.benefit_amount × stage.benefit_inclusion_ratio / 100
 *
 *   allocation_v4 先按系统阶段对应的工作权重拆分40%名义池，
 *   再按表格汇总的项目比例/类型总比例拆分项目人员金额。
 *   再在每个工作类型资金池内按 roleAllocRatio 分配给人员。
 *   planned_i      = workPool.projectAmount × roleAllocRatio_i / 100
 *   actual_i       = planned_i × completionRatio_i / 100  （在职员工）
 *   降档差额（planned_i - actual_i） → 公司账
 *   离职成员 planned_i → 公司账（独立记 other_amount，但实际归公司）
 *
 * 守恒：Σ actual + companyReserve = totalAmount
 *   其中 companyReserve = 公司基础留存 + 工作类型转公司 + 降档差额 + 离职兜底
 */
@Slf4j
@Service
public class OutputValueServiceImpl implements OutputValueService {

    private static final Long ROLE_MANAGER_ID = 101L;

    private static final BigDecimal BD_100 = new BigDecimal("100");
    private static final BigDecimal BD_10000 = new BigDecimal("10000");
    /** 守恒校验容差（元），避免 BigDecimal 舍入带来的微分误差误报 */
    private static final BigDecimal INVARIANT_TOLERANCE = new BigDecimal("0.05");
    /** 分配比例合计校验容差（百分点） */
    private static final BigDecimal ALLOC_TOLERANCE = new BigDecimal("0.01");

    /** 分配类型常量 */
    private static final int DIST_TYPE_NORMAL = 0;
    private static final int DIST_TYPE_DOWNGRADE = 1;
    private static final int DIST_TYPE_OTHER = 4;
    private static final int DIST_TYPE_BENEFIT_ADJUSTMENT = 5;
    private static final int COMPONENT_NORMAL = 0;
    private static final int COMPONENT_BENEFIT_ADJUSTMENT = 1;
    /** 可创建产值分配的阶段状态：1-进行中（部分完成）/ 3-已验收 / 6-已完成 */
    private static final Set<Integer> OUTPUT_VALUE_ALLOWED_STAGE_STATUSES = Set.of(1, 3, 6);
    private static final DateTimeFormatter EXPORT_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<Integer, String> STATUS_LABELS = Map.of(
            0, "待确认", 1, "待审核", 2, "已审批", 3, "已发放"
    );
    private static final Map<Integer, String> WORK_TYPE_LABELS = Map.of(
            0, "管理工作", 1, "基础工作", 2, "智励工作"
    );
    private static final Map<Integer, String> DIST_TYPE_LABELS = Map.of(
            0, "员工正常", 1, "员工降档", 2, "领导兜底", 3, "公司留存",
            4, "其他金额", 5, "效益补差/扣回"
    );

    @Resource
    private OutputValueMapper outputValueMapper;

    @Resource
    private OutputValueDistributionMapper distributionMapper;

    @Resource
    private OutputValueAdjustmentDetailMapper adjustmentDetailMapper;

    @Resource
    private OutputValueWorkPoolMapper workPoolMapper;

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectStageService projectStageService;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectMemberService projectMemberService;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ContractService contractService;

    @Resource
    private BusinessRuleConfigService businessRuleConfigService;

    @Resource
    private OutputAllocationRuleService outputAllocationRuleService;

    @Resource
    private ApprovalFlowService approvalFlowService;

    // ==================== 查询 ====================

    @Override
    public List<OutputValueVo> getOutputValueList(Integer status) {
        LambdaQueryWrapper<OutputValue> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(OutputValue::getStatus, status);
        }
        wrapper.orderByDesc(OutputValue::getCreatedTime);

        List<OutputValue> list = outputValueMapper.selectList(wrapper);
        return list.stream().map(this::convertToVo).collect(Collectors.toList());
    }

    @Override
    public OutputValuePreviewVo previewOutputValue(Long projectId, Long projectStageId) {
        if (projectId == null || projectStageId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择项目和阶段");
        }
        // 使用阶段的默认系数进行预览
        ProjectStage stage = projectStageService.getProjectStageById(projectStageId);
        BigDecimal coeff = (stage != null && stage.getCoefficient() != null && stage.getCoefficient().signum() > 0)
                ? stage.getCoefficient() : BigDecimal.ONE;
        OutputValueCalculationResult calculation = calcOutputValue(projectId, projectStageId, coeff);
        OutputAllocationContext allocation = outputAllocationRuleService.calculate(
                projectId, projectStageId, calculation.currentStageAmount);
        BenefitAdjustmentPlan adjustmentPlan = calcBenefitAdjustmentPlan(
                projectId, projectStageId, calculation.benefitAmount, Collections.emptyMap());
        return toPreviewVo(calculation, allocation, adjustmentPlan);
    }

    @Override
    public OutputValuePreviewVo previewOutputValue(Long projectId, Long projectStageId, BigDecimal coefficient) {
        if (projectId == null || projectStageId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择项目和阶段");
        }
        if (coefficient == null || coefficient.signum() <= 0) {
            coefficient = BigDecimal.ONE;
        }
        OutputValueCalculationResult calculation = calcOutputValue(projectId, projectStageId, coefficient);
        OutputAllocationContext allocation = outputAllocationRuleService.calculate(
                projectId, projectStageId, calculation.currentStageAmount);
        BenefitAdjustmentPlan adjustmentPlan = calcBenefitAdjustmentPlan(
                projectId, projectStageId, calculation.benefitAmount, Collections.emptyMap());
        return toPreviewVo(calculation, allocation, adjustmentPlan);
    }

    // ==================== 创建（v0.4） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOutputValue(CreateOutputValueDto dto, Long userId) {
        if (dto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "创建参数不能为空");
        }
        if (dto.getProjectId() == null || dto.getProjectStageId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择项目和阶段");
        }
        projectService.ensureProjectNotArchived(dto.getProjectId());
        if (dto.getDistributions() == null || dto.getDistributions().isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请添加至少一条分配明细");
        }
        if (!StringUtils.hasText(dto.getQuarter())) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择所属季度");
        }
        if (dto.getConfirmUserId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择确认人");
        }
        // Serialize all output-value settlements for this project so the same historical balance
        // cannot be consumed by two concurrent stage submissions.
        outputValueMapper.selectByProjectIdForUpdate(dto.getProjectId());
        List<OutputValue> stageOutputValues = outputValueMapper.selectByProjectStageIdForUpdate(dto.getProjectStageId());
        // 如果该阶段有待确认或待审核的分配单，不允许再创建
        boolean hasPending = stageOutputValues.stream()
                .anyMatch(ov -> ov.getStatus() != null && (ov.getStatus() == 0 || ov.getStatus() == 1));
        if (hasPending) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "该阶段已有待处理的产值分配单，请先完成审批流程");
        }
        if (hasConfirmedStageOutputValue(stageOutputValues, null)) {
            // 部分完成的阶段允许再次分配：检查当前完成比例是否超过已分配的最大比例
            ProjectStage checkStage = projectStageService.getProjectStageById(dto.getProjectStageId());
            BigDecimal currentRatio = checkStage != null ? checkStage.getCompletionRatio() : null;
            if (currentRatio == null || currentRatio.signum() <= 0) {
                currentRatio = (checkStage != null && checkStage.getStageStatus() != null && checkStage.getStageStatus() == 6)
                        ? new BigDecimal("100") : BigDecimal.ZERO;
            }
            BigDecimal maxAllocatedRatio = stageOutputValues.stream()
                    .filter(ov -> ov.getStatus() != null && ov.getStatus() >= 1)
                    .map(ov -> ov.getStageCompletionRatio() != null ? ov.getStageCompletionRatio() : new BigDecimal("100"))
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            if (currentRatio.compareTo(maxAllocatedRatio) <= 0) {
                throw new BusinessException(ErrorType.OPERATION_FAILED, "该阶段产值已全部分配完毕，无法重复提交");
            }
        }

        // 1. 先计算当前阶段纯产值；历史效益补差在人员正常分配完成后逐人结算。
        BigDecimal coefficient = dto.getCoefficient();
        if (coefficient == null || coefficient.signum() <= 0) {
            ProjectStage stage = projectStageService.getProjectStageById(dto.getProjectStageId());
            coefficient = (stage != null && stage.getCoefficient() != null && stage.getCoefficient().signum() > 0)
                    ? stage.getCoefficient() : BigDecimal.ONE;
        }
        OutputValueCalculationResult calculation = calcOutputValue(dto.getProjectId(), dto.getProjectStageId(), coefficient);

        // 2. allocation_v5：当前阶段资金池只包含当前阶段金额，不再混入历史补差。
        OutputAllocationContext allocation = outputAllocationRuleService.calculate(
                dto.getProjectId(), dto.getProjectStageId(), calculation.currentStageAmount);
        Map<Integer, OutputAllocationContext.WorkPool> poolByWorkType = allocation.getWorkPools().stream()
                .collect(Collectors.toMap(OutputAllocationContext.WorkPool::getWorkType, item -> item));
        Map<Integer, List<CreateOutputValueDto.DistributionItem>> distributionsByWorkType =
                dto.getDistributions().stream().collect(Collectors.groupingBy(
                        item -> item.getWorkType() == null ? 1 : item.getWorkType()));

        Set<String> userWorkKeys = new HashSet<>();
        for (Map.Entry<Integer, List<CreateOutputValueDto.DistributionItem>> entry
                : distributionsByWorkType.entrySet()) {
            if (!poolByWorkType.containsKey(entry.getKey())) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "存在无效的工作类型分配明细");
            }
            for (CreateOutputValueDto.DistributionItem item : entry.getValue()) {
                if (item.getUserId() != null && !userWorkKeys.add(entry.getKey() + ":" + item.getUserId())) {
                    throw new BusinessException(ErrorType.ARGS_INVALID,
                            "同一人员不能在同一个工作类型中重复分配");
                }
            }
        }

        for (OutputAllocationContext.WorkPool pool : allocation.getWorkPools()) {
            List<CreateOutputValueDto.DistributionItem> items = distributionsByWorkType
                    .getOrDefault(pool.getWorkType(), Collections.emptyList());
            if (pool.getProjectAmount().signum() == 0) {
                if (!items.isEmpty()) {
                    throw new BusinessException(ErrorType.ARGS_INVALID,
                            WORK_TYPE_LABELS.get(pool.getWorkType()) + "当前没有可分配金额，请删除对应人员明细");
                }
                continue;
            }
            if (items.isEmpty()) {
                throw new BusinessException(ErrorType.ARGS_NOT_NULL,
                        "请填写" + WORK_TYPE_LABELS.get(pool.getWorkType()) + "的人员分配明细");
            }
            BigDecimal roleRatioSum = items.stream()
                    .map(item -> item.getRoleAllocRatio() != null
                            ? item.getRoleAllocRatio()
                            : defaultIfNull(item.getAllocRatio(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (roleRatioSum.subtract(BD_100).abs().compareTo(ALLOC_TOLERANCE) > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID,
                        WORK_TYPE_LABELS.get(pool.getWorkType()) + "人员比例合计应为100%，当前为"
                                + roleRatioSum.stripTrailingZeros().toPlainString() + "%");
            }
        }

        // 3. 在每个工作类型资金池内逐人计算计划金额与实得金额
        BigDecimal downgradeDelta = BigDecimal.ZERO;  // 降档差额（→ 公司账）
        BigDecimal otherAmount = BigDecimal.ZERO;     // 离职兜底（→ 公司账，独立记账）
        List<OutputValueDistribution> distEntities = new ArrayList<>();

        for (OutputAllocationContext.WorkPool pool : allocation.getWorkPools()) {
            List<CreateOutputValueDto.DistributionItem> items = distributionsByWorkType
                    .getOrDefault(pool.getWorkType(), Collections.emptyList());
            BigDecimal groupPlanned = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            for (int i = 0; i < items.size(); i++) {
                CreateOutputValueDto.DistributionItem item = items.get(i);
                if (item.getUserId() == null) {
                    throw new BusinessException(ErrorType.ARGS_NOT_NULL,
                            WORK_TYPE_LABELS.get(pool.getWorkType()) + "第" + (i + 1) + "行分配对象为空");
                }
                BigDecimal roleRatio = item.getRoleAllocRatio() != null
                        ? item.getRoleAllocRatio()
                        : defaultIfNull(item.getAllocRatio(), BigDecimal.ZERO);
                BigDecimal completion = defaultIfNull(item.getCompletionRatio(), BD_100);
                if (roleRatio.signum() < 0 || roleRatio.compareTo(BD_100) > 0) {
                    throw new BusinessException(ErrorType.ARGS_INVALID, "角色内分配比例应在0-100之间");
                }
                if (completion.signum() < 0 || completion.compareTo(BD_100) > 0) {
                    throw new BusinessException(ErrorType.ARGS_INVALID, "兑现比例应在0-100之间");
                }

                BigDecimal planned = i == items.size() - 1
                        ? pool.getProjectAmount().subtract(groupPlanned).setScale(2, RoundingMode.HALF_UP)
                        : pool.getProjectAmount().multiply(roleRatio)
                        .divide(BD_100, 2, RoundingMode.HALF_UP);
                groupPlanned = groupPlanned.add(planned);
                int isActive = resolveIsActive(item.getUserId(), item.getIsActive());
                int distType;
                BigDecimal actualAmount;
                BigDecimal companyDelta;

                if (isActive == 0) {
                    distType = DIST_TYPE_OTHER;
                    actualAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    companyDelta = planned;
                    otherAmount = otherAmount.add(planned);
                } else if (completion.compareTo(BD_100) < 0) {
                    distType = DIST_TYPE_DOWNGRADE;
                    actualAmount = planned.multiply(completion)
                            .divide(BD_100, 2, RoundingMode.HALF_UP);
                    companyDelta = planned.subtract(actualAmount).setScale(2, RoundingMode.HALF_UP);
                    downgradeDelta = downgradeDelta.add(companyDelta);
                } else {
                    distType = DIST_TYPE_NORMAL;
                    actualAmount = planned;
                    companyDelta = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }

                BigDecimal globalAllocRatio = allocation.getEmployeePoolAmount().signum() == 0
                        ? BigDecimal.ZERO
                        : planned.multiply(BD_100).divide(
                        allocation.getEmployeePoolAmount(), 4, RoundingMode.HALF_UP);
                distEntities.add(OutputValueDistribution.builder()
                        .componentType(COMPONENT_NORMAL)
                        .userId(item.getUserId())
                        .workType(pool.getWorkType())
                        .ratio(globalAllocRatio)
                        .allocRatio(globalAllocRatio)
                        .roleAllocRatio(roleRatio)
                        .plannedAmount(planned)
                        .companyDelta(companyDelta)
                        .completionRatio(completion)
                        .distType(distType)
                        .isActive(isActive)
                        .amount(actualAmount)
                        .build());
            }
        }

        // 4. 历史效益修正按原分配人员逐笔结算。负数最多扣到该人员本期实得为 0。
        Map<Long, BigDecimal> normalAmountsByUser = distEntities.stream()
                .collect(Collectors.groupingBy(
                        OutputValueDistribution::getUserId,
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, OutputValueDistribution::getAmount, BigDecimal::add)));
        BenefitAdjustmentPlan adjustmentPlan = calcBenefitAdjustmentPlan(
                dto.getProjectId(), dto.getProjectStageId(), calculation.benefitAmount, normalAmountsByUser);

        for (BenefitAdjustmentAllocator.AppliedAdjustment applied : adjustmentPlan.personResult.adjustments()) {
            BenefitAdjustmentAllocator.PendingAdjustment source = applied.source();
            distEntities.add(OutputValueDistribution.builder()
                    .componentType(COMPONENT_BENEFIT_ADJUSTMENT)
                    .sourceDistributionId(source.sourceDistributionId())
                    .sourceOutputValueId(source.sourceOutputValueId())
                    .sourceProjectStageId(source.sourceProjectStageId())
                    .userId(source.userId())
                    .workType(source.workType())
                    .ratio(BigDecimal.ZERO)
                    .allocRatio(BigDecimal.ZERO)
                    .roleAllocRatio(BigDecimal.ZERO)
                    .plannedAmount(applied.appliedAmount())
                    .companyDelta(BigDecimal.ZERO)
                    .adjustmentTargetAmount(source.targetAmount())
                    .previousAdjustedAmount(source.previousAdjustedAmount())
                    .remainingAdjustmentAmount(applied.remainingAmount())
                    .completionRatio(BD_100)
                    .distType(DIST_TYPE_BENEFIT_ADJUSTMENT)
                    .isActive(1)
                    .amount(applied.appliedAmount())
                    .build());
        }

        BigDecimal personAdjustmentAmount = adjustmentPlan.personResult.appliedTotal();
        BigDecimal companyAdjustmentAmount = adjustmentPlan.companyAppliedAmount;
        BigDecimal adjustmentAmount = personAdjustmentAmount.add(companyAdjustmentAmount)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = calculation.currentStageAmount.add(adjustmentAmount)
                .setScale(2, RoundingMode.HALF_UP);
        if (total.signum() == 0) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "本次产值为 0，请检查合同金额、阶段比例和历史补差");
        }
        boolean allowNegativeOutput = businessRuleConfigService.booleanValue(
                ApprovalBizType.OUTPUT.getExt(), "allow_negative_output", false);
        if (total.signum() < 0 && !allowNegativeOutput) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "本次多退少补结果为负，请先在规则配置中允许负产值");
        }

        // 5. 公司账 = 当前阶段公司留存 + 本次公司效益补差/扣回。
        BigDecimal companyReserve = allocation.getCompanyBaseAmount()
                .add(allocation.getWorkTransferAmount())
                .add(downgradeDelta)
                .add(otherAmount)
                .add(companyAdjustmentAmount)
                .setScale(2, RoundingMode.HALF_UP);

        // 6. 守恒校验：正常分配 + 人员补扣 + 公司账 = 本次总额。
        BigDecimal sumActual = distEntities.stream()
                .map(OutputValueDistribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sum = companyReserve.add(sumActual);
        if (sum.subtract(total).abs().compareTo(INVARIANT_TOLERANCE) > 0) {
            throw new BusinessException(ErrorType.OPERATION_FAILED,
                    "金额守恒失败：公司账 " + companyReserve + "（基础留存 "
                            + allocation.getCompanyBaseAmount() + " + 工作类型转入 "
                            + allocation.getWorkTransferAmount() + " + 降档差额 " + downgradeDelta
                            + " + 离职兜底 " + otherAmount + " + 公司效益补扣 " + companyAdjustmentAmount
                            + "）+ 员工实得 " + sumActual + " = " + sum + "，应为 " + total);
        }

        // 7. 保存
        OutputValue ov = OutputValue.builder()
                .projectId(dto.getProjectId())
                .projectStageId(dto.getProjectStageId())
                .quarter(dto.getQuarter().trim())
                .totalAmount(total)
                .companyReserve(companyReserve)
                .leaderExtra(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .otherAmount(otherAmount)
                .subsidyAmount(dto.getSubsidyAmount() != null
                        ? dto.getSubsidyAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .stageCumulativeAmount(calculation.currentStageAmount)
                .previousCumulativeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .baseAmountPart(calculation.basePart)
                .benefitAmountPart(calculation.benefitPart)
                .benefitSnapshot(calculation.benefitAmount)
                .currentStageAmount(calculation.currentStageAmount)
                .adjustmentAmount(adjustmentAmount)
                .personAdjustmentAmount(personAdjustmentAmount)
                .companyAdjustmentAmount(companyAdjustmentAmount)
                .pendingPersonAdjustmentAmount(adjustmentPlan.personResult.remainingTotal())
                .stageCompletionRatio(calculation.completionRatio)
                .stageIncrementalRatio(calculation.incrementalRatio)
                .coefficient(coefficient)
                .baseAmountSnapshot(calculation.baseAmount)
                .benefitAmountSnapshot(calculation.benefitAmount)
                .baseRatioSnapshot(calculation.baseRatio)
                .benefitRatioSnapshot(calculation.benefitRatio)
                .calculationVersion("person_benefit_adjustment_v2")
                .allocationVersion("allocation_v5")
                .allocationRuleVersionId(allocation.getRuleVersionId())
                .employeePoolAmount(allocation.getEmployeePoolAmount())
                .companyBaseAmount(allocation.getCompanyBaseAmount())
                .workTransferAmount(allocation.getWorkTransferAmount())
                .projectPoolAmount(allocation.getProjectPoolAmount())
                .status(0)
                .submitUserId(userId)
                .confirmUserId(dto.getConfirmUserId())
                .currentHandlerId(dto.getConfirmUserId())
                .submitTime(LocalDateTime.now())
                .build();
        outputValueMapper.insert(ov);

        for (OutputValueAdjustmentDetail detail : buildAdjustmentDetails(adjustmentPlan, calculation)) {
            detail.setOutputValueId(ov.getOutputValueId());
            adjustmentDetailMapper.insert(detail);
        }

        Map<Integer, Long> workPoolIds = new HashMap<>();
        for (OutputAllocationContext.WorkPool pool : allocation.getWorkPools()) {
            OutputValueWorkPool entity = OutputValueWorkPool.builder()
                    .outputValueId(ov.getOutputValueId())
                    .ruleVersionId(allocation.getRuleVersionId())
                    .ruleVersionNo(allocation.getRuleVersionNo())
                    .workType(pool.getWorkType())
                    .stageWorkRatio(pool.getWorkWeight())
                    .grossRate(pool.getGrossRate())
                    .grossAmount(pool.getGrossAmount())
                    .projectRate(pool.getProjectRate())
                    .projectAmount(pool.getProjectAmount())
                    .companyRate(pool.getCompanyRate())
                    .companyAmount(pool.getCompanyAmount())
                    .build();
            workPoolMapper.insert(entity);
            workPoolIds.put(pool.getWorkType(), entity.getWorkPoolId());
        }

        for (OutputValueDistribution d : distEntities) {
            d.setOutputValueId(ov.getOutputValueId());
            if (Objects.equals(d.getComponentType(), COMPONENT_NORMAL)) {
                d.setWorkPoolId(workPoolIds.get(d.getWorkType()));
            }
            distributionMapper.insert(d);
        }

        log.info("[allocation_v5] 创建产值分配单 id={} 季度={} total={}（当前阶段{} + 人员补扣{} + 公司补扣{}；基本{} + 效益{}） 项目人员池={} 公司内部留存={} 公司账={} 员工实得={} 待扣余额={}",
                ov.getOutputValueId(), ov.getQuarter(), total,
                calculation.currentStageAmount, personAdjustmentAmount, companyAdjustmentAmount,
                calculation.basePart, calculation.benefitPart,
                allocation.getProjectPoolAmount(), allocation.getWorkTransferAmount(),
                companyReserve, sumActual, adjustmentPlan.personResult.remainingTotal());

        // 同步提交到统一审批流（供统一待办/消息中心消费）
        // L1 = 确认人（confirmUserId），后续 confirm→approve→pay 时分别流转
        SubmitApprovalDto submit = new SubmitApprovalDto();
        submit.setBizType(ApprovalBizType.OUTPUT.getExt());
        submit.setBizId(ov.getOutputValueId());
        submit.setFirstApproverId(dto.getConfirmUserId());
        submit.setDescription("产值分配单待确认 · 季度 " + ov.getQuarter());
        approvalFlowService.submit(submit, userId);

        return ov.getOutputValueId();
    }

    /**
     * 计算指定阶段产值。阶段比例是单阶段比例；历史补差并入本次分配总额。
     */
    private OutputValueCalculationResult calcOutputValue(Long projectId, Long stageId, BigDecimal coefficient) {
        ProjectStage stage = projectStageService.getProjectStageById(stageId);
        if (stage == null || !projectId.equals(stage.getProjectId())) {
            throw new BusinessException(ErrorType.STAGE_NOT_FOUND);
        }
        boolean requireStageInspectionPassed = businessRuleConfigService.booleanValue(
                ApprovalBizType.OUTPUT.getExt(), "require_stage_inspection_passed", true);
        if (requireStageInspectionPassed && !OUTPUT_VALUE_ALLOWED_STAGE_STATUSES.contains(stage.getStageStatus())) {
            throw new BusinessException(ErrorType.STAGE_STATUS_INVALID,
                    "只有已完成阶段才能创建产值分配单，当前阶段[" + stage.getStageName() + "]状态不允许");
        }
        Contract contract = contractService.getContractByProjectId(projectId);
        if (contract == null) {
            throw new BusinessException(ErrorType.CONTRACT_NOT_FOUND);
        }

        boolean hasBenefit = Objects.equals(contract.getContractType(), 1);
        BigDecimal baseAmt = resolveBaseAmount(contract, hasBenefit);
        BigDecimal benefitAmt = hasBenefit && contract.getBenefitAmount() != null
                ? contract.getBenefitAmount() : BigDecimal.ZERO;

        BigDecimal baseRatio = stage.getStageOutput() != null
                ? stage.getStageOutput() : BigDecimal.ZERO;
        BigDecimal benefitRatio = hasBenefit
                ? resolveBenefitRatio(stage.getBenefitInclusionRatio(), baseRatio)
                : BigDecimal.ZERO;

        // 部分完成：按实际完成比例折算（兼容旧数据：completionRatio 为 null 或 0 且 status=6 时按 100% 计算）
        BigDecimal completionRatio = stage.getCompletionRatio();
        if (completionRatio == null || completionRatio.signum() <= 0) {
            completionRatio = (stage.getStageStatus() != null && stage.getStageStatus() == 6)
                    ? new BigDecimal("100") : BigDecimal.ZERO;
        }

        // 扣除当前阶段已确认的产值分配金额（本次应分配 = 累计总额 - 已分配总额）
        LambdaQueryWrapper<OutputValue> allocatedW = new LambdaQueryWrapper<>();
        allocatedW.eq(OutputValue::getProjectStageId, stageId)
                .ge(OutputValue::getStatus, 2);
        List<OutputValue> allocatedList = outputValueMapper.selectList(allocatedW);
        BigDecimal alreadyAllocated = allocatedList.stream()
                .map(ov -> ov.getCurrentStageAmount() != null ? ov.getCurrentStageAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // 计算增量完成比例：当前累计比例 - 已分配的累计比例之和
        BigDecimal alreadyCompletionRatio = allocatedList.stream()
                .map(ov -> ov.getStageCompletionRatio() != null ? ov.getStageCompletionRatio() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        // 兼容旧数据：如果没有 stage_completion_ratio，用 stage_incremental_ratio 累加
        if (alreadyCompletionRatio.signum() == 0 && !allocatedList.isEmpty()) {
            alreadyCompletionRatio = allocatedList.stream()
                    .map(ov -> ov.getStageIncrementalRatio() != null ? ov.getStageIncrementalRatio() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal incrementalRatio = completionRatio.subtract(alreadyCompletionRatio).max(BigDecimal.ZERO);

        BigDecimal basePart = baseAmt.multiply(baseRatio).multiply(incrementalRatio)
                .divide(BD_10000, 2, RoundingMode.HALF_UP);
        BigDecimal benefitPart = benefitAmt.multiply(benefitRatio).multiply(incrementalRatio)
                .divide(BD_10000, 2, RoundingMode.HALF_UP);
        BigDecimal currentStageAmount = basePart.add(benefitPart).setScale(2, RoundingMode.HALF_UP);
        // 应用系数
        if (coefficient == null || coefficient.signum() <= 0) {
            coefficient = BigDecimal.ONE;
        }
        currentStageAmount = currentStageAmount.multiply(coefficient).setScale(2, RoundingMode.HALF_UP);

        return new OutputValueCalculationResult(
                baseAmt.setScale(2, RoundingMode.HALF_UP),
                benefitAmt.setScale(2, RoundingMode.HALF_UP),
                baseRatio,
                benefitRatio,
                basePart,
                benefitPart,
                currentStageAmount,
                completionRatio,
                alreadyAllocated,
                incrementalRatio,
                coefficient
        );
    }

    private BenefitAdjustmentPlan calcBenefitAdjustmentPlan(Long projectId,
                                                            Long currentStageId,
                                                            BigDecimal currentBenefitAmount,
                                                            Map<Long, BigDecimal> normalAmountsByUser) {
        LambdaQueryWrapper<OutputValue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutputValue::getProjectId, projectId)
                .ge(OutputValue::getStatus, 2)
                .orderByAsc(OutputValue::getCreatedTime)
                .orderByAsc(OutputValue::getOutputValueId);
        List<OutputValue> historicalOutputs = outputValueMapper.selectList(wrapper);
        if (historicalOutputs == null || historicalOutputs.isEmpty()) {
            return BenefitAdjustmentPlan.empty();
        }

        BigDecimal newBenefit = money(currentBenefitAmount);
        List<BenefitStageAdjustment> stages = new ArrayList<>();
        List<BenefitAdjustmentAllocator.PendingAdjustment> personPending = new ArrayList<>();
        for (OutputValue historical : historicalOutputs) {
            if (Objects.equals(historical.getProjectStageId(), currentStageId)) {
                continue;
            }
            ProjectStage sourceStage = projectStageService.getProjectStageById(historical.getProjectStageId());
            if (sourceStage == null) {
                continue;
            }

            BigDecimal oldBenefit = money(resolveHistoricalBenefitSnapshot(historical));
            BigDecimal sourceIncrementalRatio = positiveOrFallback(
                    historical.getStageIncrementalRatio(),
                    positiveOrFallback(historical.getStageCompletionRatio(), BD_100));
            BigDecimal sourceBaseRatio = positiveOrFallback(
                    historical.getBaseRatioSnapshot(),
                    defaultIfNull(sourceStage.getStageOutput(), BigDecimal.ZERO));
            BigDecimal sourceBenefitRatio = resolveHistoricalBenefitRatio(
                    historical, sourceStage, oldBenefit, sourceIncrementalRatio, sourceBaseRatio);
            BigDecimal sourceCoefficient = positiveOrFallback(historical.getCoefficient(), BigDecimal.ONE);

            BigDecimal stageTarget = newBenefit.subtract(oldBenefit)
                    .multiply(sourceBenefitRatio)
                    .multiply(sourceIncrementalRatio)
                    .divide(BD_10000, 8, RoundingMode.HALF_UP)
                    .multiply(sourceCoefficient)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal oldStageAmount = resolveOriginalStageAmount(historical);
            BigDecimal newStageAmount = oldStageAmount.add(stageTarget).setScale(2, RoundingMode.HALF_UP);

            Map<Integer, OutputValueWorkPool> pools = outputAllocationRuleService
                    .getWorkPools(historical.getOutputValueId()).stream()
                    .collect(Collectors.toMap(OutputValueWorkPool::getWorkType, item -> item, (left, right) -> left));
            LambdaQueryWrapper<OutputValueDistribution> distWrapper = new LambdaQueryWrapper<>();
            distWrapper.eq(OutputValueDistribution::getOutputValueId, historical.getOutputValueId())
                    .and(w -> w.eq(OutputValueDistribution::getComponentType, COMPONENT_NORMAL)
                            .or().isNull(OutputValueDistribution::getComponentType))
                    .orderByAsc(OutputValueDistribution::getCreatedTime)
                    .orderByAsc(OutputValueDistribution::getDistributionId);
            List<OutputValueDistribution> sourceDistributions = distributionMapper.selectList(distWrapper);

            BigDecimal personTargetTotal = BigDecimal.ZERO;
            BigDecimal personPreviousTotal = BigDecimal.ZERO;
            List<BenefitAdjustmentAllocator.PendingAdjustment> stagePersonPending = new ArrayList<>();
            for (OutputValueDistribution sourceDistribution : sourceDistributions) {
                if (sourceDistribution.getUserId() == null) {
                    continue;
                }
                BigDecimal target = calculatePersonBenefitAdjustmentTarget(
                        stageTarget, oldStageAmount, sourceDistribution,
                        pools.get(sourceDistribution.getWorkType()));
                BigDecimal previous = money(distributionMapper.sumAppliedBenefitAdjustment(
                        sourceDistribution.getDistributionId()));
                BigDecimal pending = target.subtract(previous).setScale(2, RoundingMode.HALF_UP);
                personTargetTotal = personTargetTotal.add(target);
                personPreviousTotal = personPreviousTotal.add(previous);
                if (pending.signum() != 0) {
                    stagePersonPending.add(new BenefitAdjustmentAllocator.PendingAdjustment(
                            sourceDistribution.getDistributionId(),
                            historical.getOutputValueId(),
                            historical.getProjectStageId(),
                            sourceStage.getStageName(),
                            sourceDistribution.getUserId(),
                            sourceDistribution.getWorkType(),
                            target,
                            previous,
                            pending));
                }
            }

            personTargetTotal = money(personTargetTotal);
            personPreviousTotal = money(personPreviousTotal);
            BigDecimal companyTarget = stageTarget.subtract(personTargetTotal).setScale(2, RoundingMode.HALF_UP);
            BigDecimal companyPrevious = money(adjustmentDetailMapper
                    .sumAppliedCompanyBenefitAdjustment(historical.getOutputValueId()));
            BigDecimal companyPending = companyTarget.subtract(companyPrevious)
                    .setScale(2, RoundingMode.HALF_UP);
            if (stagePersonPending.isEmpty() && companyPending.signum() == 0) {
                continue;
            }

            personPending.addAll(stagePersonPending);
            stages.add(new BenefitStageAdjustment(
                    historical,
                    sourceStage.getStageName(),
                    sourceBaseRatio,
                    sourceBenefitRatio,
                    oldBenefit,
                    newBenefit,
                    oldStageAmount,
                    newStageAmount,
                    stageTarget,
                    personTargetTotal,
                    personPreviousTotal,
                    companyTarget,
                    companyPrevious,
                    companyPending));
        }

        BenefitAdjustmentAllocator.Result personResult = BenefitAdjustmentAllocator.allocate(
                personPending, normalAmountsByUser);
        BigDecimal companyApplied = stages.stream()
                .map(BenefitStageAdjustment::companyPendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new BenefitAdjustmentPlan(List.copyOf(stages), personResult, companyApplied);
    }

    private BigDecimal calculatePersonBenefitAdjustmentTarget(BigDecimal stageTarget,
                                                              BigDecimal oldStageAmount,
                                                              OutputValueDistribution source,
                                                              OutputValueWorkPool sourcePool) {
        if (Objects.equals(source.getIsActive(), 0)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal roleRatio = source.getRoleAllocRatio();
        if (sourcePool != null && roleRatio != null) {
            return BenefitAdjustmentAllocator.calculatePersonTarget(
                    stageTarget,
                    sourcePool.getProjectRate(),
                    roleRatio,
                    defaultIfNull(source.getCompletionRatio(), BD_100));
        }
        if (oldStageAmount.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return stageTarget.multiply(defaultIfNull(source.getAmount(), BigDecimal.ZERO))
                .divide(oldStageAmount, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveHistoricalBenefitRatio(OutputValue historical,
                                                     ProjectStage sourceStage,
                                                     BigDecimal oldBenefit,
                                                     BigDecimal sourceIncrementalRatio,
                                                     BigDecimal sourceBaseRatio) {
        if (historical.getBenefitRatioSnapshot() != null) {
            return historical.getBenefitRatioSnapshot();
        }
        if (historical.getBenefitAmountPart() != null
                && oldBenefit.signum() != 0
                && sourceIncrementalRatio.signum() != 0) {
            return historical.getBenefitAmountPart().multiply(BD_10000)
                    .divide(oldBenefit.multiply(sourceIncrementalRatio), 4, RoundingMode.HALF_UP);
        }
        return resolveBenefitRatio(sourceStage.getBenefitInclusionRatio(), sourceBaseRatio);
    }

    private BigDecimal resolveOriginalStageAmount(OutputValue output) {
        if (output.getCurrentStageAmount() != null) {
            return output.getCurrentStageAmount().setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal basePart = output.getBaseAmountPart();
        BigDecimal benefitPart = output.getBenefitAmountPart();
        if (basePart != null || benefitPart != null) {
            return (basePart == null ? BigDecimal.ZERO : basePart)
                    .add(benefitPart == null ? BigDecimal.ZERO : benefitPart)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        if (output.getStageCumulativeAmount() != null) {
            return output.getStageCumulativeAmount().setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal adjustment = output.getAdjustmentAmount() != null
                ? output.getAdjustmentAmount() : BigDecimal.ZERO;
        return (output.getTotalAmount() == null ? BigDecimal.ZERO : output.getTotalAmount())
                .subtract(adjustment)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveHistoricalBenefitSnapshot(OutputValue output) {
        if (output.getBenefitAmountSnapshot() != null) {
            return output.getBenefitAmountSnapshot();
        }
        return output.getBenefitSnapshot();
    }

    private List<OutputValueAdjustmentDetail> buildAdjustmentDetails(
            BenefitAdjustmentPlan plan,
            OutputValueCalculationResult calculation) {
        Map<Long, BenefitAdjustmentAllocator.AppliedAdjustment> appliedBySource = plan.personResult.adjustments()
                .stream().collect(Collectors.toMap(
                        item -> item.source().sourceDistributionId(),
                        item -> item,
                        (left, right) -> left));
        List<OutputValueAdjustmentDetail> details = new ArrayList<>();
        for (BenefitStageAdjustment stage : plan.stages) {
            BigDecimal personApplied = BigDecimal.ZERO;
            BigDecimal personRemaining = BigDecimal.ZERO;
            for (BenefitAdjustmentAllocator.AppliedAdjustment applied : appliedBySource.values()) {
                if (Objects.equals(applied.source().sourceOutputValueId(),
                        stage.sourceOutput().getOutputValueId())) {
                    personApplied = personApplied.add(applied.appliedAmount());
                    personRemaining = personRemaining.add(applied.remainingAmount());
                }
            }
            personApplied = money(personApplied);
            personRemaining = money(personRemaining);
            BigDecimal stageApplied = personApplied.add(stage.companyPendingAmount())
                    .setScale(2, RoundingMode.HALF_UP);

            details.add(OutputValueAdjustmentDetail.builder()
                    .sourceOutputValueId(stage.sourceOutput().getOutputValueId())
                    .sourceProjectStageId(stage.sourceOutput().getProjectStageId())
                    .sourceStageName(stage.sourceStageName())
                    .sourceBaseRatio(stage.sourceBaseRatio())
                    .sourceBenefitRatio(stage.sourceBenefitRatio())
                    .oldBaseAmountSnapshot(stage.sourceOutput().getBaseAmountSnapshot())
                    .oldBenefitAmountSnapshot(stage.oldBenefitAmount())
                    .oldStageAmount(stage.oldStageAmount())
                    .newBaseAmountSnapshot(calculation.baseAmount)
                    .newBenefitAmountSnapshot(stage.newBenefitAmount())
                    .newStageAmount(stage.newStageAmount())
                    .alreadyAdjustedAmount(stage.personPreviousAmount()
                            .add(stage.companyPreviousAmount()).setScale(2, RoundingMode.HALF_UP))
                    .adjustmentAmount(stageApplied)
                    .personAdjustmentAmount(personApplied)
                    .companyAdjustmentAmount(stage.companyPendingAmount())
                    .remainingPersonAdjustmentAmount(personRemaining)
                    .build());
        }
        return details;
    }

    private List<OutputValuePreviewVo.BenefitAdjustmentVo> toBenefitAdjustmentVos(
            BenefitAdjustmentPlan plan) {
        return plan.personResult.adjustments().stream().map(applied -> {
            BenefitAdjustmentAllocator.PendingAdjustment source = applied.source();
            return new OutputValuePreviewVo.BenefitAdjustmentVo(
                    source.sourceDistributionId(),
                    source.sourceOutputValueId(),
                    source.sourceProjectStageId(),
                    source.sourceStageName(),
                    source.userId(),
                    resolveUserName(source.userId()),
                    source.workType(),
                    plan.oldBenefitAmount(source.sourceOutputValueId()),
                    plan.newBenefitAmount(source.sourceOutputValueId()),
                    source.targetAmount(),
                    source.previousAdjustedAmount(),
                    source.pendingAmount(),
                    applied.appliedAmount(),
                    applied.remainingAmount());
        }).collect(Collectors.toList());
    }

    private OutputValuePreviewVo toPreviewVo(OutputValueCalculationResult calculation,
                                             OutputAllocationContext allocation,
                                             BenefitAdjustmentPlan adjustmentPlan) {
        OutputValuePreviewVo vo = new OutputValuePreviewVo();
        vo.setBaseAmount(calculation.baseAmount);
        vo.setBenefitAmount(calculation.benefitAmount);
        vo.setBaseRatio(calculation.baseRatio);
        vo.setBenefitRatio(calculation.benefitRatio);
        vo.setBasePart(calculation.basePart);
        vo.setBenefitPart(calculation.benefitPart);
        vo.setCurrentStageAmount(calculation.currentStageAmount);
        vo.setPersonAdjustmentAmount(adjustmentPlan.personResult.appliedTotal());
        vo.setCompanyAdjustmentAmount(adjustmentPlan.companyAppliedAmount);
        vo.setPendingPersonAdjustmentAmount(adjustmentPlan.personResult.remainingTotal());
        BigDecimal adjustmentAmount = adjustmentPlan.personResult.appliedTotal()
                .add(adjustmentPlan.companyAppliedAmount).setScale(2, RoundingMode.HALF_UP);
        vo.setAdjustmentAmount(adjustmentAmount);
        vo.setThisPeriodTotal(calculation.currentStageAmount.add(adjustmentAmount)
                .setScale(2, RoundingMode.HALF_UP));
        vo.setAlreadyAllocated(calculation.alreadyAllocated);
        vo.setIncrementalRatio(calculation.incrementalRatio);
        vo.setCoefficient(calculation.coefficient);
        vo.setAllocationVersion("allocation_v5");
        vo.setAllocationRuleVersionId(allocation.getRuleVersionId());
        vo.setAllocationRuleVersionNo(allocation.getRuleVersionNo());
        vo.setEmployeePoolRate(allocation.getEmployeePoolRate());
        vo.setCompanyBaseRate(allocation.getCompanyBaseRate());
        vo.setEmployeePoolAmount(allocation.getEmployeePoolAmount());
        vo.setCompanyBaseAmount(allocation.getCompanyBaseAmount());
        vo.setWorkTransferAmount(allocation.getWorkTransferAmount());
        vo.setProjectPoolAmount(allocation.getProjectPoolAmount());
        vo.setWorkPools(allocation.getWorkPools().stream()
                .map(this::toWorkPoolVo)
                .collect(Collectors.toList()));
        vo.setAdjustmentDetails(buildAdjustmentDetails(adjustmentPlan, calculation).stream()
                .map(this::toAdjustmentDetailVo)
                .collect(Collectors.toList()));
        vo.setBenefitAdjustments(toBenefitAdjustmentVos(adjustmentPlan));
        return vo;
    }

    private OutputValuePreviewVo.WorkPoolVo toWorkPoolVo(OutputAllocationContext.WorkPool pool) {
        return new OutputValuePreviewVo.WorkPoolVo(
                null,
                pool.getWorkType(),
                WORK_TYPE_LABELS.get(pool.getWorkType()),
                pool.getWorkWeight(),
                pool.getGrossRate(),
                pool.getGrossAmount(),
                pool.getProjectRate(),
                pool.getProjectAmount(),
                pool.getCompanyRate(),
                pool.getCompanyAmount()
        );
    }

    private OutputValuePreviewVo.WorkPoolVo toWorkPoolVo(OutputValueWorkPool pool) {
        return new OutputValuePreviewVo.WorkPoolVo(
                pool.getWorkPoolId(),
                pool.getWorkType(),
                WORK_TYPE_LABELS.get(pool.getWorkType()),
                pool.getStageWorkRatio(),
                pool.getGrossRate(),
                pool.getGrossAmount(),
                pool.getProjectRate(),
                pool.getProjectAmount(),
                pool.getCompanyRate(),
                pool.getCompanyAmount()
        );
    }

    /**
     * 基本收费合同以合同金额作为基本部分基数；基本+效益合同优先使用 base_amount，
     * 老数据未维护 base_amount 或为 0 时兜底到 contract_amount。
     */
    private BigDecimal resolveBaseAmount(Contract contract, boolean hasBenefit) {
        BigDecimal contractAmount = contract.getContractAmount() != null
                ? contract.getContractAmount() : BigDecimal.ZERO;
        BigDecimal baseAmount = contract.getBaseAmount() != null
                ? contract.getBaseAmount() : BigDecimal.ZERO;

        if (!hasBenefit) {
            return contractAmount.signum() > 0 ? contractAmount : baseAmount;
        }
        return baseAmount.signum() > 0 ? baseAmount : contractAmount;
    }

    private BigDecimal resolveBenefitRatio(BigDecimal benefitRatio, BigDecimal baseRatio) {
        return benefitRatio != null && benefitRatio.signum() > 0 ? benefitRatio : baseRatio;
    }

    private BigDecimal positiveOrFallback(BigDecimal value, BigDecimal fallback) {
        return value != null && value.signum() > 0 ? value : fallback;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record OutputValueCalculationResult(
            BigDecimal baseAmount,
            BigDecimal benefitAmount,
            BigDecimal baseRatio,
            BigDecimal benefitRatio,
            BigDecimal basePart,
            BigDecimal benefitPart,
            BigDecimal currentStageAmount,
            BigDecimal completionRatio,
            BigDecimal alreadyAllocated,
            BigDecimal incrementalRatio,
            BigDecimal coefficient
    ) {}

    private record BenefitStageAdjustment(
            OutputValue sourceOutput,
            String sourceStageName,
            BigDecimal sourceBaseRatio,
            BigDecimal sourceBenefitRatio,
            BigDecimal oldBenefitAmount,
            BigDecimal newBenefitAmount,
            BigDecimal oldStageAmount,
            BigDecimal newStageAmount,
            BigDecimal stageTargetAmount,
            BigDecimal personTargetAmount,
            BigDecimal personPreviousAmount,
            BigDecimal companyTargetAmount,
            BigDecimal companyPreviousAmount,
            BigDecimal companyPendingAmount
    ) {}

    private record BenefitAdjustmentPlan(
            List<BenefitStageAdjustment> stages,
            BenefitAdjustmentAllocator.Result personResult,
            BigDecimal companyAppliedAmount
    ) {
        private static BenefitAdjustmentPlan empty() {
            return new BenefitAdjustmentPlan(
                    Collections.emptyList(),
                    BenefitAdjustmentAllocator.allocate(Collections.emptyList(), Collections.emptyMap()),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        private BigDecimal oldBenefitAmount(Long sourceOutputValueId) {
            return stages.stream()
                    .filter(stage -> Objects.equals(stage.sourceOutput().getOutputValueId(), sourceOutputValueId))
                    .map(BenefitStageAdjustment::oldBenefitAmount)
                    .findFirst().orElse(null);
        }

        private BigDecimal newBenefitAmount(Long sourceOutputValueId) {
            return stages.stream()
                    .filter(stage -> Objects.equals(stage.sourceOutput().getOutputValueId(), sourceOutputValueId))
                    .map(BenefitStageAdjustment::newBenefitAmount)
                    .findFirst().orElse(null);
        }
    }

    /** 在职状态：优先 DTO 传入，其次查 sys_user.employment_status，默认 1（在职） */
    private int resolveIsActive(Long userId, Integer override) {
        if (override != null) return override;
        SysUser u = sysUserMapper.selectById(userId);
        if (u != null && u.getEmploymentStatus() != null) {
            return u.getEmploymentStatus();
        }
        return 1;
    }

    // ==================== 状态流转 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmOutputValue(Long outputValueId, Long operatorId, Long approveUserId) {
        OutputValue existing = outputValueMapper.selectById(outputValueId);
        if (existing == null) throw new BusinessException(ErrorType.OUTPUT_VALUE_NOT_FOUND);

        List<OutputValue> stageOutputValues = outputValueMapper.selectByProjectStageIdForUpdate(existing.getProjectStageId());
        OutputValue ov = stageOutputValues.stream()
                .filter(item -> Objects.equals(item.getOutputValueId(), outputValueId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorType.OUTPUT_VALUE_NOT_FOUND));
        if (ov.getStatus() != 0) throw new BusinessException(ErrorType.OUTPUT_VALUE_STATUS_INVALID, "当前状态无法确认");
        if (hasConfirmedStageOutputValue(stageOutputValues, outputValueId)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED,
                    "您的这个产值已经通过其他分配单确定，无法进行二次确认");
        }
        assertCurrentHandler(ov, operatorId, "您不是当前确认人");
        if (approveUserId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择审批人");
        }

        ov.setStatus(1); // 待审核
        ov.setApproveUserId(approveUserId);
        ov.setCurrentHandlerId(approveUserId);
        outputValueMapper.updateById(ov);

        // 同步流转统一审批流：L1（确认）→ L2（审批），下一级审批人 = approveUserId
        ApprovalRecords pending = approvalFlowService.getCurrentPending(ApprovalBizType.OUTPUT, outputValueId);
        if (pending != null) {
            ApproveDto approve = new ApproveDto();
            approve.setRecordId(pending.getApprovalRecordId());
            approve.setPass(true);
            approve.setNextApproverId(approveUserId);
            approvalFlowService.approve(approve, operatorId);
        }
    }

    private boolean hasConfirmedStageOutputValue(List<OutputValue> outputValues, Long excludeOutputValueId) {
        if (outputValues == null || outputValues.isEmpty()) {
            return false;
        }
        return outputValues.stream()
                .filter(item -> excludeOutputValueId == null
                        || !Objects.equals(item.getOutputValueId(), excludeOutputValueId))
                .anyMatch(item -> item.getStatus() != null && item.getStatus() >= 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveOutputValue(Long outputValueId, Long operatorId, Long payUserId) {
        OutputValue ov = outputValueMapper.selectById(outputValueId);
        if (ov == null) throw new BusinessException(ErrorType.OUTPUT_VALUE_NOT_FOUND);
        if (ov.getStatus() != 1) throw new BusinessException(ErrorType.OUTPUT_VALUE_STATUS_INVALID, "当前状态无法审批");
        assertCurrentHandler(ov, operatorId, "您不是当前审批人");
        if (payUserId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择发放人");
        }

        ov.setStatus(2); // 已审批
        ov.setApprovedTime(LocalDateTime.now());
        ov.setPayUserId(payUserId);
        ov.setCurrentHandlerId(payUserId);
        outputValueMapper.updateById(ov);

        // 同步流转统一审批流：L2（审批）→ L3（发放），下一级审批人 = payUserId
        ApprovalRecords pending = approvalFlowService.getCurrentPending(ApprovalBizType.OUTPUT, outputValueId);
        if (pending != null) {
            ApproveDto approve = new ApproveDto();
            approve.setRecordId(pending.getApprovalRecordId());
            approve.setPass(true);
            approve.setNextApproverId(payUserId);
            approvalFlowService.approve(approve, operatorId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOutputValue(Long outputValueId, Long operatorId) {
        OutputValue ov = outputValueMapper.selectById(outputValueId);
        if (ov == null) throw new BusinessException(ErrorType.OUTPUT_VALUE_NOT_FOUND);
        if (ov.getStatus() != 2) throw new BusinessException(ErrorType.OUTPUT_VALUE_STATUS_INVALID, "当前状态无法发放");
        assertCurrentHandler(ov, operatorId, "您不是当前发放人");

        ov.setStatus(3); // 已发放
        ov.setPaidTime(LocalDateTime.now());
        ov.setCurrentHandlerId(null);
        outputValueMapper.updateById(ov);

        // 同步流转统一审批流：L3（发放）终审通过，流程结束
        ApprovalRecords pending = approvalFlowService.getCurrentPending(ApprovalBizType.OUTPUT, outputValueId);
        if (pending != null) {
            ApproveDto approve = new ApproveDto();
            approve.setRecordId(pending.getApprovalRecordId());
            approve.setPass(true);
            approve.setTerminate(true);
            approvalFlowService.approve(approve, operatorId);
        }
    }

    @Override
    public void terminateOutputValue(Long outputValueId, Long operatorId) {
        OutputValue ov = outputValueMapper.selectById(outputValueId);
        if (ov == null) throw new BusinessException(ErrorType.OUTPUT_VALUE_NOT_FOUND);
        // 仅 待确认(0) / 待审核(1) 状态可终审；已审批(2) 走正常 pay 即可
        if (ov.getStatus() == null || ov.getStatus() >= 2) {
            throw new BusinessException(ErrorType.OUTPUT_VALUE_STATUS_INVALID, "当前状态无法终审");
        }
        assertCurrentHandler(ov, operatorId, "您不是当前处理人，无法终审");

        // 同步流转统一审批流：当前节点（L1 确认 / L2 审批）终审通过，流程结束。
        // approvalFlowService.approve 内部会校验「当前节点 allow_terminate=1」，
        // 若流程配置不允许终审会抛 BusinessException，由 GlobalExceptionHandler 兜底。
        ApprovalRecords pending = approvalFlowService.getCurrentPending(ApprovalBizType.OUTPUT, outputValueId);
        if (pending == null) {
            throw new BusinessException(ErrorType.OUTPUT_VALUE_STATUS_INVALID, "未找到待审节点，无法终审");
        }
        ApproveDto approve = new ApproveDto();
        approve.setRecordId(pending.getApprovalRecordId());
        approve.setPass(true);
        approve.setTerminate(true);
        approvalFlowService.approve(approve, operatorId);

        // 产值单状态直接置为 已发放（3），跳过后续审批/发放环节
        ov.setStatus(3);
        ov.setApprovedTime(LocalDateTime.now());
        ov.setPaidTime(LocalDateTime.now());
        ov.setCurrentHandlerId(null);
        outputValueMapper.updateById(ov);
    }

    private void assertCurrentHandler(OutputValue ov, Long operatorId, String message) {
        if (ov.getCurrentHandlerId() == null) {
            return;
        }
        if (!Objects.equals(ov.getCurrentHandlerId(), operatorId)) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, message);
        }
    }

    // ==================== 统计 ====================

    @Override
    public Map<String, Object> getStatistics() {
        List<OutputValue> all = outputValueMapper.selectList(null);
        Map<String, Object> stats = new HashMap<>();
        // 各状态独立计数（供前端 tab 角标使用）
        stats.put("totalCount", all.size());
        stats.put("pendingCount", all.stream().filter(o -> o.getStatus() == 0 || o.getStatus() == 1).count());
        stats.put("confirmCount", all.stream().filter(o -> o.getStatus() == 0).count());
        stats.put("reviewCount", all.stream().filter(o -> o.getStatus() == 1).count());
        stats.put("approvedCount", all.stream().filter(o -> o.getStatus() == 2).count());
        stats.put("paidCount", all.stream().filter(o -> o.getStatus() == 3).count());
        // "已发放"仅统计 status=3 的记录，作为现金动作统计
        stats.put("paidAmount", all.stream()
                .filter(o -> o.getStatus() == 3)
                .map(OutputValue::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("totalAmount", all.stream()
                .map(OutputValue::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return stats;
    }

    // ==================== 导出 Excel ====================

    @Override
    public void exportOutputValues(Integer status, String keyword, HttpServletResponse response) throws IOException {
        List<OutputValueVo> list = getOutputValueList(status);
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            list = list.stream()
                    .filter(item -> contains(item.getProjectName(), k) || contains(item.getProjectCode(), k))
                    .collect(Collectors.toList());
        }

        List<OutputValueExcelData> data = new ArrayList<>();
        for (OutputValueVo item : list) {
            List<OutputValueVo.DistributionItemVo> distributions = item.getDistributions();
            if (distributions == null || distributions.isEmpty()) {
                data.add(toExcelData(item, null));
                continue;
            }
            for (OutputValueVo.DistributionItemVo distribution : distributions) {
                data.add(toExcelData(item, distribution));
            }
        }

        String fileName = "产值分配数据_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        setExcelResponseHeader(response, fileName);
        EasyExcel.write(response.getOutputStream(), OutputValueExcelData.class)
                .sheet("产值分配")
                .doWrite(data);
    }

    private OutputValueExcelData toExcelData(OutputValueVo item, OutputValueVo.DistributionItemVo distribution) {
        return OutputValueExcelData.builder()
                .projectName(item.getProjectName())
                .projectCode(item.getProjectCode())
                .projectTypeName(item.getProjectTypeName())
                .stageName(item.getStageName())
                .quarter(item.getQuarter())
                .status(labelOf(STATUS_LABELS, item.getStatus()))
                .totalAmount(item.getTotalAmount())
                .stageCumulativeAmount(item.getStageCumulativeAmount())
                .previousCumulativeAmount(item.getPreviousCumulativeAmount())
                .baseAmountPart(item.getBaseAmountPart())
                .benefitAmountPart(item.getBenefitAmountPart())
                .companyReserve(item.getCompanyReserve())
                .companyBaseAmount(item.getCompanyBaseAmount())
                .projectPoolAmount(item.getProjectPoolAmount())
                .otherAmount(item.getOtherAmount())
                .subsidyAmount(item.getSubsidyAmount())
                .submitUserName(item.getSubmitUserName())
                .submitTime(formatTime(item.getSubmitTime()))
                .approvedTime(formatTime(item.getApprovedTime()))
                .paidTime(formatTime(item.getPaidTime()))
                .userName(distribution == null ? null : distribution.getUserName())
                .userRole(distribution == null ? null : distribution.getUserRole())
                .workType(distribution == null ? null : labelOf(WORK_TYPE_LABELS, distribution.getWorkType()))
                .allocRatio(distribution == null ? null : defaultIfNull(distribution.getAllocRatio(), distribution.getRatio()))
                .roleAllocRatio(distribution == null ? null : distribution.getRoleAllocRatio())
                .completionRatio(distribution == null ? null : distribution.getCompletionRatio())
                .distType(distribution == null ? null : labelOf(DIST_TYPE_LABELS, distribution.getDistType()))
                .activeStatus(distribution == null ? null : Objects.equals(distribution.getIsActive(), 0) ? "离职" : "在职")
                .plannedAmount(distribution == null ? null : distribution.getPlannedAmount())
                .actualAmount(distribution == null ? null : distribution.getAmount())
                .build();
    }

    private boolean contains(String raw, String keyword) {
        return raw != null && raw.contains(keyword);
    }

    private String labelOf(Map<Integer, String> labels, Integer value) {
        return value == null ? "未知" : labels.getOrDefault(value, "未知");
    }

    private BigDecimal defaultIfNull(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(EXPORT_TIME_FMT);
    }

    private void setExcelResponseHeader(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    // ==================== VO 转换 ====================

    private OutputValueVo convertToVo(OutputValue ov) {
        OutputValueVo vo = new OutputValueVo();
        vo.setOutputValueId(ov.getOutputValueId());
        vo.setProjectId(ov.getProjectId());
        vo.setProjectStageId(ov.getProjectStageId());
        vo.setQuarter(ov.getQuarter());
        vo.setTotalAmount(ov.getTotalAmount());
        vo.setCompanyReserve(ov.getCompanyReserve());
        vo.setLeaderExtra(ov.getLeaderExtra());
        vo.setOtherAmount(ov.getOtherAmount());
        vo.setSubsidyAmount(ov.getSubsidyAmount());
        vo.setStageCumulativeAmount(ov.getStageCumulativeAmount());
        vo.setPreviousCumulativeAmount(ov.getPreviousCumulativeAmount());
        vo.setBaseAmountPart(ov.getBaseAmountPart());
        vo.setBenefitAmountPart(ov.getBenefitAmountPart());
        vo.setBenefitSnapshot(ov.getBenefitSnapshot());
        vo.setCurrentStageAmount(ov.getCurrentStageAmount());
        vo.setAdjustmentAmount(ov.getAdjustmentAmount());
        vo.setPersonAdjustmentAmount(ov.getPersonAdjustmentAmount());
        vo.setCompanyAdjustmentAmount(ov.getCompanyAdjustmentAmount());
        vo.setPendingPersonAdjustmentAmount(ov.getPendingPersonAdjustmentAmount());
        vo.setStageCompletionRatio(ov.getStageCompletionRatio());
        vo.setStageIncrementalRatio(ov.getStageIncrementalRatio());
        vo.setCoefficient(ov.getCoefficient());
        vo.setBaseAmountSnapshot(ov.getBaseAmountSnapshot());
        vo.setBenefitAmountSnapshot(ov.getBenefitAmountSnapshot());
        vo.setBaseRatioSnapshot(ov.getBaseRatioSnapshot());
        vo.setBenefitRatioSnapshot(ov.getBenefitRatioSnapshot());
        vo.setCalculationVersion(ov.getCalculationVersion());
        vo.setAllocationVersion(ov.getAllocationVersion());
        vo.setAllocationRuleVersionId(ov.getAllocationRuleVersionId());
        vo.setEmployeePoolAmount(ov.getEmployeePoolAmount());
        vo.setCompanyBaseAmount(ov.getCompanyBaseAmount());
        vo.setWorkTransferAmount(ov.getWorkTransferAmount());
        vo.setProjectPoolAmount(ov.getProjectPoolAmount());
        vo.setStatus(ov.getStatus());
        vo.setConfirmUserId(ov.getConfirmUserId());
        vo.setApproveUserId(ov.getApproveUserId());
        vo.setPayUserId(ov.getPayUserId());
        vo.setCurrentHandlerId(ov.getCurrentHandlerId());
        vo.setSubmitTime(ov.getSubmitTime());
        vo.setApprovedTime(ov.getApprovedTime());
        vo.setPaidTime(ov.getPaidTime());
        vo.setCreatedTime(ov.getCreatedTime());

        // 项目信息
        Project project = projectService.getProjectById(ov.getProjectId());
        if (project != null) {
            vo.setProjectName(project.getProjectName());
            vo.setProjectCode(project.getProjectCode());
            if (project.getProjectType() != null) {
                ProjectType type = projectTypeService.getProjectTypeById(project.getProjectType());
                if (type != null) vo.setProjectTypeName(type.getProjectTypeName());
            }
        }

        // 阶段信息
        ProjectStage stage = projectStageService.getProjectStageById(ov.getProjectStageId());
        if (stage != null) {
            vo.setStageName(stage.getStageName());
            vo.setStageOutput(stage.getStageOutput());
        }

        // 提交人
        if (ov.getSubmitUserId() != null) {
            SysUser user = sysUserMapper.selectById(ov.getSubmitUserId());
            if (user != null) vo.setSubmitUserName(user.getRealName());
        }
        vo.setConfirmUserName(resolveUserName(ov.getConfirmUserId()));
        vo.setApproveUserName(resolveUserName(ov.getApproveUserId()));
        vo.setPayUserName(resolveUserName(ov.getPayUserId()));
        vo.setCurrentHandlerName(resolveUserName(ov.getCurrentHandlerId()));

        // 分配明细
        LambdaQueryWrapper<OutputValueDistribution> distWrapper = new LambdaQueryWrapper<>();
        distWrapper.eq(OutputValueDistribution::getOutputValueId, ov.getOutputValueId());
        List<OutputValueDistribution> dists = distributionMapper.selectList(distWrapper);

        // 项目成员角色（用于展示"项目经理/项目成员"）
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(ov.getProjectId());
        Map<Long, Long> userRoleMap = new HashMap<>();
        if (members != null) {
            for (ProjectMember m : members) {
                userRoleMap.put(m.getUserId(), m.getProjectRole());
            }
        }

        List<OutputValueVo.DistributionItemVo> distVos = dists.stream().map(d -> {
            OutputValueVo.DistributionItemVo item = new OutputValueVo.DistributionItemVo();
            item.setDistributionId(d.getDistributionId());
            item.setComponentType(d.getComponentType());
            item.setSourceDistributionId(d.getSourceDistributionId());
            item.setSourceOutputValueId(d.getSourceOutputValueId());
            item.setSourceProjectStageId(d.getSourceProjectStageId());
            item.setUserId(d.getUserId());
            item.setWorkType(d.getWorkType());
            item.setRatio(d.getRatio());
            item.setAllocRatio(d.getAllocRatio());
            item.setCompletionRatio(d.getCompletionRatio());
            item.setWorkPoolId(d.getWorkPoolId());
            item.setRoleAllocRatio(d.getRoleAllocRatio());
            item.setPlannedAmount(d.getPlannedAmount());
            item.setCompanyDelta(d.getCompanyDelta());
            item.setAdjustmentTargetAmount(d.getAdjustmentTargetAmount());
            item.setPreviousAdjustedAmount(d.getPreviousAdjustedAmount());
            item.setRemainingAdjustmentAmount(d.getRemainingAdjustmentAmount());
            item.setDistType(d.getDistType());
            item.setIsActive(d.getIsActive());
            item.setAmount(d.getAmount());

            SysUser u = sysUserMapper.selectById(d.getUserId());
            if (u != null) item.setUserName(u.getRealName());

            Long roleId = userRoleMap.get(d.getUserId());
            item.setUserRole(ROLE_MANAGER_ID.equals(roleId) ? "项目经理" : "项目成员");

            return item;
        }).collect(Collectors.toList());

        vo.setDistributions(distVos);
        vo.setBenefitAdjustments(dists.stream()
                .filter(d -> Objects.equals(d.getComponentType(), COMPONENT_BENEFIT_ADJUSTMENT))
                .map(this::toStoredBenefitAdjustmentVo)
                .collect(Collectors.toList()));
        vo.setWorkPools(outputAllocationRuleService.getWorkPools(ov.getOutputValueId()).stream()
                .map(this::toWorkPoolVo)
                .collect(Collectors.toList()));

        LambdaQueryWrapper<OutputValueAdjustmentDetail> adjustmentWrapper = new LambdaQueryWrapper<>();
        adjustmentWrapper.eq(OutputValueAdjustmentDetail::getOutputValueId, ov.getOutputValueId())
                .orderByAsc(OutputValueAdjustmentDetail::getCreatedTime);
        List<OutputValueAdjustmentDetail> adjustmentDetails = adjustmentDetailMapper.selectList(adjustmentWrapper);
        vo.setAdjustmentDetails(adjustmentDetails.stream()
                .map(this::toAdjustmentDetailVo)
                .collect(Collectors.toList()));
        return vo;
    }

    private OutputValuePreviewVo.AdjustmentDetailVo toAdjustmentDetailVo(OutputValueAdjustmentDetail detail) {
        OutputValuePreviewVo.AdjustmentDetailVo vo = new OutputValuePreviewVo.AdjustmentDetailVo();
        vo.setAdjustmentDetailId(detail.getAdjustmentDetailId());
        vo.setSourceOutputValueId(detail.getSourceOutputValueId());
        vo.setSourceProjectStageId(detail.getSourceProjectStageId());
        vo.setSourceStageName(detail.getSourceStageName());
        vo.setSourceBaseRatio(detail.getSourceBaseRatio());
        vo.setSourceBenefitRatio(detail.getSourceBenefitRatio());
        vo.setOldBaseAmountSnapshot(detail.getOldBaseAmountSnapshot());
        vo.setOldBenefitAmountSnapshot(detail.getOldBenefitAmountSnapshot());
        vo.setOldStageAmount(detail.getOldStageAmount());
        vo.setNewBaseAmountSnapshot(detail.getNewBaseAmountSnapshot());
        vo.setNewBenefitAmountSnapshot(detail.getNewBenefitAmountSnapshot());
        vo.setNewStageAmount(detail.getNewStageAmount());
        vo.setAlreadyAdjustedAmount(detail.getAlreadyAdjustedAmount());
        vo.setAdjustmentAmount(detail.getAdjustmentAmount());
        vo.setPersonAdjustmentAmount(detail.getPersonAdjustmentAmount());
        vo.setCompanyAdjustmentAmount(detail.getCompanyAdjustmentAmount());
        vo.setRemainingPersonAdjustmentAmount(detail.getRemainingPersonAdjustmentAmount());
        return vo;
    }

    private OutputValuePreviewVo.BenefitAdjustmentVo toStoredBenefitAdjustmentVo(
            OutputValueDistribution distribution) {
        OutputValue sourceOutput = distribution.getSourceOutputValueId() == null
                ? null : outputValueMapper.selectById(distribution.getSourceOutputValueId());
        OutputValue settlementOutput = distribution.getOutputValueId() == null
                ? null : outputValueMapper.selectById(distribution.getOutputValueId());
        ProjectStage sourceStage = distribution.getSourceProjectStageId() == null
                ? null : projectStageService.getProjectStageById(distribution.getSourceProjectStageId());
        BigDecimal target = money(distribution.getAdjustmentTargetAmount());
        BigDecimal previous = money(distribution.getPreviousAdjustedAmount());
        return new OutputValuePreviewVo.BenefitAdjustmentVo(
                distribution.getSourceDistributionId(),
                distribution.getSourceOutputValueId(),
                distribution.getSourceProjectStageId(),
                sourceStage == null ? null : sourceStage.getStageName(),
                distribution.getUserId(),
                resolveUserName(distribution.getUserId()),
                distribution.getWorkType(),
                sourceOutput == null ? null : resolveHistoricalBenefitSnapshot(sourceOutput),
                settlementOutput == null ? null : settlementOutput.getBenefitAmountSnapshot(),
                target,
                previous,
                target.subtract(previous).setScale(2, RoundingMode.HALF_UP),
                money(distribution.getAmount()),
                money(distribution.getRemainingAdjustmentAmount()));
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) return null;
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }
}
