package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.domain.entity.UploadChunk;
import com.qsy.edifice.domain.vo.FilesVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.mapper.UploadChunkMapper;
import com.qsy.edifice.service.ChunkUploadService;
import com.qsy.edifice.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 分片上传服务实现
 */
@Slf4j
@Service
public class ChunkUploadServiceImpl implements ChunkUploadService {

    @Resource
    private UploadChunkMapper uploadChunkMapper;

    @Resource
    private FilesMapper filesMapper;

    @Resource
    private com.qsy.edifice.utils.FileUtils fileUtils;

    @Value("${file.upload-common-url:.}")
    private String uploadCommonPath;

    @Value("${file.upload-prefix-url:/upload}")
    private String uploadPrefixPath;

    @Value("${file.upload-document-url:documents}")
    private String uploadDocumentPath;

    @Value("${file.upload-image-url:images}")
    private String uploadImagePath;

    @Value("${file.upload-audio-url:audios}")
    private String uploadAudioPath;

    @Override
    public void initUpload(String uploadId, String fileName, int totalChunks, long totalSize, String fileType) {
        // 检查 uploadId 是否已存在
        long count = uploadChunkMapper.selectCount(
                new LambdaQueryWrapper<UploadChunk>().eq(UploadChunk::getUploadId, uploadId));
        if (count > 0) {
            log.info("上传会话已存在，跳过初始化: uploadId={}", uploadId);
            return;
        }

        // 批量插入分片记录
        for (int i = 0; i < totalChunks; i++) {
            UploadChunk chunk = UploadChunk.builder()
                    .uploadId(uploadId)
                    .chunkIndex(i)
                    .chunkSize(0L) // 上传时更新
                    .fileName(fileName)
                    .totalChunks(totalChunks)
                    .totalSize(totalSize)
                    .fileType(fileType)
                    .status(0)
                    .build();
            uploadChunkMapper.insert(chunk);
        }
        log.info("分片上传初始化完成: uploadId={}, fileName={}, totalChunks={}, totalSize={}",
                uploadId, fileName, totalChunks, totalSize);
    }

    @Override
    public void uploadChunk(String uploadId, int chunkIndex, MultipartFile chunk) {
        // 校验分片记录存在
        UploadChunk record = uploadChunkMapper.selectOne(
                new LambdaQueryWrapper<UploadChunk>()
                        .eq(UploadChunk::getUploadId, uploadId)
                        .eq(UploadChunk::getChunkIndex, chunkIndex));
        if (record == null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "分片记录不存在");
        }

        // 写入临时目录: ./upload/chunks/{uploadId}/{chunkIndex}
        String chunkDir = uploadCommonPath + "/upload/chunks/" + uploadId;
        try {
            java.nio.file.Files.createDirectories(Paths.get(chunkDir));
        } catch (IOException e) {
            throw new BusinessException("创建分片目录失败");
        }

        Path chunkPath = Paths.get(chunkDir, String.valueOf(chunkIndex));
        try (InputStream is = chunk.getInputStream()) {
            java.nio.file.Files.copy(is, chunkPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("分片写入失败: " + e.getMessage());
        }

        // 更新分片记录
        record.setChunkSize(chunk.getSize());
        record.setStatus(1);
        uploadChunkMapper.updateById(record);

        log.debug("分片上传成功: uploadId={}, chunkIndex={}, size={}", uploadId, chunkIndex, chunk.getSize());
    }

    @Override
    public FilesVo mergeChunks(String uploadId, String fileName, String fileType, Long userId) {
        // 查询所有分片记录
        List<UploadChunk> chunks = uploadChunkMapper.selectList(
                new LambdaQueryWrapper<UploadChunk>()
                        .eq(UploadChunk::getUploadId, uploadId)
                        .orderByAsc(UploadChunk::getChunkIndex));

        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "分片记录不存在");
        }

        int totalChunks = chunks.get(0).getTotalChunks();
        // 校验所有分片都已上传
        long uploadedCount = chunks.stream().filter(c -> c.getStatus() == 1).count();
        if (uploadedCount < totalChunks) {
            throw new BusinessException(ErrorType.ARGS_INVALID,
                    "分片未全部上传，已完成 " + uploadedCount + "/" + totalChunks);
        }

        // 生成最终文件路径
        String subPath = resolveSubPath(fileType);
        LocalDate now = LocalDate.now();
        String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fullDir = uploadCommonPath + File.separator + subPath + File.separator + datePath;
        try {
            java.nio.file.Files.createDirectories(Paths.get(fullDir));
        } catch (IOException e) {
            throw new BusinessException("创建目标目录失败");
        }

        String extension = getExtension(fileName);
        String newFileName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path targetPath = Paths.get(fullDir, newFileName);

        // 合并分片到目标文件（流式，不占内存）
        String chunkDir = uploadCommonPath + "/upload/chunks/" + uploadId;
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(targetPath.toFile()))) {
            for (UploadChunk chunk : chunks) {
                Path chunkPath = Paths.get(chunkDir, String.valueOf(chunk.getChunkIndex()));
                try (InputStream is = new BufferedInputStream(new FileInputStream(chunkPath.toFile()))) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        os.write(buf, 0, len);
                    }
                }
            }
        } catch (IOException e) {
            throw new BusinessException("合并分片失败: " + e.getMessage());
        }

        // 计算文件摘要
        long totalSize = chunks.get(0).getTotalSize();
        String fileMd5;
        String fileHash;
        try (InputStream is = new BufferedInputStream(new FileInputStream(targetPath.toFile()))) {
            // 计算 MD5 需要重新读取，先缓存到内存外的临时方式
            fileMd5 = DigestUtils.md5Hex(new BufferedInputStream(new FileInputStream(targetPath.toFile())));
            fileHash = DigestUtils.sha256Hex(is);
        } catch (IOException e) {
            fileMd5 = "";
            fileHash = "";
        }

        // 拼接访问路径
        String accessUrl = uploadPrefixPath + "/" + subPath + "/" + datePath + "/" + newFileName;
        String appUrl = ""; // 与 FileServiceImpl 保持一致，由前端拼接域名

        // 写入 files 表
        Files file = new Files();
        file.setUploadUserId(userId);
        file.setFileType(fileType);
        file.setFileName(newFileName);
        file.setOriginalName(fileName);
        file.setDisplayName(fileName);
        file.setFileExtension(extension);
        file.setFilePath(accessUrl);
        file.setFileUrl(appUrl + accessUrl);
        file.setFileSize(totalSize);
        file.setFileMd5(fileMd5);
        file.setFileHash(fileHash);
        file.setMimeType(resolveMimeType(extension));
        // 图片类型生成缩略图
        if ("image".equals(fileType)) {
            String thumbUrl = fileUtils.generateThumbnail(targetPath.toFile(), subPath, newFileName);
            file.setThumbnailUrl(thumbUrl);
        }
        file.setDownloadCount(0);
        file.setPreviewCount(0);
        file.setAccessCount(0);
        file.setShareCount(0);
        file.setStatus(1);
        filesMapper.insert(file);

        // 清理分片记录和临时文件
        uploadChunkMapper.delete(
                new LambdaQueryWrapper<UploadChunk>().eq(UploadChunk::getUploadId, uploadId));
        try {
            deleteDirectory(new File(chunkDir));
        } catch (Exception e) {
            log.warn("清理分片临时目录失败: {}", chunkDir, e);
        }

        log.info("分片合并完成: uploadId={}, fileId={}, fileName={}, size={}", uploadId, file.getFileId(), fileName, totalSize);

        // 构建返回 VO
        FilesVo vo = new FilesVo();
        vo.setFileId(file.getFileId());
        vo.setFileType(file.getFileType());
        vo.setDisplayName(file.getDisplayName());
        vo.setFileExtension(file.getFileExtension());
        vo.setFileUrl(file.getFileUrl());
        vo.setFilePath(file.getFilePath());
        vo.setThumbnailUrl(file.getThumbnailUrl());
        vo.setFileSize(file.getFileSize());
        vo.setStatus(file.getStatus());
        return vo;
    }

    private String resolveSubPath(String fileType) {
        if ("image".equals(fileType)) return uploadImagePath;
        if ("audio".equals(fileType)) return uploadAudioPath;
        return uploadDocumentPath;
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return (dot > 0 && dot < fileName.length() - 1) ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private String resolveMimeType(String ext) {
        return switch (ext.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "zip" -> "application/zip";
            case "rar" -> "application/x-rar-compressed";
            case "7z" -> "application/x-7z-compressed";
            default -> "application/octet-stream";
        };
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) deleteDirectory(f);
                    else f.delete();
                }
            }
        }
        dir.delete();
    }
}
