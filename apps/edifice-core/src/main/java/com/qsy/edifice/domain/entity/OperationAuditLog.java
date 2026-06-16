package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作审计日志。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("operation_audit_log")
public class OperationAuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "audit_log_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long auditLogId;

    @TableField("operator_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    @TableField("operator_name")
    private String operatorName;

    @TableField("module_name")
    private String moduleName;

    @TableField("operation_name")
    private String operationName;

    @TableField("http_method")
    private String httpMethod;

    @TableField("request_path")
    private String requestPath;

    @TableField("client_ip")
    private String clientIp;

    /**
     * 1 成功，0 失败。
     */
    @TableField("status")
    private Integer status;

    @TableField("cost_ms")
    private Long costMs;

    @TableField("request_summary")
    private String requestSummary;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
