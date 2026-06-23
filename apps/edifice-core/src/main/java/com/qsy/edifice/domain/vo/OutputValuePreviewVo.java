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
    private List<AdjustmentDetailVo> adjustmentDetails;

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
