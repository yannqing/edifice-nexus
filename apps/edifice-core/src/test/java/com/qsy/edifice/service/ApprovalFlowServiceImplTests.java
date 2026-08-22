package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ApprovalFlowConfigVo;
import com.qsy.edifice.domain.vo.ApprovalFlowNodeVo;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ApprovalRecordsMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.impl.ApprovalFlowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalFlowServiceImplTests {

    private ApprovalRecordsMapper approvalRecordsMapper;
    private SysUserMapper sysUserMapper;
    private ApprovalFlowConfigService approvalFlowConfigService;
    private ApprovalFlowServiceImpl service;

    @BeforeEach
    void setUp() {
        approvalRecordsMapper = mock(ApprovalRecordsMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        approvalFlowConfigService = mock(ApprovalFlowConfigService.class);
        service = new ApprovalFlowServiceImpl();
        ReflectionTestUtils.setField(service, "approvalRecordsMapper", approvalRecordsMapper);
        ReflectionTestUtils.setField(service, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(service, "approvalFlowConfigService", approvalFlowConfigService);
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
    void shouldAllowApplicantAsFirstApproverWhenFlowEnablesSelfApproval() {
        when(approvalFlowConfigService.getEnabledByBizType("bid"))
                .thenReturn(ApprovalFlowConfigVo.builder().allowSelfApproval(1).build());
        when(sysUserMapper.selectById(1L)).thenReturn(activeUser(1L));
        when(approvalRecordsMapper.selectOne(any())).thenReturn(null);

        service.submit(new SubmitApprovalDto("bid", 10L, 1L, null), 1L);

        verify(approvalRecordsMapper).insert(argThat((ApprovalRecords record) ->
                Long.valueOf(1L).equals(record.getApprover())
                        && Long.valueOf(1L).equals(record.getApplyUserId())));
    }

    @Test
    void shouldAllowApplicantAsLaterApproverWhenFlowEnablesSelfApproval() {
        ApprovalRecords current = pendingRecord(2L, 1L);
        when(approvalRecordsMapper.selectById(100L)).thenReturn(current);
        when(approvalRecordsMapper.selectByBizTypeExtAndBizId("bid", 10L)).thenReturn(List.of(current));
        when(sysUserMapper.selectBatchIds(anyCollection())).thenReturn(List.of(activeUser(2L)));
        when(sysUserMapper.selectById(1L)).thenReturn(activeUser(1L));
        when(approvalRecordsMapper.updatePendingResult(any())).thenReturn(1);
        when(approvalFlowConfigService.getEnabledByBizType("bid")).thenReturn(twoNodeConfig(true));

        service.approve(new ApproveDto(100L, true, 1L, false, "同意"), 2L);

        verify(approvalRecordsMapper).insert(argThat((ApprovalRecords record) ->
                Long.valueOf(1L).equals(record.getApprover())
                        && Integer.valueOf(2).equals(record.getApprovalLevel())));
    }

    @Test
    void shouldRejectApplicantExecutingApprovalWhenFlowDisablesSelfApproval() {
        ApprovalRecords current = pendingRecord(1L, 1L);
        when(approvalRecordsMapper.selectById(100L)).thenReturn(current);
        when(approvalFlowConfigService.getEnabledByBizType("bid")).thenReturn(twoNodeConfig(false));

        assertThrows(BusinessException.class,
                () -> service.approve(new ApproveDto(100L, false, null, false, "驳回"), 1L));

        verify(approvalRecordsMapper, never()).updatePendingResult(any());
    }

    @Test
    void shouldStillRejectRepeatedApproverWhenSelfApprovalIsEnabled() {
        ApprovalRecords current = pendingRecord(1L, 1L);
        when(approvalRecordsMapper.selectById(100L)).thenReturn(current);
        when(approvalRecordsMapper.selectByBizTypeExtAndBizId("bid", 10L)).thenReturn(List.of(current));
        when(sysUserMapper.selectBatchIds(anyCollection())).thenReturn(List.of(activeUser(1L)));
        when(sysUserMapper.selectById(1L)).thenReturn(activeUser(1L));
        when(approvalFlowConfigService.getEnabledByBizType("bid")).thenReturn(twoNodeConfig(true));

        assertThrows(BusinessException.class,
                () -> service.approve(new ApproveDto(100L, true, 1L, false, "同意"), 1L));

        verify(approvalRecordsMapper, never()).updatePendingResult(any());
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
                () -> service.approve(new ApproveDto(100L, true, null, false, "同意"), 2L));
        verify(approvalRecordsMapper, never()).insert(any(ApprovalRecords.class));
    }

    private SysUser activeUser(Long id) {
        return SysUser.builder().userId(id).status(1).employmentStatus(1).build();
    }

    private ApprovalRecords pendingRecord(Long approverId, Long applyUserId) {
        return ApprovalRecords.builder()
                .approvalRecordId(100L)
                .approvalRecordType(4)
                .bizTypeExt("bid")
                .inspectionFormId(10L)
                .approver(approverId)
                .applyUserId(applyUserId)
                .inspectionFormStatus(0)
                .approvalLevel(1)
                .build();
    }

    private ApprovalFlowConfigVo twoNodeConfig(boolean allowSelfApproval) {
        return ApprovalFlowConfigVo.builder()
                .allowStarterSelectNext(1)
                .allowSelfApproval(allowSelfApproval ? 1 : 0)
                .nodes(List.of(
                        ApprovalFlowNodeVo.builder()
                                .nodeOrder(1)
                                .nodeName("初审")
                                .approverSourceType("starter_select")
                                .build(),
                        ApprovalFlowNodeVo.builder()
                                .nodeOrder(2)
                                .nodeName("复审")
                                .approverSourceType("starter_select")
                                .build()))
                .build();
    }
}
