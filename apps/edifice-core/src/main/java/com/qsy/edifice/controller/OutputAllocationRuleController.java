package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.SaveOutputAllocationRuleDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.OutputAllocationRuleVo;
import com.qsy.edifice.service.OutputAllocationRuleService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "产值分配规则")
@RestController
@RequestMapping("/output-allocation-rule")
@PreAuthorize("hasAnyAuthority('menu:project-config', 'menu:performance-rules') or hasRole('SUPER_ADMIN')")
public class OutputAllocationRuleController {

    @Resource
    private OutputAllocationRuleService outputAllocationRuleService;

    @Resource
    private JwtUtils jwtUtils;

    @GetMapping("/{projectTypeId}")
    @Operation(summary = "获取项目类型当前生效的产值分配规则")
    public BaseResponse<OutputAllocationRuleVo> getActiveRule(@PathVariable Long projectTypeId) {
        return ResultUtils.success(Code.SUCCESS, outputAllocationRuleService.getActiveRule(projectTypeId));
    }

    @PutMapping("/{projectTypeId}")
    @Operation(summary = "保存产值分配规则", description = "保存时创建新版本，历史分配单继续使用原规则快照")
    public BaseResponse<OutputAllocationRuleVo> saveRule(@PathVariable Long projectTypeId,
                                                          @RequestBody SaveOutputAllocationRuleDto dto,
                                                          HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        OutputAllocationRuleVo result = outputAllocationRuleService.saveRule(projectTypeId, dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result, "规则已保存并生成新版本");
    }
}
