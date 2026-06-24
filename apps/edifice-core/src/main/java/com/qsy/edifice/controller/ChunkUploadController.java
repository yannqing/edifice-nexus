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
        chunkUploadService.initUpload(uploadId, fileName, totalChunks, totalSize, fileType);
        return ResultUtils.success(Code.SUCCESS, true, "初始化成功");
    }

    @PostMapping("/upload")
    @Operation(summary = "上传单个分片")
    public BaseResponse<Boolean> uploadChunk(
            @RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk) {
        chunkUploadService.uploadChunk(uploadId, chunkIndex, chunk);
        return ResultUtils.success(Code.SUCCESS, true, "分片上传成功");
    }

    @PostMapping("/merge")
    @Operation(summary = "合并分片")
    public BaseResponse<FilesVo> mergeChunks(
            @RequestParam String uploadId,
            @RequestParam String fileName,
            @RequestParam(defaultValue = "document") String fileType,
            HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser user = jwtUtils.getUserFromToken(token);
        FilesVo result = chunkUploadService.mergeChunks(uploadId, fileName, fileType, user.getUserId());
        return ResultUtils.success(Code.SUCCESS, result, "合并成功");
    }
}
