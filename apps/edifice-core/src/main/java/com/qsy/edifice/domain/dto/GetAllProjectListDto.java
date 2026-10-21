package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询全部项目列表 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetAllProjectListDto {

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
     * 项目类型id
     */
    @Schema(description = "项目类型id")
    private Long projectType;

    @Schema(description = "主项目ID，仅查询其结算子项目")
    private Long parentProjectId;

    @Schema(description = "项目层级：0-主项目及独立项目，1-结算子项目；空为全部")
    private Integer projectLevel;

    private Integer archiveStatus;

    /**
     * 当前页
     */
    private Integer current = 1;

    /**
     * 一页大小
     */
    private Integer pageSize = 10;
}
