package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.ProjectStageTemplate;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目阶段模版 Mapper 接口
 */
@Mapper
public interface ProjectStageTemplateMapper extends BaseMapper<ProjectStageTemplate> {

    /**
     * 查询所有启用的阶段模版
     * @return 阶段模版列表
     */
    List<ProjectStageTemplate> selectAllEnabled();
}
