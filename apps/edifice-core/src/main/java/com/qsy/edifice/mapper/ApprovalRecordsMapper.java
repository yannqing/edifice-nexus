package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审批记录 Mapper 接口
 */
@Mapper
public interface ApprovalRecordsMapper extends BaseMapper<ApprovalRecords> {

    /**
     * 根据业务id查询审批记录列表
     * @param inspectionFormId 业务id
     * @return 审批记录列表
     */
    List<ApprovalRecords> selectByInspectionFormId(@Param("inspectionFormId") Long inspectionFormId);

    /**
     * 根据审批人id查询审批记录列表
     * @param approver 审批人id
     * @return 审批记录列表
     */
    List<ApprovalRecords> selectByApproverId(@Param("approver") Long approver);

    /**
     * 根据审批类型查询审批记录列表
     * @param approvalRecordType 审批类型
     * @return 审批记录列表
     */
    List<ApprovalRecords> selectByApprovalRecordType(@Param("approvalRecordType") Integer approvalRecordType);
}
