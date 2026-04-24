package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交审批参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "提交审批请求")
public class SubmitApprovalDto {

    /** 业务类型扩展字符串：file / inspection / bid / acceptance / output / timesheet */
    @Schema(description = "业务类型 ext", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bizType;

    /** 业务主表 id */
    @Schema(description = "业务id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long bizId;

    /** 第一审批人 */
    @Schema(description = "第一级审批人id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long firstApproverId;

    /** 可选的提交说明 */
    @Schema(description = "提交说明")
    private String description;
}
