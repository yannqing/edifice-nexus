package com.qsy.edifice.controller;

import com.alibaba.excel.EasyExcel;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.ProjectCreateDto;
import com.qsy.edifice.domain.dto.ProjectExportDTO;
import com.qsy.edifice.domain.dto.SysUserCreateDto;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

@Tag(name = "项目管理")
@RestController
@RequestMapping("/project")
public class ProjectController {
    @Resource
    private ProjectService projectService;

    @PostMapping("/create")
    @Operation(summary = "新增项目")
    public BaseResponse<Boolean> createUser(@Valid @RequestBody ProjectCreateDto sysUserCreateDto) {
        Long currentUserId = getCurrentUserId();
        boolean result = projectService.createUser(sysUserCreateDto,currentUserId);

        if (result) {
            return ResultUtils.success(Code.SUCCESS, true);
        } else {
            return ResultUtils.failure(Code.FAILURE, false);
        }
    }
    @PostMapping("/export")
    @Operation(summary = "导出文件", description = "xlxs")
    public void exportProjects(@RequestBody List<Long> projectIds, HttpServletResponse response) throws IOException {
        String fileName = URLEncoder.encode("项目列表_" + System.currentTimeMillis(), "UTF-8");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        List<ProjectExportDTO> data = projectService.getProjectsForExport(projectIds);

        EasyExcel.write(response.getOutputStream(), ProjectExportDTO.class)
                .autoCloseStream(false) // 手动关闭流
                .sheet("项目列表")
                .doWrite(data);

        // 防止 EasyExcel 自动关闭导致异常
        response.flushBuffer();

    }
    private Long getCurrentUserId() {
        // 示例：return SecurityUtils.getCurrentUserId();
        return 1001L; // 测试用
    }

}
