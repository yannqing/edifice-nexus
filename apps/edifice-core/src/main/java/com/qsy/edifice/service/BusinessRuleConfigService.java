package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetBusinessRuleConfigListDto;
import com.qsy.edifice.domain.dto.SaveBusinessRuleConfigDto;
import com.qsy.edifice.domain.vo.BusinessRuleConfigVo;
import com.qsy.edifice.domain.vo.BusinessRuleTemplateVo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BusinessRuleConfigService {
    Page<BusinessRuleConfigVo> list(GetBusinessRuleConfigListDto dto);
    BusinessRuleConfigVo detail(Long id);
    Long save(SaveBusinessRuleConfigDto dto, Long userId);
    void toggle(Long id, Integer enabled, Long userId);
    void delete(Long id);
    List<BusinessRuleConfigVo> getEnabledByBizType(String bizType);
    List<BusinessRuleTemplateVo> templates();
    boolean booleanValue(String bizType, String ruleKey, boolean defaultValue);
    BigDecimal numberValue(String bizType, String ruleKey, BigDecimal defaultValue);
    String stringValue(String bizType, String ruleKey, String defaultValue);
    Map<String, Object> jsonValue(String bizType, String ruleKey, Map<String, Object> defaultValue);
}
