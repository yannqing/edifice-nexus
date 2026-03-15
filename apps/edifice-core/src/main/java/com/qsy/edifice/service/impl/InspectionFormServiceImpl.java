package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.entity.InspectionForm;
import com.qsy.edifice.domain.vo.InspectionFormDetailVo;
import com.qsy.edifice.domain.vo.InspectionFormListVo;
import com.qsy.edifice.domain.vo.InspectionOverviewVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
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
    public Page<InspectionFormListVo> getMyInspections(GetInspectionFormListDto getInspectionFormListDto) {

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

        //List<InspectionForm> inspectionForms = inspectionFormMapper.selectList(queryWrapper);

        Page<InspectionForm> inspectionFormPage = inspectionFormMapper.selectPage(new Page<>(current, pageSize), queryWrapper);

        List<InspectionFormListVo> inspectionFormListVos = inspectionFormPage.getRecords().stream().map(InspectionFormListVo::objToVo).toList();

        return new Page<InspectionFormListVo>(current,pageSize,inspectionFormPage.getTotal()).setRecords(inspectionFormListVos);
    }

    @Override
    public InspectionFormDetailVo getInspectionById(Long id){

        //1.参数校验
         if (id==null){
             throw  new BusinessException(ErrorType.NO_AUTH_ERROR);
         }
         //2.查询数据库，获取用户原始数据

         InspectionForm inspectionForm =inspectionFormMapper.selectById(id);

         if(inspectionForm == null) {

            return null;
        }
         InspectionFormDetailVo inspectionFormDetailVo= InspectionFormDetailVo.objToVo(inspectionForm);

        log.info("查询用户(id: {})", id);

        return inspectionFormDetailVo;
    }
    @Override
    public InspectionOverviewVo getInspectionOverview() {
        InspectionOverviewVo vo = new InspectionOverviewVo();

        // 每次都查数据库，实时统计
        vo.setPendingApproval(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 0)
        ));

        vo.setPendingFirstReview(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 1)
        ));

        vo.setApproved(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 3)
        ));

        vo.setRejected(inspectionFormMapper.selectCount(
                new QueryWrapper<InspectionForm>().eq("inspection_form_status", 2)
        ));

        return vo;
    }
}
