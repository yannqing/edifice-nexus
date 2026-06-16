package com.qsy.edifice.domain.dto;

import lombok.Data;

@Data
public class GetApprovalFlowConfigListDto {
    private String bizType;
    private String keyword;
    private Integer enabled;
    private Integer current = 1;
    private Integer pageSize = 10;
}
