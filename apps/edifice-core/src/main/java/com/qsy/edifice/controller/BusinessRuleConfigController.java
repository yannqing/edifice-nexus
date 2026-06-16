package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetBusinessRuleConfigListDto;
import com.qsy.edifice.domain.dto.SaveBusinessRuleConfigDto;
import com.qsy.edifice.domain.dto.ToggleConfigStatusDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.BusinessRuleConfigVo;
import com.qsy.edifice.service.BusinessRuleConfigService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "业务规则配置")
@RestController
@RequestMapping("/business-rule-config")
@PreAuthorize("hasAuthority('menu:business-rule-config') or hasRole('SUPER_ADMIN')")
public class BusinessRuleConfigController {

    @Resource
    private BusinessRuleConfigService businessRuleConfigService;

    @Resource
    private JwtUtils jwtUtils;

    @GetMapping("/list")
    @Operation(summary = "业务规则配置列表")
    public BaseResponse<Page<BusinessRuleConfigVo>> list(GetBusinessRuleConfigListDto dto) {
        return ResultUtils.success(Code.SUCCESS, businessRuleConfigService.list(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "业务规则配置详情")
    public BaseResponse<BusinessRuleConfigVo> detail(@PathVariable Long id) {
        return ResultUtils.success(Code.SUCCESS, businessRuleConfigService.detail(id));
    }

    @GetMapping("/enabled/{bizType}")
    @Operation(summary = "按业务类型获取启用业务规则")
    public BaseResponse<List<BusinessRuleConfigVo>> enabled(@PathVariable String bizType) {
        return ResultUtils.success(Code.SUCCESS, businessRuleConfigService.getEnabledByBizType(bizType));
    }

    @PostMapping("/save")
    @Operation(summary = "保存业务规则配置")
    public BaseResponse<Long> save(@RequestBody SaveBusinessRuleConfigDto dto, HttpServletRequest request)
            throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, businessRuleConfigService.save(dto, userId(request)), "保存成功");
    }

    @PutMapping("/toggle/{id}")
    @Operation(summary = "启停业务规则")
    public BaseResponse<Boolean> toggle(@PathVariable Long id,
                                        @RequestBody ToggleConfigStatusDto dto,
                                        HttpServletRequest request) throws JsonProcessingException {
        businessRuleConfigService.toggle(id, dto == null ? null : dto.getEnabled(), userId(request));
        return ResultUtils.success(Code.SUCCESS, true, "操作成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除业务规则配置")
    public BaseResponse<Boolean> delete(@PathVariable Long id) {
        businessRuleConfigService.delete(id);
        return ResultUtils.success(Code.SUCCESS, true, "删除成功");
    }

    private Long userId(HttpServletRequest request) throws JsonProcessingException {
        SysUser user = jwtUtils.getUserFromToken(request.getHeader("token"));
        return user.getUserId();
    }
}
