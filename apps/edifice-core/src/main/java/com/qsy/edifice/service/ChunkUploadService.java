package com.qsy.edifice.service;

import com.qsy.edifice.domain.vo.FilesVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传服务
 */
public interface ChunkUploadService {

    /**
     * 初始化上传会话
     */
    void initUpload(String uploadId, String fileName, int totalChunks, long totalSize, String fileType);

    /**
     * 上传单个分片
     */
    void uploadChunk(String uploadId, int chunkIndex, MultipartFile chunk);

    /**
     * 合并所有分片，返回最终文件信息
     */
    FilesVo mergeChunks(String uploadId, String fileName, String fileType, Long userId);
}
