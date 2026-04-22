package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.entity.ProjectStage;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.ProjectStageMapper;
import com.qsy.edifice.service.ProjectStageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 项目阶段服务实现类
 *
 * 阶段状态机：
 * 0(未开始) → 1(进行中) → 2(待验收) → 3(已验收) → 6(已完成)
 *                ↑          ↓
 *                ←── 4(已驳回) ←
 */
@Slf4j
@Service
public class ProjectStageServiceImpl implements ProjectStageService {

    @Resource
    private ProjectStageMapper projectStageMapper;

    @Resource
    private ProjectMapper projectMapper;

    // ==================== 基础 CRUD ====================

    @Override
    public ProjectStage getProjectStageById(Long projectStageId) {
        return projectStageMapper.selectById(projectStageId);
    }

    @Override
    public List<ProjectStage> getProjectStagesByProjectId(Long projectId) {
        return projectStageMapper.selectByProjectId(projectId);
    }

    @Override
    public List<ProjectStage> getProjectStagesByProjectIdAndStatus(Long projectId, Integer stageStatus) {
        return projectStageMapper.selectByProjectIdAndStatus(projectId, stageStatus);
    }

    @Override
    public Page<ProjectStage> getProjectStagePage(Integer current, Integer pageSize) {
        return projectStageMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveProjectStage(ProjectStage projectStage) {
        return projectStageMapper.insert(projectStage) > 0;
    }

    @Override
    public boolean updateProjectStage(ProjectStage projectStage) {
        return projectStageMapper.updateById(projectStage) > 0;
    }

    @Override
    public boolean deleteProjectStage(Long projectStageId) {
        return projectStageMapper.deleteById(projectStageId) > 0;
    }

    // ==================== 阶段状态转换 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startStages(Set<Long> stageIds) {
        if (stageIds == null || stageIds.isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择要启动的阶段");
        }

        Long projectId = null;

        for (Long stageId : stageIds) {
            ProjectStage stage = projectStageMapper.selectById(stageId);
            if (stage == null) {
                throw new BusinessException(ErrorType.STAGE_NOT_FOUND);
            }
            if (stage.getStageStatus() != 0) {
                throw new BusinessException(ErrorType.STAGE_STATUS_INVALID,
                        "阶段[" + stage.getStageName() + "]当前状态不是[未开始]，无法启动");
            }

            stage.setStageStatus(1); // 进行中
            projectStageMapper.updateById(stage);
            projectId = stage.getProjectId();

            log.info("阶段启动: stageId={}, stageName={}", stageId, stage.getStageName());
        }

        // 同步项目状态
        if (projectId != null) {
            syncProjectStatus(projectId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restartStage(Long stageId) {
        if (stageId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "阶段ID不能为空");
        }

        ProjectStage stage = projectStageMapper.selectById(stageId);
        if (stage == null) {
            throw new BusinessException(ErrorType.STAGE_NOT_FOUND);
        }
        if (stage.getStageStatus() != 4) {
            throw new BusinessException(ErrorType.STAGE_STATUS_INVALID,
                    "阶段[" + stage.getStageName() + "]当前状态不是[已驳回]，无法重新启动");
        }

        stage.setStageStatus(1); // 进行中
        projectStageMapper.updateById(stage);

        log.info("阶段重启: stageId={}, stageName={}", stageId, stage.getStageName());

        syncProjectStatus(stage.getProjectId());
    }

    // ==================== 项目状态联动 ====================

    @Override
    public void syncProjectStatus(Long projectId) {
        if (projectId == null) return;

        List<ProjectStage> stages = projectStageMapper.selectByProjectId(projectId);
        if (stages == null || stages.isEmpty()) return;

        Project project = projectMapper.selectById(projectId);
        if (project == null) return;

        int newStatus = calculateProjectStatus(stages);
        if (project.getProjectStatus() != newStatus) {
            project.setProjectStatus(newStatus);
            projectMapper.updateById(project);
            log.info("项目状态同步: projectId={}, newStatus={}", projectId, newStatus);
        }
    }

    /**
     * 根据所有阶段状态计算项目状态
     *
     * 规则：
     * - 所有阶段都是 0(未开始) → 项目 0(未开始)
     * - 所有阶段都是 6(已完成) 或 3(已验收) → 项目 4(已结束)
     * - 其他情况 → 项目 1(进行中)
     */
    private int calculateProjectStatus(List<ProjectStage> stages) {
        boolean allNotStarted = stages.stream().allMatch(s -> s.getStageStatus() == 0);
        if (allNotStarted) {
            return 0; // 项目未开始
        }

        boolean allDone = stages.stream().allMatch(s -> s.getStageStatus() == 3 || s.getStageStatus() == 6);
        if (allDone) {
            return 4; // 项目已结束
        }

        return 1; // 项目进行中
    }
}
