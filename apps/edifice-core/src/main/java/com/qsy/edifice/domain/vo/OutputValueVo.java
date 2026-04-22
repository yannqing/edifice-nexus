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

    private Integer totalAmount;

    /** 0-待确认/1-待审核/2-已审批/3-已发放 */
    private Integer status;

    private String submitUserName;

    private LocalDateTime submitTime;
    private LocalDateTime approvedTime;
    private LocalDateTime paidTime;
    private LocalDateTime createdTime;

    private List<DistributionItemVo> distributions;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DistributionItemVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long distributionId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long userId;

        private String userName;
        private String userRole;

        /** 0-管理工作/1-基础工作/2-智励工作 */
        private Integer workType;
        private BigDecimal ratio;
        private Integer amount;
    }
}
