package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ApprovalRecordsMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.impl.ApprovalFlowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalFlowServiceImplTests {

    private ApprovalRecordsMapper approvalRecordsMapper;
    private SysUserMapper sysUserMapper;
    private ApprovalFlowServiceImpl service;

    @BeforeEach
    void setUp() {
        approvalRecordsMapper = mock(ApprovalRecordsMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        service = new ApprovalFlowServiceImpl();
        ReflectionTestUtils.setField(service, "approvalRecordsMapper", approvalRecordsMapper);
        ReflectionTestUtils.setField(service, "sysUserMapper", sysUserMapper);
    }

    @Test
    void shouldRejectDisabledApprover() {
        when(sysUserMapper.selectById(2L)).thenReturn(SysUser.builder()
                .userId(2L).status(0).employmentStatus(1).build());

        assertThrows(BusinessException.class,
                () -> service.submit(new SubmitApprovalDto("bid", 10L, 2L, null), 1L));
        verify(approvalRecordsMapper, never()).insert(any(ApprovalRecords.class));
    }

    @Test
    void shouldRejectApplicantAsFirstApprover() {
        when(sysUserMapper.selectById(1L)).thenReturn(activeUser(1L));

        assertThrows(BusinessException.class,
                () -> service.submit(new SubmitApprovalDto("bid", 10L, 1L, null), 1L));
        verify(approvalRecordsMapper, never()).insert(any(ApprovalRecords.class));
    }

    @Test
    void shouldTranslateDuplicatePendingNodeIntoBusinessError() {
        when(sysUserMapper.selectById(2L)).thenReturn(activeUser(2L));
        when(approvalRecordsMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate pending business"))
                .when(approvalRecordsMapper).insert(any(ApprovalRecords.class));

        assertThrows(BusinessException.class,
                () -> service.submit(new SubmitApprovalDto("bid", 10L, 2L, null), 1L));
    }

    @Test
    void shouldRejectConcurrentApprovalWhenPendingNodeAlreadyChanged() {
        ApprovalRecords record = ApprovalRecords.builder()
                .approvalRecordId(100L)
                .approvalRecordType(4)
                .bizTypeExt("bid")
                .inspectionFormId(10L)
                .approver(2L)
                .inspectionFormStatus(0)
                .build();
        when(approvalRecordsMapper.selectById(100L)).thenReturn(record);
        when(approvalRecordsMapper.updatePendingResult(any())).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.approve(new ApproveDto(100L, true, null, "同意"), 2L));
        verify(approvalRecordsMapper, never()).insert(any(ApprovalRecords.class));
    }

    private SysUser activeUser(Long id) {
        return SysUser.builder().userId(id).status(1).employmentStatus(1).build();
    }
}
