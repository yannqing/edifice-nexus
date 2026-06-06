package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.CreateOaApplicationDto;
import com.qsy.edifice.domain.dto.GetOaApplicationListDto;
import com.qsy.edifice.domain.dto.SubmitOaApplicationDto;
import com.qsy.edifice.domain.dto.UpdateOaApplicationDto;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.OaApplicationTypeVo;
import com.qsy.edifice.domain.vo.OaApplicationVo;
import com.qsy.edifice.service.ApprovalFlowService;
import com.qsy.edifice.service.OaApplicationService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "OA 申请中心")
@RestController
@RequestMapping("/oa/application")
@PreAuthorize("hasAuthority('menu:oa-applications') or hasRole('SUPER_ADMIN')")
public class OaApplicationController {

    @Resource
    private OaApplicationService oaApplicationService;

    @Resource
    private JwtUtils jwtUtils;

    @GetMapping("/types")
    @Operation(summary = "OA 申请类型")
    public BaseResponse<List<OaApplicationTypeVo>> types() {
        return ResultUtils.success(Code.SUCCESS, oaApplicationService.listTypes());
    }

    @GetMapping("/list")
    @Operation(summary = "OA 申请列表")
    public BaseResponse<Page<OaApplicationVo>> list(GetOaApplicationListDto dto,
                                                    HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = currentUser(request);
        return ResultUtils.success(Code.SUCCESS, oaApplicationService.list(dto, loginUser.getUserId()));
    }

    @GetMapping("/pending")
    @Operation(summary = "我的 OA 待审批")
    public BaseResponse<Page<OaApplicationVo>> pending(GetOaApplicationListDto dto,
                                                       HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = currentUser(request);
        return ResultUtils.success(Code.SUCCESS, oaApplicationService.listMyPending(dto, loginUser.getUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "OA 申请详情")
    public BaseResponse<OaApplicationVo> detail(@PathVariable("id") Long id,
                                                HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = currentUser(request);
        return ResultUtils.success(Code.SUCCESS, oaApplicationService.getById(id, loginUser.getUserId()));
    }

    @PostMapping("/create")
    @Operation(summary = "创建 OA 申请")
    public BaseResponse<Long> create(@RequestBody CreateOaApplicationDto dto,
                                     HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = currentUser(request);
        Long id = oaApplicationService.create(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, id, "创建成功");
    }

    @PutMapping("/update")
    @Operation(summary = "更新 OA 申请草稿")
    public BaseResponse<Boolean> update(@RequestBody UpdateOaApplicationDto dto,
                                        HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = currentUser(request);
        oaApplicationService.update(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "保存成功");
    }

    @PutMapping("/submit/{id}")
    @Operation(summary = "提交 OA 申请")
    public BaseResponse<Boolean> submit(@PathVariable("id") Long id,
                                        @RequestBody(required = false) SubmitOaApplicationDto dto,
                                        HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = currentUser(request);
        oaApplicationService.submit(id, dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "提交成功");
    }

    @PutMapping("/withdraw/{id}")
    @Operation(summary = "撤回 OA 申请")
    public BaseResponse<Boolean> withdraw(@PathVariable("id") Long id,
                                          HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = currentUser(request);
        oaApplicationService.withdraw(id, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "撤回成功");
    }

    @PostMapping("/approve")
    @Operation(summary = "审批 OA 申请")
    public BaseResponse<ApprovalFlowService.ApprovalResult> approve(@RequestBody ApproveDto dto,
                                                                    HttpServletRequest request) throws JsonProcessingException {
        SysUser loginUser = currentUser(request);
        ApprovalFlowService.ApprovalResult result = oaApplicationService.approve(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result, "审批完成");
    }

    private SysUser currentUser(HttpServletRequest request) throws JsonProcessingException {
        return jwtUtils.getUserFromToken(request.getHeader("token"));
    }
}
