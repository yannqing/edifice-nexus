package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批验工单 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "审批验工单请求参数")
public class ApprovalInspectionDto {

    @Schema(description = "验工单id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long inspectionFormId;

    @Schema(description = "审批结果：1-通过/2-驳回", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer result;

    @Schema(description = "审批意见")
    private String approvalDescription;
}
