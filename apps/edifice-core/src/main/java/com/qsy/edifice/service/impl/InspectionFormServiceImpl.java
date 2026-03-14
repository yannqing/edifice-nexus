package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.entity.InspectionForm;
import com.qsy.edifice.domain.vo.InspectionFormListVo;
import com.qsy.edifice.mapper.InspectionFormMapper;
import com.qsy.edifice.service.InspectionFormService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 验工单服务实现类
 */
@Slf4j
@Service
public class InspectionFormServiceImpl implements InspectionFormService {

    @Resource
    private InspectionFormMapper inspectionFormMapper;

    @Override
    public List<InspectionFormListVo> getMyInspections(GetInspectionFormListDto getInspectionFormListDto) {

        //1.获取全部参数
        Long inspectionFormId = getInspectionFormListDto.getInspectionFormId();
        String inspectionFormCode = getInspectionFormListDto.getInspectionFormCode();
        String projectId = getInspectionFormListDto.getProjectId();
        Long projectStageId = getInspectionFormListDto.getProjectStageId();
        Long applyUserId = getInspectionFormListDto.getApplyUserId();
        Integer inspectionFormStatus = getInspectionFormListDto.getInspectionFormStatus();
        Integer current = getInspectionFormListDto.getCurrent();
        Integer pageSize = getInspectionFormListDto.getPageSize();

        QueryWrapper<InspectionForm> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotEmpty(inspectionFormCode), "inspectionFormCode", inspectionFormCode);
        queryWrapper.eq(StringUtils.isNotEmpty(projectId), "projectId", projectId);

        List<InspectionForm> inspectionForms = inspectionFormMapper.selectList(queryWrapper);

        inspectionFormMapper.selectPage(new Page<>(current, pageSize), queryWrapper);
        return List.of();
    }

    @Override
    public InspectionForm getInspectionFormById(Long inspectionFormId) {
        return inspectionFormMapper.selectById(inspectionFormId);
    }

    @Override
    public InspectionForm getInspectionFormByCode(String inspectionFormCode) {
        return inspectionFormMapper.selectByInspectionFormCode(inspectionFormCode);
    }

    @Override
    public List<InspectionForm> getInspectionFormsByProjectId(String projectId) {
        return inspectionFormMapper.selectByProjectId(projectId);
    }

    @Override
    public List<InspectionForm> getInspectionFormsByProjectStageId(Long projectStageId) {
        return inspectionFormMapper.selectByProjectStageId(projectStageId);
    }

    @Override
    public List<InspectionForm> getInspectionFormsByApplyUserId(Long applyUserId) {
        return inspectionFormMapper.selectByApplyUserId(applyUserId);
    }

    @Override
    public Page<InspectionForm> getInspectionFormPage(Integer current, Integer pageSize) {
        return inspectionFormMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveInspectionForm(InspectionForm inspectionForm) {
        return inspectionFormMapper.insert(inspectionForm) > 0;
    }

    @Override
    public boolean updateInspectionForm(InspectionForm inspectionForm) {
        return inspectionFormMapper.updateById(inspectionForm) > 0;
    }

    @Override
    public boolean deleteInspectionForm(Long inspectionFormId) {
        return inspectionFormMapper.deleteById(inspectionFormId) > 0;
    }
}
