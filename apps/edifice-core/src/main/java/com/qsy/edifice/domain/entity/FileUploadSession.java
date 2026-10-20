package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("file_upload_session")
public class FileUploadSession {
    @TableId(type = IdType.INPUT)
    private String uploadId;
    private Long userId;
    private String fileName;
    private String purpose;
    private Long totalSize;
    private Integer chunkSize;
    private Integer totalParts;
    private String storageType;
    private String storageBucket;
    private String objectKey;
    private String multipartId;
    private String status;
    private Long fileId;
    private LocalDateTime createdTime;
    private LocalDateTime expiresTime;
    private LocalDateTime updatedTime;
}
