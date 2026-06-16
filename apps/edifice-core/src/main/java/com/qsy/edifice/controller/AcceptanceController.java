package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateAcceptanceDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.AcceptanceVo;
import com.qsy.edifice.service.AcceptanceService;
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
 * 成果 / 过程 / 阶段性验收（Phase 3 #4）
 *
 * 使用 {@code acceptanceType}（0/1/2）区分三种验收，审批走统一 ApprovalFlow。
 */
@Tag(name = "验收")
@RestController
@RequestMapping("/acceptance")
public class AcceptanceController {

    @Resource
    private AcceptanceService acceptanceService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/create")
    @Operation(summary = "创建验收单并提交审批",
            description = "acceptanceType: 0-过程 / 1-成果 / 2-阶段性；阶段性验收必须选阶段")
    @PreAuthorize("hasAuthority('menu:oa-applications') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Long> create(@RequestBody CreateAcceptanceDto dto,
                                     HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long id = acceptanceService.createAndSubmit(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, id, "提交成功");
    }

    @PostMapping("/approve")
    @Operation(summary = "审批（通过 / 驳回）",
            description = "通过 + nextApproverId 非空时流转下一级；否则视为终审")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<Boolean> approve(@RequestBody ApproveDto dto,
                                         HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        acceptanceService.approve(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "审批完成");
    }

    @GetMapping("/list")
    @Operation(summary = "验收单列表",
            description = "支持 projectId / acceptanceType / status / keyword 过滤")
    @PreAuthorize("hasAuthority('menu:oa-applications') or hasRole('SUPER_ADMIN')")
    public BaseResponse<List<AcceptanceVo>> list(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "acceptanceType", required = false) Integer acceptanceType,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResultUtils.success(Code.SUCCESS,
                acceptanceService.list(projectId, acceptanceType, status, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "验收单详情（含审批链）")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<AcceptanceVo> detail(@PathVariable("id") Long id,
                                             HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = jwtUtils.getUserFromToken(request.getHeader("token"));
        return ResultUtils.success(Code.SUCCESS,
                acceptanceService.getDetail(id, loginUser.getUserId(), canViewAllAcceptance()));
    }

    @GetMapping("/my-pending")
    @Operation(summary = "我的待审验收单")
    @PreAuthorize("isAuthenticated()")
    public BaseResponse<List<AcceptanceVo>> myPending(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        return ResultUtils.success(Code.SUCCESS,
                acceptanceService.listMyPending(loginUser.getUserId()));
    }

    private boolean canViewAllAcceptance() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(authority ->
                "menu:oa-applications".equals(authority.getAuthority())
                        || "menu:all-projects".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }
}
