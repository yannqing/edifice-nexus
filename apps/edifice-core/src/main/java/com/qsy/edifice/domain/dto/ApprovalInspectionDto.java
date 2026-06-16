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

    /**
     * 可选：通过时指定下一级审批人。
     * - 若不传：视为终审，通过后验工单状态 → 已通过（3）
     * - 若传入：流转到下一级继续审批，验工单状态保持 审核中（1）
     * - 驳回时忽略此字段
     */
    @Schema(description = "通过时指定下一级审批人id（省略则终审）")
    private Long nextApproverId;

    @Schema(description = "是否终审通过，不再流转下一级")
    private Boolean terminate;
}
