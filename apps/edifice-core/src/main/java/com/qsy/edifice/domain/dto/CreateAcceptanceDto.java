package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建验收单并提交审批
 *
 * 三种类型的差异：
 * - 0-过程验收：project_stage_id 可空（随时发起）
 * - 1-成果验收：project_stage_id 可空；通常项目结项时发起
 * - 2-阶段性验收：project_stage_id 必填
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "创建验收单并提交审批")
public class CreateAcceptanceDto {

    @Schema(description = "项目id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "项目阶段id（阶段性验收必填）")
    private Long projectStageId;

    @Schema(description = "类型：0-过程/1-成果/2-阶段性", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer acceptanceType;

    @Schema(description = "验收标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "验收说明 / 内容")
    private String content;

    @Schema(description = "附件 fileId 列表（json 数组字符串）")
    private String fileIds;

    /** 一级审批人；缺省时按项目负责人自动选取 */
    @Schema(description = "一级审批人id")
    private Long firstApproverId;
}
