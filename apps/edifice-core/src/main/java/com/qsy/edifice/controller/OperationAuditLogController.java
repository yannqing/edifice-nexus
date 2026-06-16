package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetOperationAuditLogListDto;
import com.qsy.edifice.domain.vo.OperationAuditLogVo;
import com.qsy.edifice.service.OperationAuditLogService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "操作审计")
@RestController
@RequestMapping("/audit-logs")
@PreAuthorize("hasAuthority('menu:audit-logs') or hasRole('SUPER_ADMIN')")
public class OperationAuditLogController {

    @Resource
    private OperationAuditLogService operationAuditLogService;

    @GetMapping("/list")
    @Operation(summary = "操作审计日志列表")
    public BaseResponse<Page<OperationAuditLogVo>> getList(GetOperationAuditLogListDto dto) {
        Page<OperationAuditLogVo> result = operationAuditLogService.getOperationAuditLogList(dto);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "操作审计日志详情")
    public BaseResponse<OperationAuditLogVo> getDetail(@PathVariable("id") Long id) {
        OperationAuditLogVo result = operationAuditLogService.getOperationAuditLogDetail(id);
        return ResultUtils.success(Code.SUCCESS, result);
    }
}
