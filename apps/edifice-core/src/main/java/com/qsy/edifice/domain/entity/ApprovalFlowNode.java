package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("approval_flow_node")
public class ApprovalFlowNode {

    @TableId(value = "flow_node_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowNodeId;

    @TableField("flow_config_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowConfigId;

    @TableField("node_order")
    private Integer nodeOrder;

    @TableField("node_name")
    private String nodeName;

    @TableField("approver_source_type")
    private String approverSourceType;

    @TableField("approver_source_id")
    private String approverSourceId;

    @TableField("allow_terminate")
    private Integer allowTerminate;

    @TableField("required_node")
    private Integer requiredNode;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
