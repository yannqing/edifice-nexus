package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.ReviseBenefitDto;
import com.qsy.edifice.domain.vo.ContractBenefitRevisionVo;

import java.util.List;

/**
 * 合同效益修正服务（v0.4）
 *
 * 业务约束：
 * - {@code contract.benefit_status=1} 后不允许再修正
 * - 修正写入 {@code contract_benefit_revision} 历史表，并同步更新 {@code contract.benefit_amount}
 * - 不立即重算已生成的产值分配单；下次创建产值分配单时自动用最新值
 */
public interface ContractBenefitRevisionService {

    /**
     * 提交一次效益修正。
     *
     * @return 新建的 revisionId
     */
    Long revise(Long contractId, ReviseBenefitDto dto, Long operatorId);

    /**
     * 查询某合同的修正历史（按时间倒序）。
     */
    List<ContractBenefitRevisionVo> listByContract(Long contractId);
}
