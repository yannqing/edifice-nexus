package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 合同管理列表 VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContractListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String projectName;

    private String projectCode;

    private Integer projectStatus;

    private String contractName;

    private String contractCode;

    private Integer contractType;

    private BigDecimal contractAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractFile;

    private String contractOtherFiles;

    private BigDecimal baseAmount;

    private String benefitRules;

    private BigDecimal benefitAmount;

    private Integer benefitStatus;

    private LocalDateTime signingDate;

    private LocalDateTime preStartDate;

    private LocalDateTime preEndDate;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
