package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetMyProjectListDto {

    /**
     * 项目名称或编号
     */
    @Schema(description = "项目名称或编号")
    private String keywords;

    /**
     * 项目状态：0-未开始/1-进行中/2-待验收/3-验收中/4-已结束
     */
    @Schema(description = "项目状态")
    private Integer projectStatus;

    /**
     * 当前页
     */
    private Integer current = 1;

    /**
     * 一页大小
     */
    private Integer pageSize = 10;
}
