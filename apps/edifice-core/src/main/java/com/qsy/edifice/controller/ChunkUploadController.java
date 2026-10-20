package com.qsy.edifice.controller;

import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.FilesVo;
import com.qsy.edifice.service.ChunkUploadService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * 分片上传接口
 */
@Tag(name = "分片上传")
@RestController
@RequestMapping("/file/chunk")
@PreAuthorize("isAuthenticated()")
public class ChunkUploadController {

    @Resource
    private ChunkUploadService chunkUploadService;

    @Resource
    private JwtUtils jwtUtils;

    @PostMapping("/init")
    @Operation(summary = "初始化上传会话")
    public BaseResponse<Boolean> initUpload(
            @RequestParam String uploadId,
            @RequestParam String fileName,
            @RequestParam int totalChunks,
            @RequestParam long totalSize,
            @RequestParam(defaultValue = "document") String fileType) {
        throw new com.qsy.edifice.exception.BusinessException("上传接口已升级，请刷新页面后重试");
    }

    @PostMapping("/upload")
    @Operation(summary = "上传单个分片")
    public BaseResponse<Boolean> uploadChunk(
            @RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk) {
        throw new com.qsy.edifice.exception.BusinessException("上传接口已升级，请刷新页面后重试");
    }

    @PostMapping("/merge")
    @Operation(summary = "合并分片")
    public BaseResponse<FilesVo> mergeChunks(
            @RequestParam String uploadId,
            @RequestParam String fileName,
            @RequestParam(defaultValue = "document") String fileType,
            HttpServletRequest request) throws JsonProcessingException {
        throw new com.qsy.edifice.exception.BusinessException("上传接口已升级，请刷新页面后重试");
    }
}
