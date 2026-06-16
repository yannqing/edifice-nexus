package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.ContractChangeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合同变更日志 Mapper
 */
@Mapper
public interface ContractChangeLogMapper extends BaseMapper<ContractChangeLog> {
}
