package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetContractListDto;
import com.qsy.edifice.domain.dto.UpdateContractDto;
import com.qsy.edifice.domain.entity.Contract;
import com.qsy.edifice.domain.vo.ContractChangeLogVo;
import com.qsy.edifice.domain.vo.ContractListVo;

import java.util.List;

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
     * 根据项目id查询合同
     * @param projectId 项目id
     * @return 合同信息
     */
    Contract getContractByProjectId(Long projectId);

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
     * 合同管理分页列表
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<ContractListVo> getContractList(GetContractListDto dto);

    /**
     * 合同管理详情
     * @param contractId 合同id
     * @return 合同详情
     */
    ContractListVo getContractDetail(Long contractId);

    /**
     * 合同管理更新
     * @param dto 更新参数
     */
    void updateContractInfo(UpdateContractDto dto, Long operatorId);

    /**
     * 合同字段变更日志
     * @param contractId 合同id
     * @return 变更日志
     */
    List<ContractChangeLogVo> getContractChangeLogs(Long contractId);

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
