package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "查询公告列表请求参数")
public class GetAnnouncementListDto {

    @Schema(description = "标题关键字（模糊匹配）")
    private String keywords;

    /** 0-草稿/1-已发布/2-已下线；null=全部 */
    @Schema(description = "状态：0-草稿/1-已发布/2-已下线")
    private Integer status;

    @Schema(description = "优先级：0-普通/1-重要/2-紧急")
    private Integer priority;

    @Schema(description = "当前页，默认 1")
    private Integer current;

    @Schema(description = "每页数量，默认 10")
    private Integer pageSize;
}
