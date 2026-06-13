package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.OutputValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface OutputValueMapper extends BaseMapper<OutputValue> {

    @Select("""
            SELECT *
            FROM output_value
            WHERE project_stage_id = #{projectStageId}
              AND is_delete = 0
            FOR UPDATE
            """)
    List<OutputValue> selectByProjectStageIdForUpdate(@Param("projectStageId") Long projectStageId);

    /**
     * 查询同项目已确认（status>=2：已审批/已发放）产值分配单中的最大阶段累计应得，
     * 作为本期产值的「上一次累计」基准。无历史单则返回 0。
     * 对应 v0.4 §4.1：本期产值 = 本阶段累计 - 同项目所有 status>=2 单的最大 stage_cumulative_amount。
     */
    @Select("""
            SELECT COALESCE(MAX(stage_cumulative_amount), 0)
            FROM output_value
            WHERE project_id = #{projectId}
              AND status >= 2
              AND is_delete = 0
            """)
    BigDecimal findMaxCumulativeByProject(@Param("projectId") Long projectId);
}
