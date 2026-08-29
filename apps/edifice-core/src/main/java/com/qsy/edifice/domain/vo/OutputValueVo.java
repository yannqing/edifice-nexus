package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "产值分配单VO")
public class OutputValueVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long outputValueId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String projectName;
    private String projectCode;
    private String projectTypeName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectStageId;

    private String stageName;
    private BigDecimal stageOutput;

    /** 所属季度，格式 YYYY-Qn */
    private String quarter;

    /** 阶段产值总额 */
    private BigDecimal totalAmount;

    /** 公司留存金额（公司基础留存 + 各类转入） */
    private BigDecimal companyReserve;

    /** 领导兜底（降档差额累计） */
    private BigDecimal leaderExtra;

    /** 其他金额（离职成员未发金额累计） */
    private BigDecimal otherAmount;

    /** 公司补贴（只记录不计入产值） */
    private BigDecimal subsidyAmount;

    // ==================== v0.4 阶段累计快照 ====================

    /** 当前阶段应得（含基本+效益） */
    private BigDecimal stageCumulativeAmount;

    /** 上一次产值分配单的累计 */
    private BigDecimal previousCumulativeAmount;

    /** 本期基本部分 */
    private BigDecimal baseAmountPart;

    /** 本期效益部分 */
    private BigDecimal benefitAmountPart;

    /** 快照：本单创建时合同的预计效益值 */
    private BigDecimal benefitSnapshot;

    /** 当前阶段纯产值，不含历史补差 */
    private BigDecimal currentStageAmount;

    /** 历史阶段补差合计，可正可负 */
    private BigDecimal adjustmentAmount;

    private BigDecimal personAdjustmentAmount;
    private BigDecimal companyAdjustmentAmount;
    private BigDecimal pendingPersonAdjustmentAmount;

    /** 创建时阶段的完成比例（%，0-100） */
    private BigDecimal stageCompletionRatio;

    /** 本次增量完成比例（%，0-100） */
    private BigDecimal stageIncrementalRatio;

    /** 分配时使用的系数（默认 1.00） */
    private BigDecimal coefficient;

    /** 快照：本单创建时合同的基本金额 */
    private BigDecimal baseAmountSnapshot;

    /** 快照：本单创建时合同的效益金额 */
    private BigDecimal benefitAmountSnapshot;

    private BigDecimal baseRatioSnapshot;
    private BigDecimal benefitRatioSnapshot;

    /** 计算版本 */
    private String calculationVersion;

    /** 人员分配计算版本 */
    private String allocationVersion;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long allocationRuleVersionId;

    private BigDecimal employeePoolAmount;
    private BigDecimal companyBaseAmount;
    private BigDecimal workTransferAmount;
    private BigDecimal projectPoolAmount;

    private List<OutputValuePreviewVo.WorkPoolVo> workPools;

    /** 0-待确认/1-待审核/2-已审批/3-已发放 */
    private Integer status;

    private String submitUserName;
    private String confirmUserName;
    private String approveUserName;
    private String payUserName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long confirmUserId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long approveUserId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long payUserId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentHandlerId;

    private String currentHandlerName;

    private LocalDateTime submitTime;
    private LocalDateTime approvedTime;
    private LocalDateTime paidTime;
    private LocalDateTime createdTime;

    private List<DistributionItemVo> distributions;

    private List<OutputValuePreviewVo.AdjustmentDetailVo> adjustmentDetails;

    private List<OutputValuePreviewVo.BenefitAdjustmentVo> benefitAdjustments;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DistributionItemVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long distributionId;

        /** 0-当前阶段正常分配/1-历史效益补差扣回 */
        private Integer componentType;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long sourceDistributionId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long sourceOutputValueId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long sourceProjectStageId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long userId;

        private String userName;
        private String userRole;

        /** 0-管理工作/1-基础工作/2-智励工作 */
        private Integer workType;

        /** 旧口径比例，保留展示以兼容历史数据 */
        private BigDecimal ratio;

        /** 历史全局员工池分配比例（allocation_v2 仅兼容展示） */
        private BigDecimal allocRatio;

        /** 完成比例（%） */
        private BigDecimal completionRatio;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long workPoolId;

        /** allocation_v2：对应工作类型资金池内比例 */
        private BigDecimal roleAllocRatio;

        /** 兑现前计划金额 */
        private BigDecimal plannedAmount;

        /** 兑现不足或离职转公司金额 */
        private BigDecimal companyDelta;

        private BigDecimal adjustmentTargetAmount;
        private BigDecimal previousAdjustedAmount;
        private BigDecimal remainingAdjustmentAmount;

        /** 0-员工正常/1-员工降档/2-领导兜底/3-公司留存/4-其他金额/5-效益补差扣回 */
        private Integer distType;

        /** 下单时成员是否在职：0-离职/1-在职 */
        private Integer isActive;

        /** 实得金额 */
        private BigDecimal amount;
    }
}
