package com.qsy.edifice.domain.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateOaApplicationDto {

    private Long applicationId;

    private String applicationType;

    private String title;

    private Integer priority;

    private Map<String, Object> formData;

    private List<Long> attachmentIds;
}
