package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.ApprovalRecords;

import java.util.List;

/**
 * 审批记录服务接口
 */
public interface ApprovalRecordsService {

    /**
     * 根据审批记录id查询审批记录
     * @param approvalRecordId 审批记录id
     * @return 审批记录信息
     */
    ApprovalRecords getApprovalRecordsById(Long approvalRecordId);

    /**
     * 根据业务id查询审批记录列表
     * @param inspectionFormId 业务id
     * @return 审批记录列表
     */
    List<ApprovalRecords> getApprovalRecordsByInspectionFormId(Long inspectionFormId);

    /**
     * 根据审批人id查询审批记录列表
     * @param approver 审批人id
     * @return 审批记录列表
     */
    List<ApprovalRecords> getApprovalRecordsByApproverId(Long approver);

    /**
     * 根据审批类型查询审批记录列表
     * @param approvalRecordType 审批类型
     * @return 审批记录列表
     */
    List<ApprovalRecords> getApprovalRecordsByApprovalRecordType(Integer approvalRecordType);

    /**
     * 分页查询审批记录列表
     * @param current 当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<ApprovalRecords> getApprovalRecordsPage(Integer current, Integer pageSize);

    /**
     * 保存审批记录
     * @param approvalRecords 审批记录信息
     * @return 是否成功
     */
    boolean saveApprovalRecords(ApprovalRecords approvalRecords);

    /**
     * 更新审批记录
     * @param approvalRecords 审批记录信息
     * @return 是否成功
     */
    boolean updateApprovalRecords(ApprovalRecords approvalRecords);

    /**
     * 删除审批记录
     * @param approvalRecordId 审批记录id
     * @return 是否成功
     */
    boolean deleteApprovalRecords(Long approvalRecordId);
}
