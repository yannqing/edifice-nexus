package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "工时查询参数")
public class GetTimesheetListDto {

    @Schema(description = "开始日期，格式 yyyy-MM-dd")
    private String startDate;

    @Schema(description = "结束日期，格式 yyyy-MM-dd")
    private String endDate;

    @Schema(description = "项目id")
    private Long projectId;

    private Integer current = 1;

    private Integer pageSize = 50;
}
