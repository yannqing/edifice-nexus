package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.Contract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 合同 Mapper 接口
 */
@Mapper
public interface ContractMapper extends BaseMapper<Contract> {

    /**
     * 根据合同编码查询合同
     * @param contractCode 合同编码
     * @return 合同信息
     */
    Contract selectByContractCode(@Param("contractCode") String contractCode);
}
