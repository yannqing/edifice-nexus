package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qsy.edifice.domain.entity.ProjectMember;
import com.qsy.edifice.mapper.ProjectMemberMapper;
import com.qsy.edifice.service.ProjectMemberService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目成员服务实现类
 */
@Slf4j
@Service
public class ProjectMemberServiceImpl extends ServiceImpl<ProjectMemberMapper,ProjectMember> implements ProjectMemberService {

    @Resource
    private ProjectMemberMapper projectMemberMapper;

    @Override
    public ProjectMember getProjectMemberById(Long projectMemberId) {
        return projectMemberMapper.selectById(projectMemberId);
    }

    @Override
    public List<ProjectMember> getProjectMembersByProjectId(Long projectId) {
        return projectMemberMapper.selectByProjectId(projectId);
    }

    @Override
    public List<ProjectMember> getProjectMembersByUserId(Long userId) {
        return projectMemberMapper.selectByUserId(userId);
    }

    @Override
    public ProjectMember getProjectMemberByProjectIdAndUserId(Long projectId, Long userId) {
        return projectMemberMapper.selectByProjectIdAndUserId(projectId, userId);
    }

    @Override
    public Page<ProjectMember> getProjectMemberPage(Integer current, Integer pageSize) {
        return projectMemberMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveProjectMember(ProjectMember projectMember) {
        return projectMemberMapper.insert(projectMember) > 0;
    }

    @Override
    public boolean updateProjectMember(ProjectMember projectMember) {
        return projectMemberMapper.updateById(projectMember) > 0;
    }

    @Override
    public boolean deleteProjectMember(Long projectMemberId) {
        return projectMemberMapper.deleteById(projectMemberId) > 0;
    }
}
