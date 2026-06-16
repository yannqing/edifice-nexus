package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageCenterItemVo {
    private String messageKey;
    private String category;
    private String categoryLabel;
    private String title;
    private String content;
    private String link;
    private Integer priority;
    private Boolean read;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceId;

    private String sourceType;
    private LocalDateTime createdTime;
}
