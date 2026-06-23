package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.entity.ProjectStageTemplate;
import com.qsy.edifice.mapper.ProjectStageTemplateMapper;
import com.qsy.edifice.service.ProjectStageTemplateService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 阶段模板管理
 */
@Tag(name = "阶段模板管理")
@RestController
@RequestMapping("/stage-template")
public class StageTemplateController {

    @Resource
    private ProjectStageTemplateService stageTemplateService;

    @Resource
    private ProjectStageTemplateMapper stageTemplateMapper;

    @GetMapping("/list")
    @Operation(summary = "分页查询阶段模板")
    @PreAuthorize("hasAuthority('menu:stage-template-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Page<ProjectStageTemplate>> list(
            @RequestParam(value = "projectTypeId", required = false) Long projectTypeId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        LambdaQueryWrapper<ProjectStageTemplate> wrapper = new LambdaQueryWrapper<>();
        if (projectTypeId != null) {
            wrapper.eq(ProjectStageTemplate::getProjectTypeId, projectTypeId);
        }
        if (status != null) {
            wrapper.eq(ProjectStageTemplate::getStageStatus, status);
        }
        wrapper.orderByAsc(ProjectStageTemplate::getStageId);

        Page<ProjectStageTemplate> page = stageTemplateMapper.selectPage(new Page<>(current, pageSize), wrapper);
        return ResultUtils.success(Code.SUCCESS, page);
    }

    @GetMapping("/all")
    @Operation(summary = "获取全部启用的阶段模板")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<List<ProjectStageTemplate>> all(
            @RequestParam(value = "projectTypeId", required = false) Long projectTypeId) {
        List<ProjectStageTemplate> result;
        if (projectTypeId != null) {
            result = stageTemplateService.getEnabledByProjectTypeId(projectTypeId);
        } else {
            result = stageTemplateService.getAllEnabledProjectStageTemplates();
        }
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @PostMapping("/create")
    @Operation(summary = "新增阶段模板")
    @PreAuthorize("hasAuthority('menu:stage-template-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Boolean> create(@RequestBody ProjectStageTemplate template) {
        if (template.getProjectTypeId() == null) {
            return ResultUtils.failure(Code.FAILURE, null, "项目类型不能为空");
        }
        boolean ok = stageTemplateService.saveProjectStageTemplate(template);
        return ok ? ResultUtils.success(Code.SUCCESS, true, "创建成功")
                  : ResultUtils.failure(Code.FAILURE, null, "创建失败");
    }

    @PutMapping("/update")
    @Operation(summary = "修改阶段模板")
    @PreAuthorize("hasAuthority('menu:stage-template-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Boolean> update(@RequestBody ProjectStageTemplate template) {
        if (template.getStageId() == null) {
            return ResultUtils.failure(Code.FAILURE, null, "阶段ID不能为空");
        }
        boolean ok = stageTemplateService.updateProjectStageTemplate(template);
        return ok ? ResultUtils.success(Code.SUCCESS, true, "更新成功")
                  : ResultUtils.failure(Code.FAILURE, null, "更新失败");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除阶段模板")
    @PreAuthorize("hasAuthority('menu:stage-template-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Boolean> delete(@PathVariable("id") Long id) {
        boolean ok = stageTemplateService.deleteProjectStageTemplate(id);
        return ok ? ResultUtils.success(Code.SUCCESS, true, "删除成功")
                  : ResultUtils.failure(Code.FAILURE, null, "删除失败");
    }
}
