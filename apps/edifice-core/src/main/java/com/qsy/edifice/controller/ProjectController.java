package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetMyProjectListDto;
import com.qsy.edifice.domain.vo.ProjectDetailVo;
import com.qsy.edifice.domain.vo.ProjectListVo;
import com.qsy.edifice.domain.vo.ProjectStatisticsVo;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.utils.ResultUtils;
import com.qsy.edifice.utils.UserContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@Tag(name = "项目管理")
@RestController
@RequestMapping("/project")
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @GetMapping("/mine")
    @Operation(summary = "查询本人参与项目", description = "分页 + 条件查询所有符合要求的项目")
    public BaseResponse<Page<ProjectListVo>> getMyProjects(GetMyProjectListDto dto) {
        // 从token中获取当前登录用户的user_id
        Long userId = UserContextUtils.getCurrentUserId();
        if (userId == null) {
            return ResultUtils.failure(Code.TOKEN_ERROR, null, "用户未登录");
        }

        // 处理分页参数默认值
        if (dto.getCurrent() == null || dto.getCurrent() < 1) {
            dto.setCurrent(1);
        }
        if (dto.getPageSize() == null || dto.getPageSize() < 1) {
            dto.setPageSize(10);
        }

        Page<ProjectListVo> result = projectService.getMyProjectPage(userId, dto);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/details/{id}")
    @Operation(summary = "根据id查询项目详情", description = "查看项目详情信息（查看自己参与的项目）")
    public BaseResponse<ProjectDetailVo> getProjectDetailById(@PathVariable("id") Long projectId) {
        // 从token中获取当前登录用户的user_id
        Long userId = UserContextUtils.getCurrentUserId();
        if (userId == null) {
            return ResultUtils.failure(Code.TOKEN_ERROR, null, "用户未登录");
        }

        // 参数校验
        if (projectId == null || projectId <= 0) {
            return ResultUtils.failure(Code.FAILURE, null, "项目ID无效");
        }

        ProjectDetailVo result = projectService.getProjectDetailById(projectId, userId);
        if (result == null) {
            // 先检查项目是否存在，返回更详细的错误信息
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
}
