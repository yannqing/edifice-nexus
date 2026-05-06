package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("oa_user_sync_outbox")
public class OaUserSyncOutbox {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("event_type")
    private String eventType;

    @TableField("user_id")
    private Long userId;

    @TableField("payload")
    private String payload;

    @TableField("status")
    private Integer status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("last_error")
    private String lastError;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
