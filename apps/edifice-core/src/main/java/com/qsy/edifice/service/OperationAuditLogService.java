package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetOperationAuditLogListDto;
import com.qsy.edifice.domain.entity.OperationAuditLog;
import com.qsy.edifice.domain.vo.OperationAuditLogVo;

public interface OperationAuditLogService {

    Page<OperationAuditLogVo> getOperationAuditLogList(GetOperationAuditLogListDto dto);

    OperationAuditLogVo getOperationAuditLogDetail(Long auditLogId);

    void saveQuietly(OperationAuditLog auditLog);
}
