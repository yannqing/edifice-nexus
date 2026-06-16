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
@TableName("approval_flow_config")
public class ApprovalFlowConfig {

    @TableId(value = "flow_config_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowConfigId;

    @TableField("biz_type")
    private String bizType;

    @TableField("flow_name")
    private String flowName;

    @TableField("enabled")
    private Integer enabled;

    @TableField("allow_withdraw")
    private Integer allowWithdraw;

    @TableField("allow_urge")
    private Integer allowUrge;

    @TableField("allow_cc")
    private Integer allowCc;

    @TableField("allow_starter_select_next")
    private Integer allowStarterSelectNext;

    @TableField("version")
    private Integer version;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;

    @TableField("created_by")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @TableField("updated_by")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
