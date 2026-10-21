package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateProjectFileDto;
import com.qsy.edifice.domain.dto.GetProjectFileListDto;
import com.qsy.edifice.domain.dto.SubmitProjectFileBatchDto;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目文件草稿及整批审批；历史单文件审批继续使用原来的记录。
 * 上传完成后先保存草稿，submit-drafts 将指定草稿冻结为一批并创建一条审批链。
 */
@Tag(name = "项目文件")
@RestController
@RequestMapping("/project-files")
public class ProjectFilesController {

    @Resource
    private ProjectFilesService projectFilesService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/draft-policy")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<java.util.Map<String, Integer>> draftPolicy() {
        return ResultUtils.success(Code.SUCCESS, java.util.Map.of("retentionDays", projectFilesService.getDraftRetentionDays(), "maxBatchFiles", 200));
    }

    @PostMapping("/drafts")
    @PreAuthorize("hasAuthority('menu:my-projects') or hasAuthority('menu:all-projects') or hasAuthority('menu:project-files-approval') or hasRole('SUPER_ADMIN')")
    public BaseResponse<List<String>> saveDrafts(@RequestBody List<CreateProjectFileDto> items, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, projectFilesService.saveDrafts(items, jwtUtils.getUserFromToken(request.getHeader("token")).getUserId()));
    }

    @GetMapping("/drafts")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<Page<ProjectFileVo>> drafts(@RequestParam Long projectId,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, projectFilesService.listDrafts(projectId, keyword, current, pageSize,
                jwtUtils.getUserFromToken(request.getHeader("token")).getUserId()));
    }

    @DeleteMapping("/drafts/{id}")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<Boolean> removeDraft(@PathVariable Long id, HttpServletRequest request) throws JsonProcessingException {
        projectFilesService.removeDraft(id, jwtUtils.getUserFromToken(request.getHeader("token")).getUserId());
        return ResultUtils.success(Code.SUCCESS, true);
    }

    @PutMapping("/drafts/{id}")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<Boolean> updateDraft(@PathVariable Long id, @RequestBody CreateProjectFileDto dto, HttpServletRequest request) throws JsonProcessingException {
        projectFilesService.updateDraft(id, dto, jwtUtils.getUserFromToken(request.getHeader("token")).getUserId());
        return ResultUtils.success(Code.SUCCESS, true);
    }

    @PostMapping("/submit-drafts")
    @PreAuthorize("hasAuthority('menu:my-projects') or hasAuthority('menu:all-projects') or hasAuthority('menu:project-files-approval') or hasRole('SUPER_ADMIN')")
    public BaseResponse<String> submitDrafts(@RequestBody SubmitProjectFileBatchDto dto, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, String.valueOf(projectFilesService.submitDrafts(dto,
                jwtUtils.getUserFromToken(request.getHeader("token")).getUserId())));
    }

    @PostMapping("/{id}/copy-to-drafts")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<List<String>> copyToDrafts(@PathVariable Long id, HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, projectFilesService.copyToDrafts(id,
                jwtUtils.getUserFromToken(request.getHeader("token")).getUserId()));
    }

    @PostMapping("/create")
    @Operation(summary = "创建项目文件并提交审批",
            description = "先通过 /file/upload/* 拿到 fileId，再调用此接口归档并进入三级审批链")
    @PreAuthorize("hasAuthority('menu:my-projects') or hasAuthority('menu:project-files-approval') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Long> create(@RequestBody CreateProjectFileDto dto,
                                     HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long id = projectFilesService.createAndSubmit(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, id, "提交成功");
    }

    @PostMapping("/create-batch")
    @PreAuthorize("hasAuthority('menu:my-projects') or hasAuthority('menu:all-projects') or hasAuthority('menu:project-files-approval') or hasRole('SUPER_ADMIN')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public BaseResponse<List<String>> createBatch(@RequestBody List<CreateProjectFileDto> items,
                                                  HttpServletRequest request) throws JsonProcessingException {
        if (items == null || items.isEmpty() || items.size() > 20)
            throw new com.qsy.edifice.exception.BusinessException("每批文件数量应为1到20个");
        Long userId = jwtUtils.getUserFromToken(request.getHeader("token")).getUserId();
        List<String> ids = projectFilesService.saveDrafts(items, userId);
        if (items.stream().map(CreateProjectFileDto::getFirstApproverId).distinct().count() != 1)
            throw new com.qsy.edifice.exception.BusinessException("一批申请只能指定一位首审人");
        SubmitProjectFileBatchDto batch = new SubmitProjectFileBatchDto();
        batch.setProjectId(items.get(0).getProjectId());
        batch.setDraftIds(ids.stream().map(Long::valueOf).toList());
        batch.setFirstApproverId(items.get(0).getFirstApproverId());
        Long id = projectFilesService.submitDrafts(batch, userId);
        return ResultUtils.success(Code.SUCCESS, List.of(String.valueOf(id)), "整批提交成功");
    }

    @PostMapping("/approve")
    @Operation(summary = "审批（通过 / 驳回）",
            description = "通过 + nextApproverId 非空时流转下一级；不传 nextApproverId 视为终审")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<Boolean> approve(@RequestBody ApproveDto dto,
                                         HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        projectFilesService.approve(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "审批完成");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "撤销项目文件", description = "上传人可撤销审批中的项目文件")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<Boolean> cancel(@PathVariable("id") Long id,
                                        HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        projectFilesService.cancel(id, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "撤销成功");
    }

    @GetMapping("/list")
    @Operation(summary = "项目文件列表", description = "支持按 projectId / approvalStatus / fileCategory / keyword 过滤；分页")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<Page<ProjectFileVo>> list(GetProjectFileListDto dto,
                                                  HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = jwtUtils.getUserFromToken(request.getHeader("token"));
        return ResultUtils.success(Code.SUCCESS,
                projectFilesService.listProjectFilesPage(dto, loginUser.getUserId(), canViewAllProjectFiles()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "项目文件详情（含审批链）")
    public BaseResponse<ProjectFileVo> detail(@PathVariable("id") Long id,
                                              HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = jwtUtils.getUserFromToken(request.getHeader("token"));
        return ResultUtils.success(Code.SUCCESS,
                projectFilesService.getDetail(id, loginUser.getUserId(), canViewAllProjectFiles()));
    }

    @GetMapping("/my-pending")
    @Operation(summary = "我的待审批文件")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<List<ProjectFileVo>> myPending(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        return ResultUtils.success(Code.SUCCESS,
                projectFilesService.listMyPending(loginUser.getUserId()));
    }

    @GetMapping("/statistics")
    @Operation(summary = "项目文件统计", description = "按审批状态分组计数，供卡片统计使用")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<java.util.Map<String, Long>> statistics(HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = jwtUtils.getUserFromToken(request.getHeader("token"));
        return ResultUtils.success(Code.SUCCESS,
                projectFilesService.getStatistics(loginUser.getUserId(), canViewAllProjectFiles()));
    }

    private boolean canViewAllProjectFiles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(authority ->
                "menu:project-files-approval".equals(authority.getAuthority())
                        || "menu:all-projects".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }
}
