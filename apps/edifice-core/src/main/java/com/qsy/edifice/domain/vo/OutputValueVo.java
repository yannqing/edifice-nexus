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

    /** 公司留存金额（40%） */
    private BigDecimal companyReserve;

    /** 领导兜底（降档差额累计） */
    private BigDecimal leaderExtra;

    /** 其他金额（离职成员未发金额累计） */
    private BigDecimal otherAmount;

    /** 公司补贴（只记录不计入产值） */
    private BigDecimal subsidyAmount;

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

        /** 旧口径比例，保留展示以兼容历史数据 */
        private BigDecimal ratio;

        /** 分配比例（%，60% 池内） */
        private BigDecimal allocRatio;

        /** 完成比例（%） */
        private BigDecimal completionRatio;

        /** 0-员工正常/1-员工降档/2-领导兜底/3-公司留存/4-其他金额 */
        private Integer distType;

        /** 下单时成员是否在职：0-离职/1-在职 */
        private Integer isActive;

        /** 实得金额 */
        private BigDecimal amount;
    }
}
