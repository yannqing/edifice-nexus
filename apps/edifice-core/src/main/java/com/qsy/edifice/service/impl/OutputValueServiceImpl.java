package com.qsy.edifice.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.CreateOutputValueDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.excel.OutputValueExcelData;
import com.qsy.edifice.domain.vo.OutputValueVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.OutputValueDistributionMapper;
import com.qsy.edifice.mapper.OutputValueMapper;
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
 *   employeePool   = totalAmount × 40%      （员工池）
 *   companyAccount = totalAmount × 60%      （公司账主体）
 *   planned_i      = employeePool × allocRatio_i / 100
 *   actual_i       = planned_i × completionRatio_i / 100  （在职员工）
 *   降档差额（planned_i - actual_i） → 公司账
 *   离职成员 planned_i → 公司账（独立记 other_amount，但实际归公司）
 *
 * 守恒：Σ actual + companyReserve = totalAmount
 *   其中 companyReserve = 60% 主体 + 降档差额 + 离职兜底
 */
@Slf4j
@Service
public class OutputValueServiceImpl implements OutputValueService {

    private static final Long ROLE_MANAGER_ID = 101L;

    /** 公司账比例（%）— v0.4 修订：60% */
    private static final BigDecimal COMPANY_RESERVE_RATE = new BigDecimal("60");
    /** 员工池比例（%）— v0.4 修订：40% */
    private static final BigDecimal PERSONAL_POOL_RATE = new BigDecimal("40");
    private static final BigDecimal BD_100 = new BigDecimal("100");
    /** 守恒校验容差（元），避免 BigDecimal 舍入带来的微分误差误报 */
    private static final BigDecimal INVARIANT_TOLERANCE = new BigDecimal("0.05");
    /** 分配比例合计校验容差（百分点） */
    private static final BigDecimal ALLOC_TOLERANCE = new BigDecimal("0.01");

    /** 分配类型常量 */
    private static final int DIST_TYPE_NORMAL = 0;
    private static final int DIST_TYPE_DOWNGRADE = 1;
    private static final int DIST_TYPE_OTHER = 4;
    /** 可创建产值分配的阶段状态：3-已验收 / 6-已完成 */
    private static final Set<Integer> OUTPUT_VALUE_ALLOWED_STAGE_STATUSES = Set.of(3, 6);
    private static final DateTimeFormatter EXPORT_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<Integer, String> STATUS_LABELS = Map.of(
            0, "待确认", 1, "待审核", 2, "已审批", 3, "已发放"
    );
    private static final Map<Integer, String> WORK_TYPE_LABELS = Map.of(
            0, "管理工作", 1, "基础工作", 2, "智励工作"
    );
    private static final Map<Integer, String> DIST_TYPE_LABELS = Map.of(
            0, "员工正常", 1, "员工降档", 2, "领导兜底", 3, "公司留存", 4, "其他金额"
    );

    @Resource
    private OutputValueMapper outputValueMapper;

    @Resource
    private OutputValueDistributionMapper distributionMapper;

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
        if (dto.getDistributions() == null || dto.getDistributions().isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请添加至少一条分配明细");
        }
        if (!StringUtils.hasText(dto.getQuarter())) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择所属季度");
        }
        if (dto.getConfirmUserId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择确认人");
        }
        List<OutputValue> stageOutputValues = outputValueMapper.selectByProjectStageIdForUpdate(dto.getProjectStageId());
        if (hasConfirmedStageOutputValue(stageOutputValues, null)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "该阶段产值已通过分配单确认，无法重复提交");
        }

        // 1. 系统自动算 totalAmount
        StageCumulativeResult cumulative = calcStageCumulative(dto.getProjectId(), dto.getProjectStageId());
        BigDecimal total = cumulative.current.setScale(2, RoundingMode.HALF_UP);

        if (total.signum() == 0) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "本阶段产值为 0，请检查合同金额和阶段产值比例");
        }
        if (total.signum() < 0) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "本阶段产值不能为负，请检查合同金额和阶段产值比例");
        }

        // 2. v0.4 比例：员工 40% / 公司 60%
        BigDecimal employeePool = total.multiply(PERSONAL_POOL_RATE)
                .divide(BD_100, 2, RoundingMode.HALF_UP);
        BigDecimal companyMain = total.multiply(COMPANY_RESERVE_RATE)
                .divide(BD_100, 2, RoundingMode.HALF_UP);

        // 3. 校验分配比例合计 ≈ 100
        BigDecimal sumAllocRatio = dto.getDistributions().stream()
                .map(i -> i.getAllocRatio() == null ? BigDecimal.ZERO : i.getAllocRatio())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumAllocRatio.subtract(BD_100).abs().compareTo(ALLOC_TOLERANCE) > 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID,
                    "分配比例合计应为 100%，当前为 " + sumAllocRatio.stripTrailingZeros().toPlainString() + "%");
        }

        // 4. 逐条计算分配明细
        BigDecimal downgradeDelta = BigDecimal.ZERO;  // 降档差额（→ 公司账）
        BigDecimal otherAmount = BigDecimal.ZERO;     // 离职兜底（→ 公司账，独立记账）
        List<OutputValueDistribution> distEntities = new ArrayList<>();

        for (int i = 0; i < dto.getDistributions().size(); i++) {
            CreateOutputValueDto.DistributionItem item = dto.getDistributions().get(i);
            int rowNum = i + 1;

            if (item.getUserId() == null) {
                throw new BusinessException(ErrorType.ARGS_NOT_NULL, "第 " + rowNum + " 行分配对象为空");
            }
            BigDecimal alloc = item.getAllocRatio() == null ? BigDecimal.ZERO : item.getAllocRatio();
            BigDecimal completion = item.getCompletionRatio() == null ? BD_100 : item.getCompletionRatio();

            if (alloc.signum() < 0 || alloc.compareTo(BD_100) > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "第 " + rowNum + " 行分配比例应在 0-100 之间");
            }
            if (completion.signum() < 0 || completion.compareTo(BD_100) > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "第 " + rowNum + " 行完成比例应在 0-100 之间");
            }

            int isActive = resolveIsActive(item.getUserId(), item.getIsActive());

            // 注意：负 total 会让 planned 也是负；按比例计算自然成立
            BigDecimal planned = employeePool.multiply(alloc)
                    .divide(BD_100, 2, RoundingMode.HALF_UP);

            int distType;
            BigDecimal actualAmount;

            if (isActive == 0) {
                distType = DIST_TYPE_OTHER;
                actualAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                otherAmount = otherAmount.add(planned);
            } else if (completion.compareTo(BD_100) < 0) {
                distType = DIST_TYPE_DOWNGRADE;
                actualAmount = planned.multiply(completion)
                        .divide(BD_100, 2, RoundingMode.HALF_UP);
                downgradeDelta = downgradeDelta.add(planned.subtract(actualAmount));
            } else {
                distType = DIST_TYPE_NORMAL;
                actualAmount = planned;
            }

            distEntities.add(OutputValueDistribution.builder()
                    .userId(item.getUserId())
                    .workType(item.getWorkType() != null ? item.getWorkType() : 1)
                    .ratio(alloc)
                    .allocRatio(alloc)
                    .completionRatio(completion)
                    .distType(distType)
                    .isActive(isActive)
                    .amount(actualAmount)
                    .build());
        }

        // 5. 公司账（v0.4）= 60% 主体 + 降档差额 + 离职兜底
        BigDecimal companyReserve = companyMain.add(downgradeDelta).add(otherAmount);

        // 6. 守恒校验：Σ actual + companyReserve ≈ total
        BigDecimal sumActual = distEntities.stream()
                .map(OutputValueDistribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sum = companyReserve.add(sumActual);
        if (sum.subtract(total).abs().compareTo(INVARIANT_TOLERANCE) > 0) {
            throw new BusinessException(ErrorType.OPERATION_FAILED,
                    "金额守恒失败：公司账 " + companyReserve + "（60% 主体 " + companyMain
                            + " + 降档差额 " + downgradeDelta + " + 离职兜底 " + otherAmount
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
                .stageCumulativeAmount(cumulative.current)
                .previousCumulativeAmount(cumulative.previous)
                .baseAmountPart(cumulative.basePart)
                .benefitAmountPart(cumulative.benefitPart)
                .benefitSnapshot(cumulative.benefitAmountUsed)
                .status(0)
                .submitUserId(userId)
                .confirmUserId(dto.getConfirmUserId())
                .currentHandlerId(dto.getConfirmUserId())
                .submitTime(LocalDateTime.now())
                .build();
        outputValueMapper.insert(ov);

        for (OutputValueDistribution d : distEntities) {
            d.setOutputValueId(ov.getOutputValueId());
            distributionMapper.insert(d);
        }

        log.info("[v0.4] 创建产值分配单 id={} 季度={} total={}（基本{} + 效益{}） 公司账={} 员工实得={}",
                ov.getOutputValueId(), ov.getQuarter(), total,
                cumulative.basePart, cumulative.benefitPart,
                companyReserve, sumActual);

        return ov.getOutputValueId();
    }

    /**
     * 计算指定阶段产值。阶段比例是单阶段比例，不是项目累计比例。
     */
    private StageCumulativeResult calcStageCumulative(Long projectId, Long stageId) {
        ProjectStage stage = projectStageService.getProjectStageById(stageId);
        if (stage == null || !projectId.equals(stage.getProjectId())) {
            throw new BusinessException(ErrorType.STAGE_NOT_FOUND);
        }
        if (!OUTPUT_VALUE_ALLOWED_STAGE_STATUSES.contains(stage.getStageStatus())) {
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

        BigDecimal basePart = baseAmt.multiply(baseRatio)
                .divide(BD_100, 2, RoundingMode.HALF_UP);
        BigDecimal benefitPart = benefitAmt.multiply(benefitRatio)
                .divide(BD_100, 2, RoundingMode.HALF_UP);
        BigDecimal currentCumulative = basePart.add(benefitPart);

        return new StageCumulativeResult(currentCumulative, BigDecimal.ZERO,
                basePart, benefitPart, benefitAmt);
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

    /** 阶段累计计算结果 */
    private record StageCumulativeResult(
            BigDecimal current,
            BigDecimal previous,
            BigDecimal basePart,
            BigDecimal benefitPart,
            BigDecimal benefitAmountUsed
    ) {}

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
        stats.put("pendingCount", all.stream().filter(o -> o.getStatus() == 0 || o.getStatus() == 1).count());
        stats.put("approvedCount", all.stream().filter(o -> o.getStatus() == 2).count());
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
                .completionRatio(distribution == null ? null : distribution.getCompletionRatio())
                .distType(distribution == null ? null : labelOf(DIST_TYPE_LABELS, distribution.getDistType()))
                .activeStatus(distribution == null ? null : Objects.equals(distribution.getIsActive(), 0) ? "离职" : "在职")
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
            item.setUserId(d.getUserId());
            item.setWorkType(d.getWorkType());
            item.setRatio(d.getRatio());
            item.setAllocRatio(d.getAllocRatio());
            item.setCompletionRatio(d.getCompletionRatio());
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
        return vo;
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) return null;
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }
}
