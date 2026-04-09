package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.mapper.ApprovalRecordsMapper;
import com.qsy.edifice.service.ApprovalRecordsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审批记录服务实现类
 */
@Slf4j
@Service
public class ApprovalRecordsServiceImpl implements ApprovalRecordsService {

    @Resource
    private ApprovalRecordsMapper approvalRecordsMapper;

    @Override
    public ApprovalRecords getApprovalRecordsById(Long approvalRecordId) {
        return approvalRecordsMapper.selectById(approvalRecordId);
    }

    @Override
    public List<ApprovalRecords> getApprovalRecordsByInspectionFormId(Long inspectionFormId) {
        return approvalRecordsMapper.selectByInspectionFormId(inspectionFormId);
    }

    @Override
    public List<ApprovalRecords> getApprovalRecordsByApproverId(Long approver) {
        return approvalRecordsMapper.selectByApproverId(approver);
    }

    @Override
    public List<ApprovalRecords> getApprovalRecordsByApprovalRecordType(Integer approvalRecordType) {
        return approvalRecordsMapper.selectByApprovalRecordType(approvalRecordType);
    }

    @Override
    public Page<ApprovalRecords> getApprovalRecordsPage(Integer current, Integer pageSize) {
        return approvalRecordsMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveApprovalRecords(ApprovalRecords approvalRecords) {
        return approvalRecordsMapper.insert(approvalRecords) > 0;
    }

    @Override
    public boolean updateApprovalRecords(ApprovalRecords approvalRecords) {
        return approvalRecordsMapper.updateById(approvalRecords) > 0;
    }

    @Override
    public boolean deleteApprovalRecords(Long approvalRecordId) {
        return approvalRecordsMapper.deleteById(approvalRecordId) > 0;
    }
}
