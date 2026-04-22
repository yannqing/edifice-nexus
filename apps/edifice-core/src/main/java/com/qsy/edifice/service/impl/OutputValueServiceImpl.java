package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.CreateOutputValueDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.OutputValueVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.OutputValueDistributionMapper;
import com.qsy.edifice.mapper.OutputValueMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OutputValueServiceImpl implements OutputValueService {

    @Resource
    private OutputValueMapper outputValueMapper;

    @Resource
    private OutputValueDistributionMapper distributionMapper;

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectStageService projectStageService;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectMemberService projectMemberService;

    @Resource
    private SysUserMapper sysUserMapper;

    private static final Long ROLE_MANAGER_ID = 101L;

    @Override
    public List<OutputValueVo> getOutputValueList(Integer status) {
        LambdaQueryWrapper<OutputValue> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(OutputValue::getStatus, status);
        }
        wrapper.orderByDesc(OutputValue::getCreatedTime);

        List<OutputValue> list = outputValueMapper.selectList(wrapper);
        return list.stream().map(this::convertToVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOutputValue(CreateOutputValueDto dto, Long userId) {
        if (dto.getProjectId() == null || dto.getProjectStageId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择项目和阶段");
        }
        if (dto.getTotalAmount() == null || dto.getTotalAmount() <= 0) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "产值总额必须大于0");
        }
        if (dto.getDistributions() == null || dto.getDistributions().isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请添加至少一条分配明细");
        }

        // 创建分配单
        OutputValue ov = OutputValue.builder()
                .projectId(dto.getProjectId())
                .projectStageId(dto.getProjectStageId())
                .totalAmount(dto.getTotalAmount())
                .status(0) // 待确认
                .submitUserId(userId)
                .submitTime(LocalDateTime.now())
                .build();
        outputValueMapper.insert(ov);

        // 创建分配明细
        for (CreateOutputValueDto.DistributionItem item : dto.getDistributions()) {
            OutputValueDistribution dist = OutputValueDistribution.builder()
                    .outputValueId(ov.getOutputValueId())
                    .userId(item.getUserId())
                    .workType(item.getWorkType())
                    .ratio(item.getRatio())
                    .amount(item.getAmount())
                    .build();
            distributionMapper.insert(dist);
        }

        return ov.getOutputValueId();
    }

    @Override
    public void confirmOutputValue(Long outputValueId) {
        OutputValue ov = outputValueMapper.selectById(outputValueId);
        if (ov == null) throw new BusinessException(ErrorType.ARGS_NOT_NULL, "分配单不存在");
        if (ov.getStatus() != 0) throw new BusinessException(ErrorType.OPERATION_FAILED, "当前状态无法确认");

        ov.setStatus(1); // 待审核
        outputValueMapper.updateById(ov);
    }

    @Override
    public void approveOutputValue(Long outputValueId, Long userId) {
        OutputValue ov = outputValueMapper.selectById(outputValueId);
        if (ov == null) throw new BusinessException(ErrorType.ARGS_NOT_NULL, "分配单不存在");
        if (ov.getStatus() != 1) throw new BusinessException(ErrorType.OPERATION_FAILED, "当前状态无法审批");

        ov.setStatus(2); // 已审批
        ov.setApprovedTime(LocalDateTime.now());
        outputValueMapper.updateById(ov);
    }

    @Override
    public void payOutputValue(Long outputValueId) {
        OutputValue ov = outputValueMapper.selectById(outputValueId);
        if (ov == null) throw new BusinessException(ErrorType.ARGS_NOT_NULL, "分配单不存在");
        if (ov.getStatus() != 2) throw new BusinessException(ErrorType.OPERATION_FAILED, "当前状态无法发放");

        ov.setStatus(3); // 已发放
        ov.setPaidTime(LocalDateTime.now());
        outputValueMapper.updateById(ov);
    }

    @Override
    public Map<String, Object> getStatistics() {
        List<OutputValue> all = outputValueMapper.selectList(null);
        Map<String, Object> stats = new HashMap<>();
        stats.put("pendingCount", all.stream().filter(o -> o.getStatus() == 0 || o.getStatus() == 1).count());
        stats.put("approvedCount", all.stream().filter(o -> o.getStatus() == 2).count());
        stats.put("paidAmount", all.stream().filter(o -> o.getStatus() == 3).mapToInt(OutputValue::getTotalAmount).sum());
        stats.put("totalAmount", all.stream().mapToInt(OutputValue::getTotalAmount).sum());
        return stats;
    }

    private OutputValueVo convertToVo(OutputValue ov) {
        OutputValueVo vo = new OutputValueVo();
        vo.setOutputValueId(ov.getOutputValueId());
        vo.setProjectId(ov.getProjectId());
        vo.setProjectStageId(ov.getProjectStageId());
        vo.setTotalAmount(ov.getTotalAmount());
        vo.setStatus(ov.getStatus());
        vo.setSubmitTime(ov.getSubmitTime());
        vo.setApprovedTime(ov.getApprovedTime());
        vo.setPaidTime(ov.getPaidTime());
        vo.setCreatedTime(ov.getCreatedTime());

        // 项目信息
        Project project = projectService.getProjectById(ov.getProjectId());
        if (project != null) {
            vo.setProjectName(project.getProjectName());
            vo.setProjectCode(project.getProjectCode());
            if (project.getProjectType() != null) {
                ProjectType type = projectTypeService.getProjectTypeById(project.getProjectType());
                if (type != null) vo.setProjectTypeName(type.getProjectTypeName());
            }
        }

        // 阶段信息
        ProjectStage stage = projectStageService.getProjectStageById(ov.getProjectStageId());
        if (stage != null) {
            vo.setStageName(stage.getStageName());
            vo.setStageOutput(stage.getStageOutput());
        }

        // 提交人
        if (ov.getSubmitUserId() != null) {
            SysUser user = sysUserMapper.selectById(ov.getSubmitUserId());
            if (user != null) vo.setSubmitUserName(user.getRealName());
        }

        // 分配明细
        LambdaQueryWrapper<OutputValueDistribution> distWrapper = new LambdaQueryWrapper<>();
        distWrapper.eq(OutputValueDistribution::getOutputValueId, ov.getOutputValueId());
        List<OutputValueDistribution> dists = distributionMapper.selectList(distWrapper);

        // 查项目成员角色
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(ov.getProjectId());
        Map<Long, Long> userRoleMap = new HashMap<>();
        if (members != null) {
            for (ProjectMember m : members) {
                userRoleMap.put(m.getUserId(), m.getProjectRole());
            }
        }

        List<OutputValueVo.DistributionItemVo> distVos = dists.stream().map(d -> {
            OutputValueVo.DistributionItemVo item = new OutputValueVo.DistributionItemVo();
            item.setDistributionId(d.getDistributionId());
            item.setUserId(d.getUserId());
            item.setWorkType(d.getWorkType());
            item.setRatio(d.getRatio());
            item.setAmount(d.getAmount());

            SysUser u = sysUserMapper.selectById(d.getUserId());
            if (u != null) item.setUserName(u.getRealName());

            Long roleId = userRoleMap.get(d.getUserId());
            item.setUserRole(ROLE_MANAGER_ID.equals(roleId) ? "项目经理" : "项目成员");

            return item;
        }).collect(Collectors.toList());

        vo.setDistributions(distVos);
        return vo;
    }
}
