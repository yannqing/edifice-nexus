package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ApprovalFlowConfigVo {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowConfigId;
    private String bizType;
    private String bizTypeLabel;
    private String flowName;
    private Integer enabled;
    private Integer allowWithdraw;
    private Integer allowUrge;
    private Integer allowCc;
    private Integer allowStarterSelectNext;
    private Integer version;
    private Integer status;
    private String remark;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private List<ApprovalFlowNodeVo> nodes;
}
