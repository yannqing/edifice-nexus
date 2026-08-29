package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.OutputValueDistribution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface OutputValueDistributionMapper extends BaseMapper<OutputValueDistribution> {

    @Select("""
            SELECT COALESCE(SUM(d.amount), 0)
            FROM output_value_distribution d
            INNER JOIN output_value ov ON ov.output_value_id = d.output_value_id
            WHERE d.source_distribution_id = #{sourceDistributionId}
              AND d.component_type = 1
              AND d.is_delete = 0
              AND ov.is_delete = 0
            """)
    BigDecimal sumAppliedBenefitAdjustment(
            @Param("sourceDistributionId") Long sourceDistributionId);
}
