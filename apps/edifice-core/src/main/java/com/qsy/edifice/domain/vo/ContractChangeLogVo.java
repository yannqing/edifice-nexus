package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 合同字段变更日志 VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContractChangeLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long changeLogId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String fieldName;

    private String fieldLabel;

    private String oldValue;

    private String newValue;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    private LocalDateTime createdTime;
}
