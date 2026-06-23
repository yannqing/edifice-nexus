package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.OutputValueAdjustmentDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface OutputValueAdjustmentDetailMapper extends BaseMapper<OutputValueAdjustmentDetail> {

    @Select("""
            SELECT COALESCE(SUM(d.adjustment_amount), 0)
            FROM output_value_adjustment_detail d
            INNER JOIN output_value ov ON ov.output_value_id = d.output_value_id
            WHERE d.source_output_value_id = #{sourceOutputValueId}
              AND d.is_delete = 0
              AND ov.is_delete = 0
              AND ov.status >= 2
            """)
    BigDecimal sumApprovedAdjustmentBySource(@Param("sourceOutputValueId") Long sourceOutputValueId);
}
