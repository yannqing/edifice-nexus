package com.qsy.edifice.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class SaveApprovalFlowConfigDto {
    private Long flowConfigId;
    private String bizType;
    private String flowName;
    private Integer enabled;
    private Integer allowWithdraw;
    private Integer allowUrge;
    private Integer allowCc;
    private Integer allowStarterSelectNext;
    private Integer allowSelfApproval;
    private Integer version;
    private Integer status;
    private String remark;
    private List<SaveApprovalFlowNodeDto> nodes;
}
