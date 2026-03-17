package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qsy.edifice.domain.entity.Contract;
import com.qsy.edifice.mapper.ContractMapper;
import com.qsy.edifice.service.ContractService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 合同服务实现类
 */
@Slf4j
@Service
public class ContractServiceImpl extends ServiceImpl<ContractMapper,Contract> implements ContractService {

    @Resource
    private ContractMapper contractMapper;

    @Override
    public Contract getContractById(Long contractId) {
        return contractMapper.selectById(contractId);
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
}
