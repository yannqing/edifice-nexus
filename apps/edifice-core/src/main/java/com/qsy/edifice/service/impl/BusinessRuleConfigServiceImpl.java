package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsy.edifice.domain.dto.GetBusinessRuleConfigListDto;
import com.qsy.edifice.domain.dto.SaveBusinessRuleConfigDto;
import com.qsy.edifice.domain.entity.BusinessRuleConfig;
import com.qsy.edifice.domain.vo.BusinessRuleConfigVo;
import com.qsy.edifice.domain.vo.BusinessRuleTemplateVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.BusinessRuleConfigMapper;
import com.qsy.edifice.service.BusinessRuleConfigService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class BusinessRuleConfigServiceImpl implements BusinessRuleConfigService {

    private static final Set<String> VALUE_TYPES = Set.of("boolean", "number", "string", "json");
    private static final List<BusinessRuleTemplateVo> RULE_TEMPLATES = List.of(
            // 产值分配 (output)
            template("output", "require_stage_inspection_passed", "产值分配前必须验工通过", "boolean", "true",
                    "只有验工单通过的项目阶段才允许新建产值分配单。"),
            template("output", "prevent_duplicate_confirmed_stage", "禁止同阶段重复确认产值", "boolean", "true",
                    "同一项目阶段已有确认中或已确认分配单时，不允许重复确认。"),
            template("output", "allow_negative_output", "允许负产值", "boolean", "false",
                    "控制产值计算结果为负时是否允许继续提交。"),
            template("output", "block_after_project_archive", "项目归档后禁止产值分配", "boolean", "true",
                    "项目归档后禁止继续发起产值分配。"),
            // 验工单 (inspection)
            template("inspection", "require_materials", "验工材料必填", "boolean", "true",
                    "发起验工时必须上传验收材料。"),
            template("inspection", "block_after_project_archive", "项目归档后禁止发起验工", "boolean", "true",
                    "项目归档后禁止继续发起验工。"),
            // 项目文件 (file)
            template("file", "require_approval", "项目文件上传必须审批", "boolean", "true",
                    "项目文件上传后进入审批流程，通过后才归档为正式文件。"),
            template("file", "allow_image_upload", "允许上传图片文件", "boolean", "true",
                    "控制项目文件上传入口是否允许图片类型。"),
            template("file", "block_after_project_archive", "项目归档后禁止上传文件", "boolean", "true",
                    "项目归档后禁止继续上传项目文件。")
    );
    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE = new TypeReference<>() {};

    @Resource
    private BusinessRuleConfigMapper businessRuleConfigMapper;

    @Resource
    private ObjectMapper objectMapper;

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
        BusinessRuleTemplateVo template = findTemplate(dto.getBizType(), dto.getRuleKey())
                .orElseThrow(() -> new BusinessException(ErrorType.ARGS_INVALID, "不支持的业务规则编码，请从规则模板中选择"));
        BusinessRuleConfig entity = dto.getRuleConfigId() == null ? new BusinessRuleConfig() : find(dto.getRuleConfigId());
        if (dto.getRuleConfigId() == null) {
            Long duplicate = businessRuleConfigMapper.selectCount(new LambdaQueryWrapper<BusinessRuleConfig>()
                    .eq(BusinessRuleConfig::getBizType, dto.getBizType())
                    .eq(BusinessRuleConfig::getRuleKey, dto.getRuleKey().trim()));
            if (duplicate != null && duplicate > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "该业务规则已存在");
            }
        } else if (!entity.getBizType().equals(dto.getBizType()) || !entity.getRuleKey().equals(dto.getRuleKey().trim())) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "不允许修改规则业务类型和规则编码");
        }
        entity.setBizType(dto.getBizType());
        entity.setRuleKey(dto.getRuleKey().trim());
        entity.setRuleName(template.getRuleName());
        entity.setRuleValue(dto.getRuleValue() == null ? "" : dto.getRuleValue().trim());
        entity.setValueType(template.getValueType());
        entity.setEnabled(dto.getEnabled() == null || dto.getEnabled() == 1 ? 1 : 0);
        entity.setDescription(StringUtils.hasText(dto.getDescription()) ? dto.getDescription().trim() : template.getDescription());
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
    public List<BusinessRuleTemplateVo> templates() {
        return RULE_TEMPLATES;
    }

    @Override
    public boolean booleanValue(String bizType, String ruleKey, boolean defaultValue) {
        BusinessRuleConfig rule = enabledRule(bizType, ruleKey);
        if (rule == null || !StringUtils.hasText(rule.getRuleValue())) return defaultValue;
        return Boolean.parseBoolean(rule.getRuleValue().trim());
    }

    @Override
    public BigDecimal numberValue(String bizType, String ruleKey, BigDecimal defaultValue) {
        BusinessRuleConfig rule = enabledRule(bizType, ruleKey);
        if (rule == null || !StringUtils.hasText(rule.getRuleValue())) return defaultValue;
        try {
            return new BigDecimal(rule.getRuleValue().trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    @Override
    public String stringValue(String bizType, String ruleKey, String defaultValue) {
        BusinessRuleConfig rule = enabledRule(bizType, ruleKey);
        if (rule == null || rule.getRuleValue() == null) return defaultValue;
        return rule.getRuleValue();
    }

    @Override
    public Map<String, Object> jsonValue(String bizType, String ruleKey, Map<String, Object> defaultValue) {
        BusinessRuleConfig rule = enabledRule(bizType, ruleKey);
        if (rule == null || !StringUtils.hasText(rule.getRuleValue())) return defaultValue;
        try {
            return objectMapper.readValue(rule.getRuleValue(), JSON_MAP_TYPE);
        } catch (Exception exception) {
            return defaultValue;
        }
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
                || !StringUtils.hasText(dto.getValueType())) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "业务类型、规则编码和值类型不能为空");
        }
        if (ApprovalBizType.fromExt(dto.getBizType()) == null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "不支持的业务类型");
        }
        if (!VALUE_TYPES.contains(dto.getValueType())) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "规则值类型不支持");
        }
        BusinessRuleTemplateVo template = findTemplate(dto.getBizType(), dto.getRuleKey())
                .orElseThrow(() -> new BusinessException(ErrorType.ARGS_INVALID, "不支持的业务规则编码，请从规则模板中选择"));
        if (!template.getValueType().equals(dto.getValueType())) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "规则值类型与模板不一致");
        }
        validateRuleValue(dto.getRuleValue(), template);
    }

    private void validateRuleValue(String value, BusinessRuleTemplateVo template) {
        if ("boolean".equals(template.getValueType())) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "布尔规则值只能为 true 或 false");
            }
            return;
        }
        if ("number".equals(template.getValueType())) {
            try {
                new BigDecimal(value);
                return;
            } catch (Exception exception) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "数字规则值不合法");
            }
        }
        if ("json".equals(template.getValueType())) {
            try {
                objectMapper.readValue(StringUtils.hasText(value) ? value : "{}", JSON_MAP_TYPE);
                return;
            } catch (Exception exception) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "JSON规则值不合法");
            }
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

    private Optional<BusinessRuleTemplateVo> findTemplate(String bizType, String ruleKey) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(ruleKey)) return Optional.empty();
        return RULE_TEMPLATES.stream()
                .filter(template -> bizType.equals(template.getBizType()) && ruleKey.trim().equals(template.getRuleKey()))
                .findFirst();
    }

    private static BusinessRuleTemplateVo template(String bizType,
                                                  String ruleKey,
                                                  String ruleName,
                                                  String valueType,
                                                  String defaultValue,
                                                  String description) {
        ApprovalBizType approvalBizType = ApprovalBizType.fromExt(bizType);
        return BusinessRuleTemplateVo.builder()
                .bizType(bizType)
                .bizTypeLabel(approvalBizType == null ? bizType : approvalBizType.getLabel())
                .ruleKey(ruleKey)
                .ruleName(ruleName)
                .valueType(valueType)
                .defaultValue(defaultValue)
                .description(description)
                .build();
    }
}
