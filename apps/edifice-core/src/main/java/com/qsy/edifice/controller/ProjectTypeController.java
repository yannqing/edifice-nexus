package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.entity.ProjectType;
import com.qsy.edifice.mapper.ProjectTypeMapper;
import com.qsy.edifice.service.ProjectTypeService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目类型管理
 */
@Tag(name = "项目类型管理")
@RestController
@RequestMapping("/project-type")
public class ProjectTypeController {

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectTypeMapper projectTypeMapper;

    @GetMapping("/list")
    @Operation(summary = "分页查询项目类型")
    @PreAuthorize("hasAuthority('menu:project-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Page<ProjectType>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        LambdaQueryWrapper<ProjectType> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ProjectType::getProjectTypeName, kw)
                    .or().like(ProjectType::getProjectTypeCode, kw));
        }
        if (status != null) {
            wrapper.eq(ProjectType::getProjectTypeStatus, status);
        }
        wrapper.orderByDesc(ProjectType::getCreatedTime);

        Page<ProjectType> page = projectTypeMapper.selectPage(new Page<>(current, pageSize), wrapper);
        return ResultUtils.success(Code.SUCCESS, page);
    }

    @GetMapping("/all")
    @Operation(summary = "获取全部启用的项目类型")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<List<ProjectType>> all() {
        List<ProjectType> result = projectTypeService.getAllEnabledProjectTypes();
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @PostMapping("/create")
    @Operation(summary = "新增项目类型")
    @PreAuthorize("hasAuthority('menu:project-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Boolean> create(@RequestBody ProjectType projectType) {
        // 校验编码唯一性
        ProjectType existing = projectTypeService.getProjectTypeByCode(projectType.getProjectTypeCode());
        if (existing != null) {
            return ResultUtils.failure(Code.FAILURE, null, "类型编码已存在");
        }
        boolean ok = projectTypeService.saveProjectType(projectType);
        return ok ? ResultUtils.success(Code.SUCCESS, true, "创建成功")
                  : ResultUtils.failure(Code.FAILURE, null, "创建失败");
    }

    @PutMapping("/update")
    @Operation(summary = "修改项目类型")
    @PreAuthorize("hasAuthority('menu:project-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Boolean> update(@RequestBody ProjectType projectType) {
        if (projectType.getProjectTypeId() == null) {
            return ResultUtils.failure(Code.FAILURE, null, "类型ID不能为空");
        }
        // 校验编码唯一性（排除自身）
        ProjectType existing = projectTypeService.getProjectTypeByCode(projectType.getProjectTypeCode());
        if (existing != null && !existing.getProjectTypeId().equals(projectType.getProjectTypeId())) {
            return ResultUtils.failure(Code.FAILURE, null, "类型编码已存在");
        }
        boolean ok = projectTypeService.updateProjectType(projectType);
        return ok ? ResultUtils.success(Code.SUCCESS, true, "更新成功")
                  : ResultUtils.failure(Code.FAILURE, null, "更新失败");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除项目类型")
    @PreAuthorize("hasAuthority('menu:project-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Boolean> delete(@PathVariable("id") Long id) {
        boolean ok = projectTypeService.deleteProjectType(id);
        return ok ? ResultUtils.success(Code.SUCCESS, true, "删除成功")
                  : ResultUtils.failure(Code.FAILURE, null, "删除失败");
    }
}
