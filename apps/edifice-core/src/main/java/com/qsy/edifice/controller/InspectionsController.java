package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.ApplyInspectionDto;
import com.qsy.edifice.domain.dto.ApprovalInspectionDto;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.InspectionFormDetailVo;
import com.qsy.edifice.domain.vo.InspectionFormListVo;
import com.qsy.edifice.domain.vo.InspectionOverviewVo;
import com.qsy.edifice.service.InspectionFormService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "验工单管理")
@RestController
@RequestMapping("/inspections")
public class InspectionsController {

    @Resource
    private InspectionFormService inspectionFormService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/all")
    @Operation(summary = "全部验工单列表", description = "分页+条件查询所有验工单")
    @PreAuthorize("hasAuthority('menu:inspection-management') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Page<InspectionFormListVo>> getAllInspections(GetInspectionFormListDto dto) {
        Page<InspectionFormListVo> result = inspectionFormService.getAllInspections(dto);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/my-list")
    @Operation(summary = "我的验工单列表", description = "查询当前用户提交的验工单")
    public BaseResponse<Page<InspectionFormListVo>> getMyInspections(GetInspectionFormListDto dto, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Page<InspectionFormListVo> result = inspectionFormService.getMyInspections(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/my-pending")
    @Operation(summary = "我的待审批验工单列表", description = "查询当前用户作为当前审批人的验工单")
    @PreAuthorize("hasAuthority('menu:inspection-approval') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Page<InspectionFormListVo>> getMyPendingInspections(GetInspectionFormListDto dto, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Page<InspectionFormListVo> result = inspectionFormService.getMyPendingInspections(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据id查看验工单详情")
    public BaseResponse<InspectionFormDetailVo> getInspectionById(@PathVariable Long id) {
        InspectionFormDetailVo result = inspectionFormService.getInspectionById(id);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/my-list/statistic")
    @Operation(summary = "我的验工单数据总览",
            description = "仅统计当前登录用户提交的验工单，与 my-list 列表口径一致")
    public BaseResponse<InspectionOverviewVo> getMyInspectionOverview(HttpServletRequest request)
            throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        InspectionOverviewVo result = inspectionFormService.getInspectionOverview(loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/my-pending/statistic")
    @Operation(summary = "我的待审批验工单数据总览",
            description = "仅统计当前登录用户作为当前审批人的验工单，与 my-pending 列表口径一致")
    @PreAuthorize("hasAuthority('menu:inspection-approval') or hasRole('SUPER_ADMIN')")
    public BaseResponse<InspectionOverviewVo> getMyPendingInspectionOverview(HttpServletRequest request)
            throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        InspectionOverviewVo result = inspectionFormService.getMyPendingInspectionOverview(loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/statistic")
    @Operation(summary = "全局验工单数据总览", description = "全量统计，不按用户过滤")
    @PreAuthorize("hasAuthority('menu:inspection-management') or hasRole('SUPER_ADMIN')")
    public BaseResponse<InspectionOverviewVo> getInspectionOverview() {
        InspectionOverviewVo result = inspectionFormService.getInspectionOverview(null);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @PostMapping("/apply")
    @Operation(summary = "提交验工单", description = "项目阶段完成后，项目经理提交验工单")
    public BaseResponse<Long> applyInspection(@RequestBody ApplyInspectionDto dto, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long inspectionId = inspectionFormService.applyInspection(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, inspectionId, "验工单提交成功");
    }

    @PutMapping("/approval")
    @Operation(summary = "审批验工单", description = "审批人通过或驳回验工单")
    public BaseResponse<Boolean> approvalInspection(@RequestBody ApprovalInspectionDto dto, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        inspectionFormService.approvalInspection(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "审批完成");
    }

    @GetMapping("/export")
    @Operation(summary = "导出全部验工单", description = "按当前筛选条件导出所有验工单为 Excel，不分页")
    @PreAuthorize("hasAuthority('menu:inspection-management') or hasRole('SUPER_ADMIN')")
    public void exportAllInspections(GetInspectionFormListDto dto, HttpServletResponse response) throws IOException {
        // 不带 applyUserId 约束 = 全部
        if (dto != null) dto.setApplyUserId(null);
        inspectionFormService.exportInspections(dto, response);
    }

    @GetMapping("/my-list/export")
    @Operation(summary = "导出我的验工单", description = "仅导出当前用户提交的验工单，按当前筛选条件")
    public void exportMyInspections(GetInspectionFormListDto dto,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws IOException, JsonProcessingException {
        // 下载链接场景下 token 可能挂在 query，兼容两种方式（与 JwtAuthenticationTokenFilter 一致）
        String token = request.getHeader("token");
        if (token == null || token.isEmpty()) {
            token = request.getParameter("token");
        }
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        dto.setApplyUserId(loginUser.getUserId());
        inspectionFormService.exportInspections(dto, response);
    }
}
