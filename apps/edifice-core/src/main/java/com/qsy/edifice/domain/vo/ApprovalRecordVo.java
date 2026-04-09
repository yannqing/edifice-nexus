package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批记录 VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "审批记录VO")
public class ApprovalRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "审批记录id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalRecordId;

    @Schema(description = "审批人id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approver;

    @Schema(description = "审批人姓名")
    private String approverName;

    @Schema(description = "审批说明")
    private String approvalDescription;

    @Schema(description = "审批状态：0-待审核/1-已通过/2-已拒绝")
    private Integer inspectionFormStatus;

    @Schema(description = "审批时间")
    private LocalDateTime createdTime;
}
