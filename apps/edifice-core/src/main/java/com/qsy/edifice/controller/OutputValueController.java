package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.CreateOutputValueDto;
import com.qsy.edifice.domain.dto.OutputValueActionDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.OutputValueVo;
import com.qsy.edifice.service.OutputValueService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "产值分配管理")
@RestController
@RequestMapping("/output-value")
@PreAuthorize("hasAuthority('menu:output-value') or hasRole('SUPER_ADMIN')")
public class OutputValueController {

    @Resource
    private OutputValueService outputValueService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/list")
    @Operation(summary = "产值分配单列表", description = "查询全部或按状态筛选")
    public BaseResponse<List<OutputValueVo>> getList(@RequestParam(required = false) Integer status) {
        List<OutputValueVo> result = outputValueService.getOutputValueList(status);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/statistics")
    @Operation(summary = "产值分配统计")
    public BaseResponse<Map<String, Object>> getStatistics() {
        Map<String, Object> result = outputValueService.getStatistics();
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/export")
    @Operation(summary = "导出产值分配单", description = "按状态和关键字导出 Excel")
    public void export(@RequestParam(required = false) Integer status,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        outputValueService.exportOutputValues(status, keyword, response);
    }

    @PostMapping("/create")
    @Operation(summary = "创建产值分配单")
    public BaseResponse<Long> create(@RequestBody CreateOutputValueDto dto, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long id = outputValueService.createOutputValue(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, id, "创建成功");
    }

    @PutMapping("/confirm/{id}")
    @Operation(summary = "确认分配", description = "待确认→待审核")
    public BaseResponse<Boolean> confirm(@PathVariable("id") Long id,
                                         @RequestBody(required = false) OutputValueActionDto dto,
                                         HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        outputValueService.confirmOutputValue(id, loginUser.getUserId(), dto == null ? null : dto.getNextUserId());
        return ResultUtils.success(Code.SUCCESS, true, "确认成功");
    }

    @PutMapping("/approve/{id}")
    @Operation(summary = "审批通过", description = "待审核→已审批")
    public BaseResponse<Boolean> approve(@PathVariable("id") Long id,
                                         @RequestBody(required = false) OutputValueActionDto dto,
                                         HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        outputValueService.approveOutputValue(id, loginUser.getUserId(), dto == null ? null : dto.getNextUserId());
        return ResultUtils.success(Code.SUCCESS, true, "审批通过");
    }

    @PutMapping("/pay/{id}")
    @Operation(summary = "发放产值", description = "已审批→已发放")
    public BaseResponse<Boolean> pay(@PathVariable("id") Long id, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        outputValueService.payOutputValue(id, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "发放成功");
    }
}
