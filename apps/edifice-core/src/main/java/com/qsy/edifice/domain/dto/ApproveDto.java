package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批操作参数（通用审批链）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "审批操作请求")
public class ApproveDto {

    @Schema(description = "审批记录id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long recordId;

    /** true = 通过，false = 驳回 */
    @Schema(description = "是否通过", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean pass;

    /**
     * 指定下一级审批人（仅 pass=true 且还有后续层级时需要）；
     * 若流程配置为固定用户/角色/岗位，可不传此字段，由后端按配置解析。
     */
    @Schema(description = "下一级审批人id")
    private Long nextApproverId;

    @Schema(description = "是否终审通过，不再流转下一级")
    private Boolean terminate;

    @Schema(description = "审批意见 / 驳回原因")
    private String comment;
}
