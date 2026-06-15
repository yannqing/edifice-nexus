package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.OutputValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
