package com.qsy.edifice.service;

import com.qsy.edifice.domain.entity.Contract;
import com.qsy.edifice.domain.entity.OutputValue;
import com.qsy.edifice.domain.entity.ProjectStage;
import com.qsy.edifice.domain.model.OutputAllocationContext;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.OutputValueMapper;
import com.qsy.edifice.service.impl.OutputValueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutputValueServiceImplTests {
    OutputValueServiceImpl service;
    OutputValueMapper mapper;
    ProjectStageService stages;
    ApprovalFlowService flow;

    @BeforeEach void setup() {
        service = new OutputValueServiceImpl();
        mapper = mock(OutputValueMapper.class);
        stages = mock(ProjectStageService.class);
        flow = mock(ApprovalFlowService.class);
        var contracts = mock(ContractService.class);
        var rules = mock(BusinessRuleConfigService.class);
        var allocation = mock(OutputAllocationRuleService.class);
        ReflectionTestUtils.setField(service, "outputValueMapper", mapper);
        ReflectionTestUtils.setField(service, "projectStageService", stages);
        ReflectionTestUtils.setField(service, "approvalFlowService", flow);
        ReflectionTestUtils.setField(service, "contractService", contracts);
        ReflectionTestUtils.setField(service, "businessRuleConfigService", rules);
        ReflectionTestUtils.setField(service, "outputAllocationRuleService", allocation);
        when(stages.getProjectStageById(20L)).thenReturn(ProjectStage.builder()
                .projectId(10L).projectStageId(20L).stageStatus(6)
                .completionRatio(new BigDecimal("100")).stageOutput(new BigDecimal("4")).build());
        when(contracts.getContractByProjectId(10L)).thenReturn(Contract.builder()
                .contractType(0).contractAmount(new BigDecimal("100000")).baseAmount(new BigDecimal("100000")).build());
        when(rules.booleanValue("output", "require_stage_inspection_passed", true)).thenReturn(true);
        when(allocation.calculate(anyLong(), anyLong(), any())).thenReturn(new OutputAllocationContext(
                1L, 1, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
    }

    @Test void thirdPartialAllocationDeductsCumulativeProgressOnlyOnce() {
        when(mapper.selectList(any())).thenReturn(List.of(paid(2L, "60", "30"), paid(1L, "30", "30")));
        var preview = service.previewOutputValue(10L, 20L);
        assertEquals(new BigDecimal("40.00"), preview.getIncrementalRatio());
        assertEquals(new BigDecimal("1600.00"), preview.getCurrentStageAmount());
    }

    @Test void legacyIncrementalSnapshotsAreSummed() {
        when(mapper.selectList(any())).thenReturn(List.of(paid(1L, null, "30"), paid(2L, null, "30")));
        assertEquals(new BigDecimal("1600.00"), service.previewOutputValue(10L, 20L).getCurrentStageAmount());
    }

    @Test void mixedSnapshotsFollowHistoricalOrder() {
        when(mapper.selectList(any())).thenReturn(List.of(paid(3L, null, "20"), paid(1L, "30", "30"), paid(2L, "60", "30")));
        assertEquals(new BigDecimal("20.00"), service.previewOutputValue(10L, 20L).getIncrementalRatio());
    }

    @Test void legacyAllocationWithoutRatioReservesWholeStage() {
        when(mapper.selectList(any())).thenReturn(List.of(paid(1L, null, null)));
        assertEquals(new BigDecimal("0.00"), service.previewOutputValue(10L, 20L).getCurrentStageAmount());
    }

    @Test void completionCannotProduceNegativeRemainingAmount() {
        when(mapper.selectList(any())).thenReturn(List.of(paid(1L, "100", "100")));
        when(stages.getProjectStageById(20L)).thenReturn(ProjectStage.builder().projectId(10L)
                .projectStageId(20L).stageStatus(1).completionRatio(new BigDecimal("60"))
                .stageOutput(new BigDecimal("4")).build());
        assertEquals(new BigDecimal("0.00"), service.previewOutputValue(10L, 20L).getCurrentStageAmount());
    }

    @Test void laterPartialAllocationCanBeConfirmed() {
        OutputValue current = paid(2L, "60", "30");
        current.setStatus(0); current.setCurrentHandlerId(7L);
        when(mapper.selectById(2L)).thenReturn(current);
        when(mapper.selectByProjectStageIdForUpdate(20L)).thenReturn(List.of(paid(1L, "30", "30"), current));
        service.confirmOutputValue(2L, 7L, 8L);
        assertEquals(1, current.getStatus());
        assertEquals(8L, current.getCurrentHandlerId());
        verify(mapper).updateById(current);
    }

    @Test void overlappingPartialAllocationCannotBeConfirmed() {
        OutputValue current = paid(3L, "60", "30");
        current.setStatus(0); current.setCurrentHandlerId(7L);
        when(mapper.selectById(3L)).thenReturn(current);
        when(mapper.selectByProjectStageIdForUpdate(20L)).thenReturn(List.of(paid(1L, "60", "60"), current));
        assertThrows(BusinessException.class, () -> service.confirmOutputValue(3L, 7L, 8L));
        verify(mapper, never()).updateById(any(OutputValue.class));
        verifyNoInteractions(flow);
    }

    private OutputValue paid(Long id, String cumulative, String incremental) {
        return OutputValue.builder().outputValueId(id).projectId(10L).projectStageId(20L).status(3)
                .stageCompletionRatio(cumulative == null ? null : new BigDecimal(cumulative))
                .stageIncrementalRatio(incremental == null ? null : new BigDecimal(incremental))
                .createdTime(LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(id))
                .currentStageAmount(new BigDecimal("1200")).build();
    }
}
