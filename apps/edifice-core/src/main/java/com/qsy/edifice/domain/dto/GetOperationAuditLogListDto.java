package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "操作审计日志查询参数")
public class GetOperationAuditLogListDto {

    @Schema(description = "操作人姓名/账号")
    private String operatorName;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "操作名称")
    private String operationName;

    @Schema(description = "HTTP 方法")
    private String httpMethod;

    @Schema(description = "状态：1成功，0失败")
    private Integer status;

    @Schema(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "当前页")
    private Integer current = 1;

    @Schema(description = "一页大小")
    private Integer pageSize = 10;
}
