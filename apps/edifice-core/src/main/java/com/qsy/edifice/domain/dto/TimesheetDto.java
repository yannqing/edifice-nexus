package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "工时填报请求参数")
public class TimesheetDto {

    @Schema(description = "工时记录id（更新时必填）")
    private Long timesheetId;

    @Schema(description = "项目id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "项目阶段id")
    private Long projectStageId;

    /** 0-管理工作/1-基础工作/2-智励工作 */
    @Schema(description = "工作类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer workType;

    @Schema(description = "工作日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate workDate;

    @Schema(description = "工作时长（小时）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal hours;

    @Schema(description = "工作内容描述")
    private String description;

    /** 0-草稿/1-已提交 */
    @Schema(description = "状态：0-草稿/1-已提交")
    private Integer status;
}
