package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.CreateProjectFileFolderDto;
import com.qsy.edifice.domain.dto.ResolveProjectFileFoldersDto;
import com.qsy.edifice.domain.entity.ProjectFileFolder;
import com.qsy.edifice.service.ProjectFileFolderService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/project-file-folders")
public class ProjectFileFoldersController {
    @Resource private ProjectFileFolderService folderService;
    @Resource private JwtUtils jwtUtils;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<List<ProjectFileFolder>> list(@RequestParam Long projectId, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, folderService.list(projectId, userId(request)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('menu:my-projects','menu:all-projects','menu:project-files-approval') or hasRole('SUPER_ADMIN')")
    public BaseResponse<ProjectFileFolder> create(@RequestBody CreateProjectFileFolderDto dto, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, folderService.create(dto.getProjectId(), dto.getParentFolderId(), dto.getFolderName(), userId(request)));
    }

    @PostMapping("/resolve-paths")
    @PreAuthorize("hasAnyAuthority('menu:my-projects','menu:all-projects','menu:project-files-approval') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Map<String, String>> resolve(@RequestBody ResolveProjectFileFoldersDto dto, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, folderService.resolvePaths(dto.getProjectId(), dto.getParentFolderId(), dto.getPaths(), userId(request)));
    }

    private Long userId(HttpServletRequest request) throws JsonProcessingException {
        return jwtUtils.getUserFromToken(request.getHeader("token")).getUserId();
    }
}
