package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetApprovalFlowConfigListDto;
import com.qsy.edifice.domain.dto.SaveApprovalFlowConfigDto;
import com.qsy.edifice.domain.dto.ToggleConfigStatusDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ApprovalFlowConfigVo;
import com.qsy.edifice.service.ApprovalFlowConfigService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "流程配置中心")
@RestController
@RequestMapping("/flow-config")
public class ApprovalFlowConfigController {

    @Resource
    private ApprovalFlowConfigService approvalFlowConfigService;

    @Resource
    private JwtUtils jwtUtils;

    @GetMapping("/list")
    @Operation(summary = "流程配置列表")
    @PreAuthorize("hasAuthority('menu:flow-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Page<ApprovalFlowConfigVo>> list(GetApprovalFlowConfigListDto dto) {
        return ResultUtils.success(Code.SUCCESS, approvalFlowConfigService.list(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "流程配置详情")
    @PreAuthorize("hasAuthority('menu:flow-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<ApprovalFlowConfigVo> detail(@PathVariable Long id) {
        return ResultUtils.success(Code.SUCCESS, approvalFlowConfigService.detail(id));
    }

    @GetMapping("/enabled/{bizType}")
    @Operation(summary = "按业务类型获取启用流程配置")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('menu:flow-config') "
            + "or (#bizType == 'file' and hasAuthority('menu:project-files-approval')) "
            + "or (#bizType == 'inspection' and hasAuthority('menu:inspection-approval')) "
            + "or (#bizType == 'output' and hasAuthority('menu:output-value')) "
            + "or (#bizType == 'timesheet' and hasAuthority('menu:timesheet')) "
            + "or (#bizType == 'bid' and hasAuthority('menu:bids')) "
            + "or (#bizType == 'acceptance' and hasAuthority('menu:oa-applications')) "
            + "or (#bizType == 'oa_application' and hasAuthority('menu:oa-applications'))")
    public BaseResponse<ApprovalFlowConfigVo> enabled(@PathVariable String bizType) {
        return ResultUtils.success(Code.SUCCESS, approvalFlowConfigService.getEnabledByBizType(bizType));
    }

    @PostMapping("/save")
    @Operation(summary = "保存流程配置")
    @PreAuthorize("hasAuthority('menu:flow-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Long> save(@RequestBody SaveApprovalFlowConfigDto dto, HttpServletRequest request)
            throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, approvalFlowConfigService.save(dto, userId(request)), "保存成功");
    }

    @PutMapping("/toggle/{id}")
    @Operation(summary = "启停流程配置")
    @PreAuthorize("hasAuthority('menu:flow-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Boolean> toggle(@PathVariable Long id,
                                        @RequestBody ToggleConfigStatusDto dto,
                                        HttpServletRequest request) throws JsonProcessingException {
        approvalFlowConfigService.toggle(id, dto == null ? null : dto.getEnabled(), userId(request));
        return ResultUtils.success(Code.SUCCESS, true, "操作成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除流程配置")
    @PreAuthorize("hasAuthority('menu:flow-config') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Boolean> delete(@PathVariable Long id) {
        approvalFlowConfigService.delete(id);
        return ResultUtils.success(Code.SUCCESS, true, "删除成功");
    }

    private Long userId(HttpServletRequest request) throws JsonProcessingException {
        SysUser user = jwtUtils.getUserFromToken(request.getHeader("token"));
        return user.getUserId();
    }
}
