package com.qsy.edifice.domain.dto;

import lombok.Data;

@Data
public class SaveApprovalFlowNodeDto {
    private Long flowNodeId;
    private Integer nodeOrder;
    private String nodeName;
    private String approverSourceType;
    private String approverSourceId;
    private Integer allowTerminate;
    private Integer requiredNode;
}
