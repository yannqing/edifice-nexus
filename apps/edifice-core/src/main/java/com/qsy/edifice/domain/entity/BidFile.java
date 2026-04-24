package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投标附件（招标文件 / 投标文件 / 中标通知 / 其他）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid_file")
public class BidFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "bid_file_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bidFileId;

    @TableField("bid_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bidId;

    @TableField("file_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    @TableField("file_category")
    private String fileCategory;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
