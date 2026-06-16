package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetContractListDto;
import com.qsy.edifice.domain.dto.UpdateContractDto;
import com.qsy.edifice.domain.entity.Contract;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.vo.ContractListVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ContractMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.service.ContractService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 合同服务实现类
 */
@Slf4j
@Service
public class ContractServiceImpl implements ContractService {

    @Resource
    private ContractMapper contractMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Override
    public Contract getContractById(Long contractId) {
        return contractMapper.selectById(contractId);
    }

    @Override
    public Contract getContractByProjectId(Long projectId) {
        LambdaQueryWrapper<Contract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Contract::getProjectId, projectId)
               .orderByDesc(Contract::getCreatedTime)
               .last("LIMIT 1");
        return contractMapper.selectOne(wrapper);
    }

    @Override
    public Contract getContractByCode(String contractCode) {
        return contractMapper.selectByContractCode(contractCode);
    }

    @Override
    public Page<Contract> getContractPage(Integer current, Integer pageSize) {
        return contractMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public Page<ContractListVo> getContractList(GetContractListDto dto) {
        if (dto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        Integer current = dto.getCurrent() == null || dto.getCurrent() < 1 ? 1 : dto.getCurrent();
        Integer pageSize = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10 : dto.getPageSize();

        LambdaQueryWrapper<Contract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getProjectId() != null, Contract::getProjectId, dto.getProjectId());
        wrapper.eq(dto.getContractType() != null, Contract::getContractType, dto.getContractType());

        if (StringUtils.hasText(dto.getKeywords())) {
            String keyword = dto.getKeywords().trim();
            List<Long> projectIds = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                            .and(w -> w.like(Project::getProjectName, keyword)
                                    .or()
                                    .like(Project::getProjectCode, keyword)))
                    .stream()
                    .map(Project::getProjectId)
                    .collect(Collectors.toList());
            wrapper.and(w -> {
                w.like(Contract::getContractName, keyword)
                        .or()
                        .like(Contract::getContractCode, keyword);
                if (!projectIds.isEmpty()) {
                    w.or().in(Contract::getProjectId, projectIds);
                }
            });
        }

        wrapper.orderByDesc(Contract::getUpdatedTime).orderByDesc(Contract::getCreatedTime);
        Page<Contract> page = contractMapper.selectPage(new Page<>(current, pageSize), wrapper);
        Page<ContractListVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::convertToListVo).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public ContractListVo getContractDetail(Long contractId) {
        if (contractId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "合同ID不能为空");
        }
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException(ErrorType.CONTRACT_NOT_FOUND);
        }
        return convertToListVo(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContractInfo(UpdateContractDto dto) {
        if (dto == null || dto.getContractId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "合同ID不能为空");
        }
        Contract contract = contractMapper.selectById(dto.getContractId());
        if (contract == null) {
            throw new BusinessException(ErrorType.CONTRACT_NOT_FOUND);
        }
        validateMoney(dto.getContractAmount(), "合同金额", true);
        validateMoney(dto.getBaseAmount(), "基本收费金额", false);
        if (dto.getContractType() != null && dto.getContractType() != 0 && dto.getContractType() != 1) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "合同类型不正确");
        }
        if (dto.getBenefitAmount() != null
                && (contract.getBenefitAmount() == null
                || dto.getBenefitAmount().compareTo(contract.getBenefitAmount()) != 0)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "预计效益金额请通过效益修正流程调整");
        }

        if (StringUtils.hasText(dto.getContractName())) contract.setContractName(dto.getContractName().trim());
        if (StringUtils.hasText(dto.getContractCode())) contract.setContractCode(dto.getContractCode().trim());
        if (dto.getContractType() != null) contract.setContractType(dto.getContractType());
        if (dto.getContractAmount() != null) contract.setContractAmount(dto.getContractAmount());
        if (dto.getBaseAmount() != null) contract.setBaseAmount(dto.getBaseAmount());
        if (dto.getBenefitRules() != null) contract.setBenefitRules(dto.getBenefitRules());
        if (dto.getSigningDate() != null) contract.setSigningDate(dto.getSigningDate());
        if (dto.getPreStartDate() != null) contract.setPreStartDate(dto.getPreStartDate());
        if (dto.getPreEndDate() != null) contract.setPreEndDate(dto.getPreEndDate());

        contractMapper.updateById(contract);
    }

    @Override
    public boolean saveContract(Contract contract) {
        return contractMapper.insert(contract) > 0;
    }

    @Override
    public boolean updateContract(Contract contract) {
        return contractMapper.updateById(contract) > 0;
    }

    @Override
    public boolean deleteContract(Long contractId) {
        return contractMapper.deleteById(contractId) > 0;
    }

    private void validateMoney(BigDecimal amount, String fieldName, boolean mustPositive) {
        if (amount == null) {
            return;
        }
        int signum = amount.signum();
        if ((mustPositive && signum <= 0) || (!mustPositive && signum < 0)) {
            throw new BusinessException(ErrorType.ARGS_INVALID,
                    fieldName + (mustPositive ? "必须大于0" : "不能小于0"));
        }
    }

    private ContractListVo convertToListVo(Contract contract) {
        ContractListVo vo = new ContractListVo();
        BeanUtils.copyProperties(contract, vo);
        if (contract.getProjectId() != null) {
            Project project = projectMapper.selectById(contract.getProjectId());
            if (project != null) {
                vo.setProjectName(project.getProjectName());
                vo.setProjectCode(project.getProjectCode());
                vo.setProjectStatus(project.getProjectStatus());
            }
        }
        if (Objects.equals(contract.getContractType(), 0) && vo.getBenefitAmount() == null) {
            vo.setBenefitAmount(BigDecimal.ZERO);
        }
        return vo;
    }
}
