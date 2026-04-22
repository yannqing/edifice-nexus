package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "公告VO")
public class AnnouncementVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long announcementId;

    private String title;
    private String content;

    /** 0-普通/1-重要/2-紧急 */
    private Integer priority;

    /** 0-草稿/1-已发布/2-已下线 */
    private Integer status;

    private LocalDateTime publishTime;
    private LocalDateTime expireTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishUserId;

    private String publishUserName;

    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
