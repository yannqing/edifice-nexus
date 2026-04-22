package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.CreateProjectDto;
import com.qsy.edifice.domain.dto.GetAllProjectListDto;
import com.qsy.edifice.domain.dto.GetMyProjectListDto;
import com.qsy.edifice.domain.dto.UpdateProjectDto;
import com.qsy.edifice.domain.entity.ProjectStageTemplate;
import com.qsy.edifice.domain.entity.ProjectType;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ProjectDetailVo;
import com.qsy.edifice.domain.vo.ProjectListVo;
import com.qsy.edifice.domain.vo.ProjectStatisticsVo;
import com.qsy.edifice.service.ProjectExcelService;
import com.qsy.edifice.service.ProjectStageService;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.service.ProjectStageTemplateService;
import com.qsy.edifice.service.ProjectTypeService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Tag(name = "项目管理")
@RestController
@RequestMapping("/project")
public class ProjectController {

    @Resource
    private ProjectService projectService;
    @Resource
    private ProjectTypeService projectTypeService;
    @Resource
    private ProjectStageTemplateService projectStageTemplateService;
    @Resource
    private ProjectExcelService projectExcelService;
    @Resource
    private ProjectStageService projectStageService;
    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/create")
    @Operation(summary = "新建项目", description = "新建项目，以及初始化合同、阶段、成员等数据")
    public BaseResponse<Long> createProject(@RequestBody CreateProjectDto dto, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long projectId = projectService.createProject(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, projectId, "项目创建成功");
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目", description = "更新项目基本信息、合同信息及成员")
    public BaseResponse<Boolean> updateProjectFull(@RequestBody UpdateProjectDto dto) {
        projectService.updateProjectFull(dto);
        return ResultUtils.success(Code.SUCCESS, true, "更新成功");
    }

    @PutMapping("/stage/start")
    @Operation(summary = "启动阶段", description = "批量启动项目阶段（未开始→进行中），支持多个阶段同时启动")
    public BaseResponse<Boolean> startStages(@RequestBody List<Long> stageIds) {
        projectStageService.startStages(new HashSet<>(stageIds));
        return ResultUtils.success(Code.SUCCESS, true, "阶段启动成功");
    }

    @PutMapping("/stage/restart/{id}")
    @Operation(summary = "重启阶段", description = "重新启动已驳回的阶段（已驳回→进行中）")
    public BaseResponse<Boolean> restartStage(@PathVariable("id") Long stageId) {
        projectStageService.restartStage(stageId);
        return ResultUtils.success(Code.SUCCESS, true, "阶段已重新启动");
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除项目", description = "逻辑删除项目")
    public BaseResponse<Boolean> deleteProject(@PathVariable("id") Long projectId) {
        boolean result = projectService.deleteProject(projectId);
        if (result) {
            return ResultUtils.success(Code.SUCCESS, true, "删除成功");
        }
        return ResultUtils.failure(Code.FAILURE, false, "删除失败，项目不存在");
    }

    @GetMapping("/all")
    @Operation(summary = "查询全部项目", description = "分页 + 条件查询所有项目，支持按名称、状态、分类筛选")
    public BaseResponse<Page<ProjectListVo>> getAllProjects(GetAllProjectListDto dto) {
        Page<ProjectListVo> result = projectService.getAllProjectPage(dto);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/mine/statistics")
    @Operation(summary = "我的项目统计", description = "统计本人参与项目的各状态数量")
    public BaseResponse<ProjectStatisticsVo> getMyProjectStatistics(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        ProjectStatisticsVo result = projectService.getMyProjectStatistics(loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/mine")
    @Operation(summary = "查询本人参与项目", description = "分页 + 条件查询所有符合要求的项目")
    public BaseResponse<Page<ProjectListVo>> getMyProjects(GetMyProjectListDto dto, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Page<ProjectListVo> result = projectService.getMyProjectPage(loginUser.getUserId(), dto);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/details/{id}")
    @Operation(summary = "根据id查询项目详情", description = "查看项目详情信息（查看自己参与的项目）")
    public BaseResponse<ProjectDetailVo> getProjectDetailById(@PathVariable("id") Long projectId, HttpServletRequest request) throws JsonProcessingException {

        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        ProjectDetailVo result = projectService.getProjectDetailById(projectId, loginUser.getUserId());

        if (result == null) {
            boolean projectExists = projectService.checkProjectExists(projectId);
            if (!projectExists) {
                return ResultUtils.failure(Code.FAILURE, null, "项目不存在");
            }
            return ResultUtils.failure(Code.FAILURE, null, "您无权限查看该项目");
        }
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/all/statistics")
    @Operation(summary = "项目信息统计总览", description = "对项目进行统计，用于全部项目页")
    public BaseResponse<ProjectStatisticsVo> getProjectStatistics() {
        ProjectStatisticsVo result = projectService.getProjectStatistics();
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/types")
    @Operation(summary = "获取所有启用的项目类型", description = "用于新建项目时选择项目类型")
    public BaseResponse<List<ProjectType>> getProjectTypes() {
        List<ProjectType> result = projectTypeService.getAllEnabledProjectTypes();
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/stage-templates")
    @Operation(summary = "获取启用的阶段模板", description = "根据项目类型获取对应阶段模板，不传则获取全部")
    public BaseResponse<List<ProjectStageTemplate>> getStageTemplates(
            @RequestParam(value = "projectTypeId", required = false) Long projectTypeId) {
        List<ProjectStageTemplate> result;
        if (projectTypeId != null) {
            result = projectStageTemplateService.getEnabledByProjectTypeId(projectTypeId);
        } else {
            result = projectStageTemplateService.getAllEnabledProjectStageTemplates();
        }
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/export")
    @Operation(summary = "导出项目", description = "导出全部或指定项目为 Excel 文件")
    public void exportProjects(
            @RequestParam(value = "ids", required = false) List<Long> ids,
            HttpServletResponse response) throws IOException {
        projectExcelService.exportProjects(ids, response);
    }

    @GetMapping("/export/template")
    @Operation(summary = "下载导入模板", description = "下载项目导入 Excel 模板")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        projectExcelService.downloadTemplate(response);
    }

    @PostMapping("/import")
    @Operation(summary = "导入项目", description = "通过 Excel 文件批量导入项目")
    public BaseResponse<String> importProjects(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        String result = projectExcelService.importProjects(file, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result);
    }
}
