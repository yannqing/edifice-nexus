package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同管理列表查询 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetContractListDto {

    @Schema(description = "合同名称、合同编号、项目名称或项目编号")
    private String keywords;

    @Schema(description = "合同类型：0-基本收费/1-基本+效益")
    private Integer contractType;

    @Schema(description = "关联项目id")
    private Long projectId;

    private Integer current = 1;

    private Integer pageSize = 10;
}
