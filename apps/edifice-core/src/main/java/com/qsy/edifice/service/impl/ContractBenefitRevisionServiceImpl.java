package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.ReviseBenefitDto;
import com.qsy.edifice.domain.entity.Contract;
import com.qsy.edifice.domain.entity.ContractBenefitRevision;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ContractBenefitRevisionVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ContractBenefitRevisionMapper;
import com.qsy.edifice.mapper.ContractMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.ContractBenefitRevisionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 合同效益修正服务实现（v0.4）
 */
@Slf4j
@Service
public class ContractBenefitRevisionServiceImpl implements ContractBenefitRevisionService {

    private static final int BENEFIT_STATUS_PENDING = 0;
    private static final int BENEFIT_STATUS_FINAL = 1;

    @Resource
    private ContractBenefitRevisionMapper revisionMapper;

    @Resource
    private ContractMapper contractMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long revise(Long contractId, ReviseBenefitDto dto, Long operatorId) {
        if (contractId == null || dto == null || dto.getNewAmount() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "合同id / 新效益金额不能为空");
        }
        if (dto.getNewAmount().signum() < 0) {
            throw new BusinessException(ErrorType.BENEFIT_AMOUNT_INVALID);
        }

        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException(ErrorType.CONTRACT_NOT_FOUND);
        }
        if (contract.getBenefitStatus() != null
                && contract.getBenefitStatus() == BENEFIT_STATUS_FINAL) {
            throw new BusinessException(ErrorType.BENEFIT_FINALIZED);
        }

        BigDecimal oldAmount = contract.getBenefitAmount();
        BigDecimal newAmount = dto.getNewAmount();
        BigDecimal delta = oldAmount == null ? null : newAmount.subtract(oldAmount);
        boolean isFinal = Boolean.TRUE.equals(dto.getIsFinal());

        ContractBenefitRevision rev = ContractBenefitRevision.builder()
                .contractId(contractId)
                .oldAmount(oldAmount)
                .newAmount(newAmount)
                .deltaAmount(delta)
                .revisionReason(dto.getRevisionReason())
                .isFinal(isFinal ? BENEFIT_STATUS_FINAL : BENEFIT_STATUS_PENDING)
                .operatorId(operatorId)
                .build();
        revisionMapper.insert(rev);

        contract.setBenefitAmount(newAmount);
        if (isFinal) contract.setBenefitStatus(BENEFIT_STATUS_FINAL);
        contractMapper.updateById(contract);

        log.info("合同效益修正 contractId={} {} → {} delta={} isFinal={} operator={}",
                contractId, oldAmount, newAmount, delta, isFinal, operatorId);
        return rev.getRevisionId();
    }

    @Override
    public List<ContractBenefitRevisionVo> listByContract(Long contractId) {
        if (contractId == null) return Collections.emptyList();
        LambdaQueryWrapper<ContractBenefitRevision> w = new LambdaQueryWrapper<>();
        w.eq(ContractBenefitRevision::getContractId, contractId)
                .orderByDesc(ContractBenefitRevision::getCreatedTime);
        List<ContractBenefitRevision> list = revisionMapper.selectList(w);
        if (list.isEmpty()) return Collections.emptyList();

        Set<Long> opIds = list.stream()
                .map(ContractBenefitRevision::getOperatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = opIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(opIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));

        return list.stream().map(r -> {
            SysUser u = r.getOperatorId() == null ? null : userMap.get(r.getOperatorId());
            return ContractBenefitRevisionVo.builder()
                    .revisionId(r.getRevisionId())
                    .contractId(r.getContractId())
                    .oldAmount(r.getOldAmount())
                    .newAmount(r.getNewAmount())
                    .deltaAmount(r.getDeltaAmount())
                    .revisionReason(r.getRevisionReason())
                    .isFinal(r.getIsFinal())
                    .operatorId(r.getOperatorId())
                    .operatorName(u == null ? null
                            : (u.getRealName() != null ? u.getRealName() : u.getUsername()))
                    .createdTime(r.getCreatedTime())
                    .build();
        }).collect(Collectors.toList());
    }
}
