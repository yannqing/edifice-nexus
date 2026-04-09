package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交验工单 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "提交验工单请求参数")
public class ApplyInspectionDto {

    @Schema(description = "项目id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "项目阶段id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectStageId;

    @Schema(description = "验工说明")
    private String inspectionFormDescription;

    @Schema(description = "附件id（json数组）")
    private String fileIds;
}
