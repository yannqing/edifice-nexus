package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.ApplyInspectionDto;
import com.qsy.edifice.domain.dto.ApprovalInspectionDto;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.vo.InspectionFormDetailVo;
import com.qsy.edifice.domain.vo.InspectionFormListVo;
import com.qsy.edifice.domain.vo.InspectionOverviewVo;

/**
 * 验工单服务接口
 */
public interface InspectionFormService {

    /**
     * 查询我的验工单列表（按申请人过滤）
     * @param dto 查询条件
     * @param userId 当前用户id
     * @return 分页结果
     */
    Page<InspectionFormListVo> getMyInspections(GetInspectionFormListDto dto, Long userId);

    /**
     * 查询全部验工单列表
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<InspectionFormListVo> getAllInspections(GetInspectionFormListDto dto);

    /**
     * 根据id查询验工单详情
     * @param id 验工单id
     * @return 验工单详情
     */
    InspectionFormDetailVo getInspectionById(Long id);

    /**
     * 验工单数据总览
     * @return 统计数据
     */
    InspectionOverviewVo getInspectionOverview();

    /**
     * 提交验工单
     * @param dto 提交参数
     * @param userId 当前用户id
     * @return 验工单id
     */
    Long applyInspection(ApplyInspectionDto dto, Long userId);

    /**
     * 审批验工单
     * @param dto 审批参数
     * @param userId 审批人id
     */
    void approvalInspection(ApprovalInspectionDto dto, Long userId);
}
