package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.service.FileService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Keeps bookmarked legacy file paths working after their storage location changes. */
@RestController
public class LegacyFileController {
    @Resource private FilesMapper files;
    @Resource private FileService service;
    @GetMapping("/upload/**")
    public ResponseEntity<StreamingResponseBody> legacy(HttpServletRequest request) {
        Files file = files.selectOne(new LambdaQueryWrapper<Files>()
                .eq(Files::getFilePath, request.getRequestURI()).last("LIMIT 1"));
        return file == null ? ResponseEntity.notFound().build() : service.downloadFile(file.getFileId(), request);
    }
}
