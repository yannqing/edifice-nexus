package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.ProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目成员 Mapper 接口
 */
@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    /**
     * 根据项目id查询项目成员列表
     * @param projectId 项目id
     * @return 项目成员列表
     */
    List<ProjectMember> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据用户id查询项目成员列表
     * @param userId 用户id
     * @return 项目成员列表
     */
    List<ProjectMember> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据项目id和用户id查询项目成员
     * @param projectId 项目id
     * @param userId 用户id
     * @return 项目成员信息
     */
    ProjectMember selectByProjectIdAndUserId(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
