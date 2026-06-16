package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目归档列表查询 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetProjectArchiveListDto {

    @Schema(description = "项目名称或编号")
    private String keywords;

    @Schema(description = "项目类型id")
    private Long projectType;

    private Integer current = 1;

    private Integer pageSize = 10;
}
