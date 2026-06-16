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
@TableName("approval_cc")
public class ApprovalCc {

    @TableId(value = "cc_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ccId;

    @TableField("record_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @TableField("biz_type_ext")
    private String bizTypeExt;

    @TableField("biz_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;

    @TableField("cc_user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ccUserId;

    @TableField("from_user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromUserId;

    @TableField("comment")
    private String comment;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
