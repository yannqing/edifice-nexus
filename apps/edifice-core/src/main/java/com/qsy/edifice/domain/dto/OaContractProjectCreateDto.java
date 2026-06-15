package com.qsy.edifice.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OaContractProjectCreateDto {

    private Integer oaContractId;

    private Long projectTypeId;

    private Integer contractType;

    private BigDecimal baseAmount;

    private BigDecimal benefitAmount;

    private String contractUrl;
}
