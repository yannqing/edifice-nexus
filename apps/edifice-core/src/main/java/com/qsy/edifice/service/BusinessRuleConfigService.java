package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetBusinessRuleConfigListDto;
import com.qsy.edifice.domain.dto.SaveBusinessRuleConfigDto;
import com.qsy.edifice.domain.vo.BusinessRuleConfigVo;

import java.util.List;

public interface BusinessRuleConfigService {
    Page<BusinessRuleConfigVo> list(GetBusinessRuleConfigListDto dto);
    BusinessRuleConfigVo detail(Long id);
    Long save(SaveBusinessRuleConfigDto dto, Long userId);
    void toggle(Long id, Integer enabled, Long userId);
    void delete(Long id);
    List<BusinessRuleConfigVo> getEnabledByBizType(String bizType);
    boolean booleanValue(String bizType, String ruleKey, boolean defaultValue);
    String stringValue(String bizType, String ruleKey, String defaultValue);
}
