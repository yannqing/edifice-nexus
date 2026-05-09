package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateProjectFileDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ProjectFileVo;
import com.qsy.edifice.service.ProjectFilesService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目文件三级审批（Phase 3 #2）
 *
 * 典型流程：
 * 1. 任意成员调 /file/upload/* 拿到 fileId
 * 2. POST /project-files/create 落档并提交审批（一级：项目负责人）
 * 3. 项目负责人 POST /project-files/approve （通过 + nextApproverId=专业主管）
 * 4. 专业主管 POST /project-files/approve （通过 + nextApproverId=总工）
 * 5. 总工 POST /project-files/approve （通过 / 驳回；不传 nextApproverId 视为终审）
 */
@Tag(name = "项目文件")
@RestController
@RequestMapping("/project-files")
public class ProjectFilesController {

    @Resource
    private ProjectFilesService projectFilesService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/create")
    @Operation(summary = "创建项目文件并提交审批",
            description = "先通过 /file/upload/* 拿到 fileId，再调用此接口归档并进入三级审批链")
    public BaseResponse<Long> create(@RequestBody CreateProjectFileDto dto,
                                     HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long id = projectFilesService.createAndSubmit(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, id, "提交成功");
    }

    @PostMapping("/approve")
    @Operation(summary = "审批（通过 / 驳回）",
            description = "通过 + nextApproverId 非空时流转下一级；不传 nextApproverId 视为终审")
    public BaseResponse<Boolean> approve(@RequestBody ApproveDto dto,
                                         HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        projectFilesService.approve(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "审批完成");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "撤销项目文件", description = "上传人可撤销审批中的项目文件")
    public BaseResponse<Boolean> cancel(@PathVariable("id") Long id,
                                        HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        projectFilesService.cancel(id, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "撤销成功");
    }

    @GetMapping("/list")
    @Operation(summary = "项目文件列表", description = "支持按 projectId / approvalStatus / keyword 过滤")
    public BaseResponse<List<ProjectFileVo>> list(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "approvalStatus", required = false) Integer approvalStatus,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResultUtils.success(Code.SUCCESS,
                projectFilesService.listProjectFiles(projectId, approvalStatus, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "项目文件详情（含审批链）")
    public BaseResponse<ProjectFileVo> detail(@PathVariable("id") Long id) {
        return ResultUtils.success(Code.SUCCESS, projectFilesService.getDetail(id));
    }

    @GetMapping("/my-pending")
    @Operation(summary = "我的待审批文件")
    public BaseResponse<List<ProjectFileVo>> myPending(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        return ResultUtils.success(Code.SUCCESS,
                projectFilesService.listMyPending(loginUser.getUserId()));
    }
}
