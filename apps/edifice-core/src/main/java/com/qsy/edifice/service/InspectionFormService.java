package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.entity.InspectionForm;
import com.qsy.edifice.domain.vo.InspectionFormListVo;

import java.util.List;

/**
 * 验工单服务接口
 */
public interface InspectionFormService {

    /**
     * 查询我的验工单列表
     *
     * @param getInspectionFormListDto 查询条件
     * @return 封装 vo 返回
     */
    List<InspectionFormListVo> getMyInspections(GetInspectionFormListDto getInspectionFormListDto);
    /**
     * 根据验工单id查询验工单
     * @param inspectionFormId 验工单id
     * @return 验工单信息
     */
    InspectionForm getInspectionFormById(Long inspectionFormId);

    /**
     * 根据验工单编号查询验工单
     * @param inspectionFormCode 验工单编号
     * @return 验工单信息
     */
    InspectionForm getInspectionFormByCode(String inspectionFormCode);

    /**
     * 根据项目id查询验工单列表
     * @param projectId 项目id
     * @return 验工单列表
     */
    List<InspectionForm> getInspectionFormsByProjectId(String projectId);

    /**
     * 根据项目阶段id查询验工单列表
     * @param projectStageId 项目阶段id
     * @return 验工单列表
     */
    List<InspectionForm> getInspectionFormsByProjectStageId(Long projectStageId);

    /**
     * 根据申请人id查询验工单列表
     * @param applyUserId 申请人id
     * @return 验工单列表
     */
    List<InspectionForm> getInspectionFormsByApplyUserId(Long applyUserId);

    /**
     * 分页查询验工单列表
     * @param current 当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<InspectionForm> getInspectionFormPage(Integer current, Integer pageSize);

    /**
     * 保存验工单
     * @param inspectionForm 验工单信息
     * @return 是否成功
     */
    boolean saveInspectionForm(InspectionForm inspectionForm);

    /**
     * 更新验工单
     * @param inspectionForm 验工单信息
     * @return 是否成功
     */
    boolean updateInspectionForm(InspectionForm inspectionForm);

    /**
     * 删除验工单
     * @param inspectionFormId 验工单id
     * @return 是否成功
     */
    boolean deleteInspectionForm(Long inspectionFormId);
}
