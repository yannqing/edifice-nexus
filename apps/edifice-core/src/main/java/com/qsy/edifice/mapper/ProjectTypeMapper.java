package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.ProjectType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目类型 Mapper 接口
 */
@Mapper
public interface ProjectTypeMapper extends BaseMapper<ProjectType> {

    /**
     * 根据项目类型编码查询项目类型
     * @param projectCode 项目类型编码
     * @return 项目类型信息
     */
    ProjectType selectByProjectCode(@Param("projectCode") String projectCode);

    /**
     * 查询所有启用的项目类型
     * @return 项目类型列表
     */
    List<ProjectType> selectAllEnabled();
}
