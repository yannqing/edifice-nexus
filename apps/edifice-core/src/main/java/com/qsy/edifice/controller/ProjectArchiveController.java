package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.ArchiveProjectDto;
import com.qsy.edifice.domain.dto.GetProjectArchiveListDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ProjectArchiveVo;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "项目归档")
@RestController
@RequestMapping("/project-archive")
@PreAuthorize("hasAuthority('menu:project-archive') or hasRole('SUPER_ADMIN')")
public class ProjectArchiveController {

    @Resource
    private ProjectService projectService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/ready")
    @Operation(summary = "可归档项目列表", description = "查询未归档项目，并返回归档条件检查结果")
    public BaseResponse<Page<ProjectArchiveVo>> ready(GetProjectArchiveListDto dto) {
        return ResultUtils.success(Code.SUCCESS, projectService.getArchivableProjectPage(dto));
    }

    @GetMapping("/archived")
    @Operation(summary = "已归档项目列表", description = "查询已归档项目")
    public BaseResponse<Page<ProjectArchiveVo>> archived(GetProjectArchiveListDto dto) {
        return ResultUtils.success(Code.SUCCESS, projectService.getArchivedProjectPage(dto));
    }

    @PutMapping("/archive/{id}")
    @Operation(summary = "归档项目", description = "所有阶段完成后，将项目状态置为已结束/已归档")
    public BaseResponse<Boolean> archive(@PathVariable("id") Long projectId,
                                         @RequestBody(required = false) ArchiveProjectDto dto,
                                         HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        projectService.archiveProject(projectId, loginUser.getUserId(), dto == null ? null : dto.getArchiveRemark());
        return ResultUtils.success(Code.SUCCESS, true, "归档成功");
    }

    @PutMapping("/unarchive/{id}")
    @Operation(summary = "取消归档", description = "将已归档项目恢复为进行中")
    public BaseResponse<Boolean> unarchive(@PathVariable("id") Long projectId) {
        projectService.unarchiveProject(projectId);
        return ResultUtils.success(Code.SUCCESS, true, "已取消归档");
    }
}
