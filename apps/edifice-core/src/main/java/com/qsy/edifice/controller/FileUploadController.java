package com.qsy.edifice.controller;

import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.service.UnifiedFileUploadService;
import com.qsy.edifice.service.FileUploadCompletionService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/file/uploads")
@PreAuthorize("isAuthenticated()")
public class FileUploadController {
    @Resource private UnifiedFileUploadService uploads;
    @Resource private FileUploadCompletionService completions;
    @Resource private JwtUtils jwt;
    private Long user(HttpServletRequest request) throws IOException {
        return jwt.getUserFromToken(request.getHeader("token")).getUserId();
    }
    @PostMapping public BaseResponse<UnifiedFileUploadService.SessionVo> init(
            @RequestBody UnifiedFileUploadService.InitRequest body, HttpServletRequest request) throws IOException {
        return ResultUtils.success(200, uploads.init(body, user(request)), "上传会话已创建");
    }
    @GetMapping("/{id}") public BaseResponse<UnifiedFileUploadService.SessionVo> status(
            @PathVariable String id, HttpServletRequest request) throws IOException {
        return ResultUtils.success(200, completions.status(id, user(request)), "成功");
    }
    @PostMapping("/{id}/parts/{part}") public BaseResponse<UnifiedFileUploadService.SessionVo> part(
            @PathVariable String id, @PathVariable int part, @RequestParam("chunk") MultipartFile chunk,
            HttpServletRequest request) throws IOException {
        return ResultUtils.success(200, uploads.uploadPart(id, part, chunk, user(request)), "分片上传成功");
    }
    @PostMapping("/{id}/complete") public BaseResponse<UnifiedFileUploadService.SessionVo> complete(
            @PathVariable String id, HttpServletRequest request) throws IOException {
        return ResultUtils.success(200, uploads.complete(id, user(request)), "上传成功");
    }
    @PostMapping("/{id}/complete-async") public BaseResponse<UnifiedFileUploadService.SessionVo> completeAsync(
            @PathVariable String id, HttpServletRequest request) throws IOException {
        return ResultUtils.success(200, completions.start(id, user(request)), "文件正在校验");
    }
    @DeleteMapping("/{id}") public BaseResponse<Boolean> cancel(
            @PathVariable String id, HttpServletRequest request) throws IOException {
        completions.cancel(id, user(request)); return ResultUtils.success(200, true, "已取消上传");
    }
}
