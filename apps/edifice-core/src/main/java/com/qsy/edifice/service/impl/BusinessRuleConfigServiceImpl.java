package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetBusinessRuleConfigListDto;
import com.qsy.edifice.domain.dto.SaveBusinessRuleConfigDto;
import com.qsy.edifice.domain.entity.BusinessRuleConfig;
import com.qsy.edifice.domain.vo.BusinessRuleConfigVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.BusinessRuleConfigMapper;
import com.qsy.edifice.service.BusinessRuleConfigService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
public class BusinessRuleConfigServiceImpl implements BusinessRuleConfigService {

    private static final Set<String> VALUE_TYPES = Set.of("boolean", "number", "string", "json");

    @Resource
    private BusinessRuleConfigMapper businessRuleConfigMapper;

    @Override
    public Page<BusinessRuleConfigVo> list(GetBusinessRuleConfigListDto dto) {
        int current = dto != null && dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        int pageSize = dto != null && dto.getPageSize() != null && dto.getPageSize() > 0
                ? Math.min(dto.getPageSize(), 100)
                : 10;
        LambdaQueryWrapper<BusinessRuleConfig> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (StringUtils.hasText(dto.getBizType())) {
                wrapper.eq(BusinessRuleConfig::getBizType, dto.getBizType());
            }
            if (StringUtils.hasText(dto.getKeyword())) {
                wrapper.and(w -> w.like(BusinessRuleConfig::getRuleName, dto.getKeyword())
                        .or()
                        .like(BusinessRuleConfig::getRuleKey, dto.getKeyword())
                        .or()
                        .like(BusinessRuleConfig::getDescription, dto.getKeyword()));
            }
            if (dto.getEnabled() != null) {
                wrapper.eq(BusinessRuleConfig::getEnabled, dto.getEnabled());
            }
        }
        wrapper.orderByAsc(BusinessRuleConfig::getBizType)
                .orderByAsc(BusinessRuleConfig::getRuleKey);
        Page<BusinessRuleConfig> page = businessRuleConfigMapper.selectPage(new Page<>(current, pageSize), wrapper);
        Page<BusinessRuleConfigVo> result = new Page<>(current, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVo).toList());
        return result;
    }

    @Override
    public BusinessRuleConfigVo detail(Long id) {
        return toVo(find(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SaveBusinessRuleConfigDto dto, Long userId) {
        validate(dto);
        BusinessRuleConfig entity = dto.getRuleConfigId() == null ? new BusinessRuleConfig() : find(dto.getRuleConfigId());
        if (dto.getRuleConfigId() == null) {
            Long duplicate = businessRuleConfigMapper.selectCount(new LambdaQueryWrapper<BusinessRuleConfig>()
                    .eq(BusinessRuleConfig::getBizType, dto.getBizType())
                    .eq(BusinessRuleConfig::getRuleKey, dto.getRuleKey()));
            if (duplicate != null && duplicate > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "该业务规则已存在");
            }
        } else if (!entity.getBizType().equals(dto.getBizType()) || !entity.getRuleKey().equals(dto.getRuleKey())) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "不允许修改规则业务类型和规则编码");
        }
        entity.setBizType(dto.getBizType());
        entity.setRuleKey(dto.getRuleKey().trim());
        entity.setRuleName(dto.getRuleName().trim());
        entity.setRuleValue(dto.getRuleValue() == null ? "" : dto.getRuleValue().trim());
        entity.setValueType(dto.getValueType());
        entity.setEnabled(dto.getEnabled() == null || dto.getEnabled() == 1 ? 1 : 0);
        entity.setDescription(StringUtils.hasText(dto.getDescription()) ? dto.getDescription().trim() : null);
        entity.setUpdatedBy(userId);
        if (entity.getRuleConfigId() == null) {
            businessRuleConfigMapper.insert(entity);
        } else {
            businessRuleConfigMapper.updateById(entity);
        }
        return entity.getRuleConfigId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, Integer enabled, Long userId) {
        BusinessRuleConfig entity = find(id);
        entity.setEnabled(enabled != null && enabled == 0 ? 0 : 1);
        entity.setUpdatedBy(userId);
        businessRuleConfigMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        businessRuleConfigMapper.deleteById(find(id).getRuleConfigId());
    }

    @Override
    public List<BusinessRuleConfigVo> getEnabledByBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) return List.of();
        return businessRuleConfigMapper.selectList(new LambdaQueryWrapper<BusinessRuleConfig>()
                        .eq(BusinessRuleConfig::getBizType, bizType)
                        .eq(BusinessRuleConfig::getEnabled, 1)
                        .orderByAsc(BusinessRuleConfig::getRuleKey))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public boolean booleanValue(String bizType, String ruleKey, boolean defaultValue) {
        BusinessRuleConfig rule = enabledRule(bizType, ruleKey);
        if (rule == null || !StringUtils.hasText(rule.getRuleValue())) return defaultValue;
        return Boolean.parseBoolean(rule.getRuleValue().trim());
    }

    @Override
    public String stringValue(String bizType, String ruleKey, String defaultValue) {
        BusinessRuleConfig rule = enabledRule(bizType, ruleKey);
        if (rule == null || rule.getRuleValue() == null) return defaultValue;
        return rule.getRuleValue();
    }

    private BusinessRuleConfig enabledRule(String bizType, String ruleKey) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(ruleKey)) return null;
        return businessRuleConfigMapper.selectOne(new LambdaQueryWrapper<BusinessRuleConfig>()
                .eq(BusinessRuleConfig::getBizType, bizType)
                .eq(BusinessRuleConfig::getRuleKey, ruleKey)
                .eq(BusinessRuleConfig::getEnabled, 1)
                .last("LIMIT 1"));
    }

    private BusinessRuleConfig find(Long id) {
        if (id == null) throw new BusinessException(ErrorType.ARGS_NOT_NULL, "规则配置ID不能为空");
        BusinessRuleConfig entity = businessRuleConfigMapper.selectById(id);
        if (entity == null) throw new BusinessException(ErrorType.OPERATION_FAILED, "业务规则不存在");
        return entity;
    }

    private void validate(SaveBusinessRuleConfigDto dto) {
        if (dto == null || !StringUtils.hasText(dto.getBizType())
                || !StringUtils.hasText(dto.getRuleKey())
                || !StringUtils.hasText(dto.getRuleName())
                || !StringUtils.hasText(dto.getValueType())) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "业务类型、规则编码、规则名称和值类型不能为空");
        }
        if (ApprovalBizType.fromExt(dto.getBizType()) == null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "不支持的业务类型");
        }
        if (!VALUE_TYPES.contains(dto.getValueType())) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "规则值类型不支持");
        }
    }

    private BusinessRuleConfigVo toVo(BusinessRuleConfig entity) {
        ApprovalBizType bizType = ApprovalBizType.fromExt(entity.getBizType());
        return BusinessRuleConfigVo.builder()
                .ruleConfigId(entity.getRuleConfigId())
                .bizType(entity.getBizType())
                .bizTypeLabel(bizType == null ? entity.getBizType() : bizType.getLabel())
                .ruleKey(entity.getRuleKey())
                .ruleName(entity.getRuleName())
                .ruleValue(entity.getRuleValue())
                .valueType(entity.getValueType())
                .enabled(entity.getEnabled())
                .description(entity.getDescription())
                .updatedBy(entity.getUpdatedBy())
                .createdTime(entity.getCreatedTime())
                .updatedTime(entity.getUpdatedTime())
                .build();
    }
}
