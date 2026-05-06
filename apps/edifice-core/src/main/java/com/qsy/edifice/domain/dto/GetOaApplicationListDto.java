package com.qsy.edifice.domain.dto;

import lombok.Data;

@Data
public class GetOaApplicationListDto {

    private String keywords;

    private String applicationType;

    /** 0-草稿，1-审批中，2-已通过，3-已驳回，4-已撤回 */
    private Integer status;

    /** true=只看我的申请；默认 true */
    private Boolean mine;

    private Integer current;

    private Integer pageSize;
}
