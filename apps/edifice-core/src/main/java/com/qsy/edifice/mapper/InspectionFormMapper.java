package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.InspectionForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 验工单 Mapper 接口
 */
@Mapper
public interface InspectionFormMapper extends BaseMapper<InspectionForm> {

    /**
     * 根据验工单编号查询验工单
     * @param inspectionFormCode 验工单编号
     * @return 验工单信息
     */
    InspectionForm selectByInspectionFormCode(@Param("inspectionFormCode") String inspectionFormCode);

    /**
     * 根据项目id查询验工单列表
     * @param projectId 项目id
     * @return 验工单列表
     */
    List<InspectionForm> selectByProjectId(@Param("projectId") String projectId);

    /**
     * 根据项目阶段id查询验工单列表
     * @param projectStageId 项目阶段id
     * @return 验工单列表
     */
    List<InspectionForm> selectByProjectStageId(@Param("projectStageId") Long projectStageId);

    /**
     * 根据申请人id查询验工单列表
     * @param applyUserId 申请人id
     * @return 验工单列表
     */
    List<InspectionForm> selectByApplyUserId(@Param("applyUserId") Long applyUserId);
}
