package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationAuditLogVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long auditLogId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    private String moduleName;

    private String operationName;

    private String httpMethod;

    private String requestPath;

    private String clientIp;

    private Integer status;

    private Long costMs;

    private String requestSummary;

    private String errorMessage;

    private LocalDateTime createdTime;
}
