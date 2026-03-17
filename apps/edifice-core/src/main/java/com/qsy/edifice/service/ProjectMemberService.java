package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qsy.edifice.domain.entity.ProjectMember;

import java.util.List;

/**
 * 项目成员服务接口
 */
public interface ProjectMemberService extends IService<ProjectMember> {

    /**
     * 根据项目成员id查询项目成员
     * @param projectMemberId 项目成员id
     * @return 项目成员信息
     */
    ProjectMember getProjectMemberById(Long projectMemberId);

    /**
     * 根据项目id查询项目成员列表
     * @param projectId 项目id
     * @return 项目成员列表
     */
    List<ProjectMember> getProjectMembersByProjectId(Long projectId);

    /**
     * 根据用户id查询项目成员列表
     * @param userId 用户id
     * @return 项目成员列表
     */
    List<ProjectMember> getProjectMembersByUserId(Long userId);

    /**
     * 根据项目id和用户id查询项目成员
     * @param projectId 项目id
     * @param userId 用户id
     * @return 项目成员信息
     */
    ProjectMember getProjectMemberByProjectIdAndUserId(Long projectId, Long userId);

    /**
     * 分页查询项目成员列表
     * @param current 当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<ProjectMember> getProjectMemberPage(Integer current, Integer pageSize);

    /**
     * 保存项目成员
     * @param projectMember 项目成员信息
     * @return 是否成功
     */
    boolean saveProjectMember(ProjectMember projectMember);

    /**
     * 更新项目成员
     * @param projectMember 项目成员信息
     * @return 是否成功
     */
    boolean updateProjectMember(ProjectMember projectMember);

    /**
     * 删除项目成员
     * @param projectMemberId 项目成员id
     * @return 是否成功
     */
    boolean deleteProjectMember(Long projectMemberId);
}
