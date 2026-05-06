package com.qsy.edifice.domain.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateOaApplicationDto {

    private String applicationType;

    private String title;

    /** 0-草稿，1-提交审批 */
    private Integer status;

    /** 0-普通，1-重要，2-紧急 */
    private Integer priority;

    private Map<String, Object> formData;

    private List<Long> attachmentIds;
}
