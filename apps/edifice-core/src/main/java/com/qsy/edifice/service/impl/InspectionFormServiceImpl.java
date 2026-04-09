package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.ApplyInspectionDto;
import com.qsy.edifice.domain.dto.ApprovalInspectionDto;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.*;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.InspectionFormMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 验工单服务实现类
 */
@Slf4j
@Service
public class InspectionFormServiceImpl implements InspectionFormService {

    @Resource
    private InspectionFormMapper inspectionFormMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectStageService projectStageService;

    @Resource
    private ContractService contractService;

    @Resource
    private ApprovalRecordsService approvalRecordsService;

    // ==================== 查询列表 ====================

    @Override
    public Page<InspectionFormListVo> getMyInspections(GetInspectionFormListDto dto) {
        Integer current = dto.getCurrent() != null ? dto.getCurrent() : 1;
        Integer pageSize = dto.getPageSize() != null ? dto.getPageSize() : 10;

        LambdaQueryWrapper<InspectionForm> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(dto.getInspectionFormCode())) {
            wrapper.like(InspectionForm::getInspectionFormCode, dto.getInspectionFormCode());
        }
        if (StringUtils.hasText(dto.getProjectId())) {
            wrapper.eq(InspectionForm::getProjectId, dto.getProjectId());
        }
        if (dto.getInspectionFormStatus() != null) {
            wrapper.eq(InspectionForm::getInspectionFormStatus, dto.getInspectionFormStatus());
        }
        if (dto.getApplyUserId() != null) {
            wrapper.eq(InspectionForm::getApplyUserId, dto.getApplyUserId());
        }

        wrapper.orderByDesc(InspectionForm::getCreatedTime);

        Page<InspectionForm> page = inspectionFormMapper.selectPage(new Page<>(current, pageSize), wrapper);

        List<InspectionFormListVo> voList = page.getRecords().stream()
                .map(this::convertToListVo)
                .collect(Collectors.toList());

        Page<InspectionFormListVo> voPage = new Page<>(current, pageSize, page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    // ==================== 查询详情 ====================

    @Override
    public InspectionFormDetailVo getInspectionById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        InspectionForm form = inspectionFormMapper.selectById(id);
        if (form == null) {
            return null;
        }

        InspectionFormDetailVo vo = InspectionFormDetailVo.objToVo(form);

        // 填充项目信息
        fillProjectInfo(vo, form);

        // 填充申请人姓名
        if (form.getApplyUserId() != null) {
            SysUser user = sysUserMapper.selectById(form.getApplyUserId());
            if (user != null) {
                vo.setApplyUserName(user.getRealName());
            }
        }

        // 填充审批记录
        List<ApprovalRecords> records = approvalRecordsService.getApprovalRecordsByInspectionFormId(form.getInspectionFormId());
        if (records != null && !records.isEmpty()) {
            List<ApprovalRecordVo> recordVos = records.stream().map(r -> {
                ApprovalRecordVo rv = ApprovalRecordVo.builder()
                        .approvalRecordId(r.getApprovalRecordId())
                        .approver(r.getApprover())
                        .approvalDescription(r.getApprovalDescription())
                        .inspectionFormStatus(r.getInspectionFormStatus())
                        .createdTime(r.getCreatedTime())
                        .build();
                // 查审批人姓名
                if (r.getApprover() != null) {
                    SysUser approverUser = sysUserMapper.selectById(r.getApprover());
                    if (approverUser != null) {
                        rv.setApproverName(approverUser.getRealName());
                    }
                }
                return rv;
            }).collect(Collectors.toList());
            vo.setApprovalRecords(recordVos);
        }

        return vo;
    }

    // ==================== 统计总览 ====================

    @Override
    public InspectionOverviewVo getInspectionOverview() {
        InspectionOverviewVo vo = new InspectionOverviewVo();
        vo.setPendingApproval(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 0)));
        vo.setPendingFirstReview(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 1)));
        vo.setApproved(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 3)));
        vo.setRejected(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 2)));
        return vo;
    }

    // ==================== 提交验工单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long applyInspection(ApplyInspectionDto dto, Long userId) {
        if (dto == null || userId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        if (dto.getProjectId() == null || dto.getProjectStageId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        // 生成验工单编号
        String code = "YG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        InspectionForm form = new InspectionForm();
        form.setInspectionFormCode(code);
        form.setProjectId(String.valueOf(dto.getProjectId()));
        form.setProjectStageId(dto.getProjectStageId());
        form.setInspectionFormDescription(dto.getInspectionFormDescription());
        form.setApplyUserId(userId);
        form.setInspectionFormStatus(0); // 待审核
        form.setFileIds(dto.getFileIds());

        inspectionFormMapper.insert(form);

        return form.getInspectionFormId();
    }

    // ==================== 审批验工单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approvalInspection(ApprovalInspectionDto dto, Long userId) {
        if (dto == null || dto.getInspectionFormId() == null || dto.getResult() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        // 查询验工单
        InspectionForm form = inspectionFormMapper.selectById(dto.getInspectionFormId());
        if (form == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        // 只有待审核(0)和审核中(1)的验工单可以审批
        if (form.getInspectionFormStatus() != 0 && form.getInspectionFormStatus() != 1) {
            throw new BusinessException(ErrorType.OPERATION_FAILED);
        }

        // 创建审批记录
        ApprovalRecords record = new ApprovalRecords();
        record.setApprovalRecordType(1); // 验工审批
        record.setInspectionFormId(dto.getInspectionFormId());
        record.setApprover(userId);
        record.setApprovalDescription(dto.getApprovalDescription());
        record.setInspectionFormStatus(dto.getResult()); // 1-通过 2-拒绝
        approvalRecordsService.saveApprovalRecords(record);

        // 更新验工单状态
        if (dto.getResult() == 1) {
            form.setInspectionFormStatus(3); // 已通过
        } else if (dto.getResult() == 2) {
            form.setInspectionFormStatus(2); // 已驳回
        }
        inspectionFormMapper.updateById(form);
    }

    // ==================== 私有方法 ====================

    /**
     * 转换为列表VO，填充关联数据
     */
    private InspectionFormListVo convertToListVo(InspectionForm form) {
        InspectionFormListVo vo = InspectionFormListVo.objToVo(form);

        fillProjectInfoForList(vo, form);

        // 申请人姓名
        if (form.getApplyUserId() != null) {
            SysUser user = sysUserMapper.selectById(form.getApplyUserId());
            if (user != null) {
                vo.setApplyUserName(user.getRealName());
            }
        }

        return vo;
    }

    /**
     * 填充列表VO的项目信息
     */
    private void fillProjectInfoForList(InspectionFormListVo vo, InspectionForm form) {
        // 项目信息
        if (StringUtils.hasText(form.getProjectId())) {
            try {
                Long projectId = Long.parseLong(form.getProjectId());
                Project project = projectService.getProjectById(projectId);
                if (project != null) {
                    vo.setProjectName(project.getProjectName());
                    vo.setProjectCode(project.getProjectCode());
                    // 项目类型
                    if (project.getProjectType() != null) {
                        ProjectType type = projectTypeService.getProjectTypeById(project.getProjectType());
                        if (type != null) {
                            vo.setProjectTypeName(type.getProjectTypeName());
                        }
                    }
                    // 合同金额
                    Contract contract = contractService.getContractByProjectId(projectId);
                    if (contract != null) {
                        vo.setContractAmount(contract.getContractAmount());
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("项目ID格式异常: {}", form.getProjectId());
            }
        }

        // 阶段信息
        if (form.getProjectStageId() != null) {
            ProjectStage stage = projectStageService.getProjectStageById(form.getProjectStageId());
            if (stage != null) {
                vo.setStageName(stage.getStageName());
                vo.setStageOutput(stage.getStageOutput());
            }
        }
    }

    /**
     * 填充详情VO的项目信息
     */
    private void fillProjectInfo(InspectionFormDetailVo vo, InspectionForm form) {
        if (StringUtils.hasText(form.getProjectId())) {
            try {
                Long projectId = Long.parseLong(form.getProjectId());
                Project project = projectService.getProjectById(projectId);
                if (project != null) {
                    vo.setProjectName(project.getProjectName());
                    vo.setProjectCode(project.getProjectCode());
                    if (project.getProjectType() != null) {
                        ProjectType type = projectTypeService.getProjectTypeById(project.getProjectType());
                        if (type != null) {
                            vo.setProjectTypeName(type.getProjectTypeName());
                        }
                    }
                    Contract contract = contractService.getContractByProjectId(projectId);
                    if (contract != null) {
                        vo.setContractAmount(contract.getContractAmount());
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("项目ID格式异常: {}", form.getProjectId());
            }
        }

        if (form.getProjectStageId() != null) {
            ProjectStage stage = projectStageService.getProjectStageById(form.getProjectStageId());
            if (stage != null) {
                vo.setStageName(stage.getStageName());
                vo.setStageOutput(stage.getStageOutput());
            }
        }
    }
}
