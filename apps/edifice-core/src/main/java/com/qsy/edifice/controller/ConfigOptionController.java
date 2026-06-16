package com.qsy.edifice.controller;

import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.vo.ConfigOptionBundleVo;
import com.qsy.edifice.service.ConfigOptionService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "配置选项")
@RestController
@RequestMapping("/config-options")
@PreAuthorize("hasAnyAuthority('menu:flow-config','menu:business-rule-config') or hasRole('SUPER_ADMIN')")
public class ConfigOptionController {

    @Resource
    private ConfigOptionService configOptionService;

    @GetMapping
    @Operation(summary = "流程/规则配置选项")
    public BaseResponse<ConfigOptionBundleVo> options() {
        return ResultUtils.success(Code.SUCCESS, configOptionService.getOptions());
    }
}
