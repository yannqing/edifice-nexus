package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetOperationAuditLogListDto;
import com.qsy.edifice.domain.entity.OperationAuditLog;
import com.qsy.edifice.domain.vo.OperationAuditLogVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.OperationAuditLogMapper;
import com.qsy.edifice.service.OperationAuditLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class OperationAuditLogServiceImpl implements OperationAuditLogService {

    @Resource
    private OperationAuditLogMapper operationAuditLogMapper;

    @Override
    public Page<OperationAuditLogVo> getOperationAuditLogList(GetOperationAuditLogListDto dto) {
        int current = dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? Math.min(dto.getPageSize(), 100) : 10;

        LambdaQueryWrapper<OperationAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getOperatorName())) {
            wrapper.like(OperationAuditLog::getOperatorName, dto.getOperatorName());
        }
        if (StringUtils.hasText(dto.getModuleName())) {
            wrapper.like(OperationAuditLog::getModuleName, dto.getModuleName());
        }
        if (StringUtils.hasText(dto.getOperationName())) {
            wrapper.like(OperationAuditLog::getOperationName, dto.getOperationName());
        }
        if (StringUtils.hasText(dto.getHttpMethod())) {
            wrapper.eq(OperationAuditLog::getHttpMethod, dto.getHttpMethod().toUpperCase());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(OperationAuditLog::getStatus, dto.getStatus());
        }
        if (dto.getStartTime() != null) {
            wrapper.ge(OperationAuditLog::getCreatedTime, dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            wrapper.le(OperationAuditLog::getCreatedTime, dto.getEndTime());
        }
        wrapper.orderByDesc(OperationAuditLog::getCreatedTime);

        Page<OperationAuditLog> page = operationAuditLogMapper.selectPage(new Page<>(current, pageSize), wrapper);
        List<OperationAuditLogVo> records = page.getRecords().stream()
                .map(this::toVo)
                .toList();

        Page<OperationAuditLogVo> voPage = new Page<>(current, pageSize, page.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public OperationAuditLogVo getOperationAuditLogDetail(Long auditLogId) {
        if (auditLogId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审计日志 ID 不能为空");
        }
        OperationAuditLog auditLog = operationAuditLogMapper.selectById(auditLogId);
        if (auditLog == null) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "审计日志不存在");
        }
        return toVo(auditLog);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveQuietly(OperationAuditLog auditLog) {
        try {
            operationAuditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("保存操作审计日志失败: {}", e.getMessage());
        }
    }

    private OperationAuditLogVo toVo(OperationAuditLog log) {
        return OperationAuditLogVo.builder()
                .auditLogId(log.getAuditLogId())
                .operatorId(log.getOperatorId())
                .operatorName(log.getOperatorName())
                .moduleName(log.getModuleName())
                .operationName(log.getOperationName())
                .httpMethod(log.getHttpMethod())
                .requestPath(log.getRequestPath())
                .clientIp(log.getClientIp())
                .status(log.getStatus())
                .costMs(log.getCostMs())
                .requestSummary(log.getRequestSummary())
                .errorMessage(log.getErrorMessage())
                .createdTime(log.getCreatedTime())
                .build();
    }
}
