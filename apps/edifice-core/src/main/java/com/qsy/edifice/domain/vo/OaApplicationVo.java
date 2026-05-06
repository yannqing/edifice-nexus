package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class OaApplicationVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicationId;

    private String applicationNo;

    private String applicationType;

    private String applicationTypeLabel;

    private String title;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;

    private String applicantName;

    private Integer status;

    private Integer priority;

    private Map<String, Object> formData;

    private List<Long> attachmentIds;

    private LocalDateTime submittedTime;

    private LocalDateTime approvedTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
