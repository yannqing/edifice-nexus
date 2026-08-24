package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OutputValuePreviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private BigDecimal baseAmount;
    private BigDecimal benefitAmount;
    private BigDecimal baseRatio;
    private BigDecimal benefitRatio;
    private BigDecimal basePart;
    private BigDecimal benefitPart;
    private BigDecimal currentStageAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal thisPeriodTotal;
    /** 当前阶段已确认的产值分配总额 */
    private BigDecimal alreadyAllocated;
    /** 本次增量完成比例（%） */
    private BigDecimal incrementalRatio;
    /** 系数 */
    private BigDecimal coefficient;
    /** 人员分配计算版本 */
    private String allocationVersion;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long allocationRuleVersionId;
    private Integer allocationRuleVersionNo;
    private BigDecimal employeePoolRate;
    private BigDecimal companyBaseRate;
    private BigDecimal employeePoolAmount;
    private BigDecimal companyBaseAmount;
    private BigDecimal workTransferAmount;
    private BigDecimal projectPoolAmount;
    private List<WorkPoolVo> workPools;
    private List<AdjustmentDetailVo> adjustmentDetails;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WorkPoolVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long workPoolId;
        private Integer workType;
        private String workTypeName;
        private BigDecimal workWeight;
        private BigDecimal grossRate;
        private BigDecimal grossAmount;
        private BigDecimal projectRate;
        private BigDecimal projectAmount;
        private BigDecimal companyRate;
        private BigDecimal companyAmount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AdjustmentDetailVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long adjustmentDetailId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long sourceOutputValueId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long sourceProjectStageId;

        private String sourceStageName;
        private BigDecimal sourceBaseRatio;
        private BigDecimal sourceBenefitRatio;
        private BigDecimal oldBaseAmountSnapshot;
        private BigDecimal oldBenefitAmountSnapshot;
        private BigDecimal oldStageAmount;
        private BigDecimal newBaseAmountSnapshot;
        private BigDecimal newBenefitAmountSnapshot;
        private BigDecimal newStageAmount;
        private BigDecimal alreadyAdjustedAmount;
        private BigDecimal adjustmentAmount;
    }
}
