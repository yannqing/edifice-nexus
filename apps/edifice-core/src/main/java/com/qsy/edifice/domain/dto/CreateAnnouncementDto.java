package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "创建公告请求参数")
public class CreateAnnouncementDto {

    @Schema(description = "公告标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "公告内容（纯文本或简单 HTML）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    /** 0-普通/1-重要/2-紧急，默认 0 */
    @Schema(description = "优先级：0-普通/1-重要/2-紧急")
    private Integer priority;

    /** 0-草稿/1-已发布，默认 0，若传 1 则立即发布 */
    @Schema(description = "状态：0-草稿/1-立即发布，默认 0")
    private Integer status;

    @Schema(description = "过期时间（可选）")
    private LocalDateTime expireTime;
}
