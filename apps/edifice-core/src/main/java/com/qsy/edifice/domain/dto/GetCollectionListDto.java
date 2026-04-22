package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "查询回款列表请求参数")
public class GetCollectionListDto {

    @Schema(description = "项目名称或编号（模糊匹配）")
    private String keywords;

    /** 0-未回款/1-部分回款/2-已回款；null=全部 */
    @Schema(description = "回款状态：0-未回款/1-部分回款/2-已回款")
    private Integer collectionStatus;

    @Schema(description = "当前页，默认 1")
    private Integer current;

    @Schema(description = "每页数量，默认 10")
    private Integer pageSize;
}
