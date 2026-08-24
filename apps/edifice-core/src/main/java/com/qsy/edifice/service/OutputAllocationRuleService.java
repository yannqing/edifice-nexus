package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.SaveOutputAllocationRuleDto;
import com.qsy.edifice.domain.entity.OutputValueWorkPool;
import com.qsy.edifice.domain.model.OutputAllocationContext;
import com.qsy.edifice.domain.vo.OutputAllocationRuleVo;

import java.math.BigDecimal;
import java.util.List;

public interface OutputAllocationRuleService {

    OutputAllocationRuleVo getActiveRule(Long projectTypeId);

    OutputAllocationRuleVo saveRule(Long projectTypeId, SaveOutputAllocationRuleDto dto, Long userId);

    OutputAllocationContext calculate(Long projectId, Long projectStageId, BigDecimal totalAmount);

    List<OutputValueWorkPool> getWorkPools(Long outputValueId);
}
