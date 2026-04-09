package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.ProjectFiles;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目文件 Mapper 接口
 */
@Mapper
public interface ProjectFilesMapper extends BaseMapper<ProjectFiles> {

    /**
     * 根据项目id查询项目文件列表
     * @param projectId 项目id
     * @return 项目文件列表
     */
    List<ProjectFiles> selectByProjectId(@Param("projectId") String projectId);

    /**
     * 根据项目阶段id查询项目文件列表
     * @param projectStageId 项目阶段id
     * @return 项目文件列表
     */
    List<ProjectFiles> selectByProjectStageId(@Param("projectStageId") Long projectStageId);
}
