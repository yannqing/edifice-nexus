package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.Contract;

/**
 * 合同服务接口
 */
public interface ContractService {

    /**
     * 根据合同id查询合同
     * @param contractId 合同id
     * @return 合同信息
     */
    Contract getContractById(Long contractId);

    /**
     * 根据合同编码查询合同
     * @param contractCode 合同编码
     * @return 合同信息
     */
    Contract getContractByCode(String contractCode);

    /**
     * 分页查询合同列表
     * @param current 当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<Contract> getContractPage(Integer current, Integer pageSize);

    /**
     * 保存合同
     * @param contract 合同信息
     * @return 是否成功
     */
    boolean saveContract(Contract contract);

    /**
     * 更新合同
     * @param contract 合同信息
     * @return 是否成功
     */
    boolean updateContract(Contract contract);

    /**
     * 删除合同
     * @param contractId 合同id
     * @return 是否成功
     */
    boolean deleteContract(Long contractId);
}
