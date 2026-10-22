package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetAllProjectListDto;
import com.qsy.edifice.domain.vo.ProjectListVo;
import com.qsy.edifice.domain.vo.ProjectTreeNodeVo;
import com.qsy.edifice.domain.vo.ProjectTreeStatisticsVo;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/project")
public class ProjectTreeController {
    @Resource private ProjectService projectService;
    @Resource private JwtUtils jwtUtils;

    @GetMapping("/mine/tree")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<Page<ProjectTreeNodeVo>> mine(GetAllProjectListDto dto, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, projectService.getProjectTree(dto, userId(request), false));
    }

    @GetMapping("/all/tree")
    @PreAuthorize("hasAuthority('menu:all-projects') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Page<ProjectTreeNodeVo>> all(GetAllProjectListDto dto, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, projectService.getProjectTree(dto, userId(request), true));
    }

    @GetMapping("/mine/tree/{id}/children")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<Page<ProjectListVo>> mineChildren(@PathVariable Long id, GetAllProjectListDto dto, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, projectService.getProjectTreeChildren(id, dto, userId(request), false));
    }

    @GetMapping("/all/tree/{id}/children")
    @PreAuthorize("hasAuthority('menu:all-projects') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Page<ProjectListVo>> allChildren(@PathVariable Long id, GetAllProjectListDto dto, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, projectService.getProjectTreeChildren(id, dto, userId(request), true));
    }

    @GetMapping("/mine/tree/statistics")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<ProjectTreeStatisticsVo> mineStatistics(GetAllProjectListDto dto, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, projectService.getProjectTreeStatistics(dto, userId(request), false));
    }

    @GetMapping("/all/tree/statistics")
    @PreAuthorize("hasAuthority('menu:all-projects') or hasRole('SUPER_ADMIN')")
    public BaseResponse<ProjectTreeStatisticsVo> allStatistics(GetAllProjectListDto dto, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, projectService.getProjectTreeStatistics(dto, userId(request), true));
    }

    private Long userId(HttpServletRequest request) throws JsonProcessingException {
        return jwtUtils.getUserFromToken(request.getHeader("token")).getUserId();
    }
}
