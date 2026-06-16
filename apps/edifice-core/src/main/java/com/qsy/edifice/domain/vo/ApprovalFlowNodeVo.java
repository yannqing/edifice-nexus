package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApprovalFlowNodeVo {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowNodeId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowConfigId;
    private Integer nodeOrder;
    private String nodeName;
    private String approverSourceType;
    private String approverSourceId;
    private Integer allowTerminate;
    private Integer requiredNode;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
