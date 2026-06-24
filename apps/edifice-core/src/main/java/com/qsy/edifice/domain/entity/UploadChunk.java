package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分片上传记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("upload_chunks")
public class UploadChunk implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 上传会话ID（前端生成UUID） */
    @TableField("upload_id")
    private String uploadId;

    /** 分片序号（0-based） */
    @TableField("chunk_index")
    private Integer chunkIndex;

    /** 分片大小（字节） */
    @TableField("chunk_size")
    private Long chunkSize;

    /** 原始文件名 */
    @TableField("file_name")
    private String fileName;

    /** 总分片数 */
    @TableField("total_chunks")
    private Integer totalChunks;

    /** 文件总大小（字节） */
    @TableField("total_size")
    private Long totalSize;

    /** 文件类型：document/image/audio */
    @TableField("file_type")
    private String fileType;

    /** 状态：0-上传中/1-已完成 */
    @TableField("status")
    private Integer status;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
