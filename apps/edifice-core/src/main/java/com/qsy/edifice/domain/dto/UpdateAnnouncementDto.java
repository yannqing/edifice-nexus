package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "更新公告请求参数")
public class UpdateAnnouncementDto {

    @Schema(description = "公告id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long announcementId;

    @Schema(description = "公告标题")
    private String title;

    @Schema(description = "公告内容")
    private String content;

    @Schema(description = "优先级：0-普通/1-重要/2-紧急")
    private Integer priority;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;
}
