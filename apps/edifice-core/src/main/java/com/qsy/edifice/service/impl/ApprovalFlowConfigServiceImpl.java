package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetApprovalFlowConfigListDto;
import com.qsy.edifice.domain.dto.SaveApprovalFlowConfigDto;
import com.qsy.edifice.domain.dto.SaveApprovalFlowNodeDto;
import com.qsy.edifice.domain.entity.ApprovalFlowConfig;
import com.qsy.edifice.domain.entity.ApprovalFlowNode;
import com.qsy.edifice.domain.vo.ApprovalFlowConfigVo;
import com.qsy.edifice.domain.vo.ApprovalFlowNodeVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ApprovalFlowConfigMapper;
import com.qsy.edifice.mapper.ApprovalFlowNodeMapper;
import com.qsy.edifice.service.ApprovalFlowConfigService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ApprovalFlowConfigServiceImpl implements ApprovalFlowConfigService {

    private static final Set<String> APPROVER_SOURCE_TYPES = Set.of("user", "role", "position", "starter_select");

    @Resource
    private ApprovalFlowConfigMapper approvalFlowConfigMapper;

    @Resource
    private ApprovalFlowNodeMapper approvalFlowNodeMapper;

    @Override
    public Page<ApprovalFlowConfigVo> list(GetApprovalFlowConfigListDto dto) {
        int current = dto != null && dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        int pageSize = dto != null && dto.getPageSize() != null && dto.getPageSize() > 0
                ? Math.min(dto.getPageSize(), 100)
                : 10;
        LambdaQueryWrapper<ApprovalFlowConfig> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (StringUtils.hasText(dto.getBizType())) {
                wrapper.eq(ApprovalFlowConfig::getBizType, dto.getBizType());
            }
            if (StringUtils.hasText(dto.getKeyword())) {
                wrapper.and(w -> w.like(ApprovalFlowConfig::getFlowName, dto.getKeyword())
                        .or()
                        .like(ApprovalFlowConfig::getRemark, dto.getKeyword()));
            }
            if (dto.getEnabled() != null) {
                wrapper.eq(ApprovalFlowConfig::getEnabled, dto.getEnabled());
            }
        }
        wrapper.orderByAsc(ApprovalFlowConfig::getBizType)
                .orderByDesc(ApprovalFlowConfig::getUpdatedTime);
        Page<ApprovalFlowConfig> page = approvalFlowConfigMapper.selectPage(new Page<>(current, pageSize), wrapper);
        Page<ApprovalFlowConfigVo> result = new Page<>(current, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVoWithNodes).toList());
        return result;
    }

    @Override
    public ApprovalFlowConfigVo detail(Long id) {
        return toVoWithNodes(find(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SaveApprovalFlowConfigDto dto, Long userId) {
        validate(dto);
        ApprovalFlowConfig entity = dto.getFlowConfigId() == null ? new ApprovalFlowConfig() : find(dto.getFlowConfigId());
        if (dto.getFlowConfigId() == null) {
            Long duplicate = approvalFlowConfigMapper.selectCount(new LambdaQueryWrapper<ApprovalFlowConfig>()
                    .eq(ApprovalFlowConfig::getBizType, dto.getBizType()));
            if (duplicate != null && duplicate > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "该业务类型已存在流程配置");
            }
            entity.setCreatedBy(userId);
        } else if (!Objects.equals(entity.getBizType(), dto.getBizType())) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "不允许修改流程配置业务类型");
        }
        entity.setBizType(dto.getBizType());
        entity.setFlowName(dto.getFlowName().trim());
        entity.setEnabled(normalizeSwitch(dto.getEnabled(), 1));
        entity.setAllowWithdraw(normalizeSwitch(dto.getAllowWithdraw(), 1));
        entity.setAllowUrge(normalizeSwitch(dto.getAllowUrge(), 1));
        entity.setAllowCc(normalizeSwitch(dto.getAllowCc(), 1));
        entity.setAllowStarterSelectNext(normalizeSwitch(dto.getAllowStarterSelectNext(), 1));
        entity.setVersion(dto.getVersion() != null && dto.getVersion() > 0 ? dto.getVersion() : 1);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        entity.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark().trim() : null);
        entity.setUpdatedBy(userId);
        if (entity.getFlowConfigId() == null) {
            approvalFlowConfigMapper.insert(entity);
        } else {
            approvalFlowConfigMapper.updateById(entity);
            approvalFlowNodeMapper.delete(new LambdaQueryWrapper<ApprovalFlowNode>()
                    .eq(ApprovalFlowNode::getFlowConfigId, entity.getFlowConfigId()));
        }
        for (SaveApprovalFlowNodeDto nodeDto : dto.getNodes()) {
            approvalFlowNodeMapper.insert(ApprovalFlowNode.builder()
                    .flowConfigId(entity.getFlowConfigId())
                    .nodeOrder(nodeDto.getNodeOrder())
                    .nodeName(nodeDto.getNodeName().trim())
                    .approverSourceType(nodeDto.getApproverSourceType())
                    .approverSourceId(StringUtils.hasText(nodeDto.getApproverSourceId())
                            ? nodeDto.getApproverSourceId().trim()
                            : null)
                    .allowTerminate(normalizeSwitch(nodeDto.getAllowTerminate(), 0))
                    .requiredNode(normalizeSwitch(nodeDto.getRequiredNode(), 1))
                    .build());
        }
        return entity.getFlowConfigId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, Integer enabled, Long userId) {
        ApprovalFlowConfig entity = find(id);
        entity.setEnabled(normalizeSwitch(enabled, 1));
        entity.setUpdatedBy(userId);
        approvalFlowConfigMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ApprovalFlowConfig entity = find(id);
        approvalFlowNodeMapper.delete(new LambdaQueryWrapper<ApprovalFlowNode>()
                .eq(ApprovalFlowNode::getFlowConfigId, entity.getFlowConfigId()));
        approvalFlowConfigMapper.deleteById(entity.getFlowConfigId());
    }

    @Override
    public ApprovalFlowConfigVo getEnabledByBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) return null;
        ApprovalFlowConfig entity = approvalFlowConfigMapper.selectOne(new LambdaQueryWrapper<ApprovalFlowConfig>()
                .eq(ApprovalFlowConfig::getBizType, bizType)
                .eq(ApprovalFlowConfig::getEnabled, 1)
                .last("LIMIT 1"));
        return entity == null ? null : toVoWithNodes(entity);
    }

    private ApprovalFlowConfig find(Long id) {
        if (id == null) throw new BusinessException(ErrorType.ARGS_NOT_NULL, "流程配置ID不能为空");
        ApprovalFlowConfig entity = approvalFlowConfigMapper.selectById(id);
        if (entity == null) throw new BusinessException(ErrorType.OPERATION_FAILED, "流程配置不存在");
        return entity;
    }

    private void validate(SaveApprovalFlowConfigDto dto) {
        if (dto == null || !StringUtils.hasText(dto.getBizType()) || !StringUtils.hasText(dto.getFlowName())) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "业务类型和流程名称不能为空");
        }
        if (ApprovalBizType.fromExt(dto.getBizType()) == null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "不支持的业务类型");
        }
        if (dto.getNodes() == null || dto.getNodes().isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "至少需要配置一个审批节点");
        }
        for (SaveApprovalFlowNodeDto node : dto.getNodes()) {
            if (node.getNodeOrder() == null || node.getNodeOrder() <= 0
                    || !StringUtils.hasText(node.getNodeName())
                    || !StringUtils.hasText(node.getApproverSourceType())) {
                throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批节点信息不完整");
            }
            if (!APPROVER_SOURCE_TYPES.contains(node.getApproverSourceType())) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "审批人来源类型不支持");
            }
            if (!"starter_select".equals(node.getApproverSourceType())
                    && !StringUtils.hasText(node.getApproverSourceId())) {
                throw new BusinessException(ErrorType.ARGS_NOT_NULL, "指定用户/角色/岗位时来源ID不能为空");
            }
        }
    }

    private ApprovalFlowConfigVo toVoWithNodes(ApprovalFlowConfig entity) {
        List<ApprovalFlowNodeVo> nodes = approvalFlowNodeMapper.selectList(new LambdaQueryWrapper<ApprovalFlowNode>()
                        .eq(ApprovalFlowNode::getFlowConfigId, entity.getFlowConfigId())
                        .orderByAsc(ApprovalFlowNode::getNodeOrder))
                .stream()
                .map(this::toNodeVo)
                .sorted(Comparator.comparing(ApprovalFlowNodeVo::getNodeOrder))
                .toList();
        ApprovalBizType bizType = ApprovalBizType.fromExt(entity.getBizType());
        return ApprovalFlowConfigVo.builder()
                .flowConfigId(entity.getFlowConfigId())
                .bizType(entity.getBizType())
                .bizTypeLabel(bizType == null ? entity.getBizType() : bizType.getLabel())
                .flowName(entity.getFlowName())
                .enabled(entity.getEnabled())
                .allowWithdraw(entity.getAllowWithdraw())
                .allowUrge(entity.getAllowUrge())
                .allowCc(entity.getAllowCc())
                .allowStarterSelectNext(entity.getAllowStarterSelectNext())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdTime(entity.getCreatedTime())
                .updatedTime(entity.getUpdatedTime())
                .nodes(nodes)
                .build();
    }

    private ApprovalFlowNodeVo toNodeVo(ApprovalFlowNode node) {
        return ApprovalFlowNodeVo.builder()
                .flowNodeId(node.getFlowNodeId())
                .flowConfigId(node.getFlowConfigId())
                .nodeOrder(node.getNodeOrder())
                .nodeName(node.getNodeName())
                .approverSourceType(node.getApproverSourceType())
                .approverSourceId(node.getApproverSourceId())
                .allowTerminate(node.getAllowTerminate())
                .requiredNode(node.getRequiredNode())
                .createdTime(node.getCreatedTime())
                .updatedTime(node.getUpdatedTime())
                .build();
    }

    private int normalizeSwitch(Integer value, int defaultValue) {
        return value == null ? defaultValue : value == 1 ? 1 : 0;
    }
}
