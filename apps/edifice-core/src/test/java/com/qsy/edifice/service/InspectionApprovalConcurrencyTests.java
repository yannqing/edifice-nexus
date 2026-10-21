package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.qsy.edifice.domain.dto.ApprovalInspectionDto;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.domain.entity.InspectionForm;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.InspectionFormMapper;
import com.qsy.edifice.service.impl.InspectionFormServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InspectionApprovalConcurrencyTests {
    SettlementProjectTests fixture;
    InspectionFormMapper forms;
    InspectionFormServiceImpl service;
    ApprovalFlowService flow;
    Long project, stage;

    @BeforeEach void setup() throws Exception {
        fixture = new SettlementProjectTests(); fixture.setup();
        project = fixture.child(fixture.root("A", "1000000"), "A1", "120000", 7L);
        stage = fixture.stageService.getProjectStagesByProjectId(project).get(0).getProjectStageId();
        fixture.tx.execute(status -> { fixture.stageService.startStages(Set.of(stage)); return null; });
        fixture.jdbc.execute("""
                CREATE TABLE inspection_form (inspection_form_id BIGINT PRIMARY KEY, inspection_form_code VARCHAR(100),
                  project_id VARCHAR(100), project_stage_id BIGINT, inspection_form_description VARCHAR(255),
                  apply_user_id BIGINT, inspection_form_status INT, file_ids VARCHAR(255), completion_ratio DECIMAL(12,4),
                  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  is_delete INT DEFAULT 0)
                """);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(fixture.jdbc.getDataSource());
        factory.setMapperLocations(new ClassPathResource("mapper/InspectionFormMapper.xml"));
        forms = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(InspectionFormMapper.class);
        service = new InspectionFormServiceImpl(); flow = mock(ApprovalFlowService.class);
        ReflectionTestUtils.setField(service, "inspectionFormMapper", forms);
        ReflectionTestUtils.setField(service, "projectStageMapper", fixture.stages);
        ReflectionTestUtils.setField(service, "projectStageService", fixture.stageService);
        ReflectionTestUtils.setField(service, "approvalFlowService", flow);
        when(flow.getCurrentPending(eq(ApprovalBizType.INSPECTION), anyLong())).thenAnswer(call ->
                ApprovalRecords.builder().approvalRecordId(call.getArgument(1)).build());
        when(flow.approve(any(), anyLong())).thenAnswer(call -> new ApprovalFlowService.ApprovalResult(
                100L, ApprovalBizType.INSPECTION, 100L, false, true, null));
    }

    @RepeatedTest(10) void concurrentApprovalsAccumulateBothRatiosInsteadOfOverwritingProgress() throws Exception {
        insert(1001L, "40"); insert(1002L, "60");
        var reads = new CyclicBarrier(2);
        var concurrentForms = mock(InspectionFormMapper.class, delegatesTo(forms));
        doAnswer(call -> {
            InspectionForm form = forms.selectById((Long) call.getArgument(0));
            reads.await(10, TimeUnit.SECONDS);
            return form;
        }).when(concurrentForms).selectById(anyLong());
        ReflectionTestUtils.setField(service, "inspectionFormMapper", concurrentForms);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> approve(1001L));
            var second = pool.submit(() -> approve(1002L));
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(0, new BigDecimal("100").compareTo(fixture.stages.selectById(stage).getCompletionRatio()));
        assertEquals(6, fixture.stages.selectById(stage).getStageStatus());
        assertEquals(3, forms.selectById(1001L).getInspectionFormStatus());
        assertEquals(3, forms.selectById(1002L).getInspectionFormStatus());
        assertTrue(fixture.stageService.getProjectStagesByProjectId(fixture.mapper.selectById(project).getParentProjectId())
                .stream().allMatch(item -> item.getStageStatus() == 0));
    }

    @Test void processedFormCannotBeApprovedAgainOrIncrementProgressTwice() {
        insert(1001L, "40"); approve(1001L);
        assertThrows(BusinessException.class, () -> approve(1001L));
        assertEquals(0, new BigDecimal("40").compareTo(fixture.stages.selectById(stage).getCompletionRatio()));
        verify(flow, times(1)).approve(any(), anyLong());
    }

    @Test void concurrentRequestsForSameFormOnlyIncrementOnce() throws Exception {
        insert(1001L, "40");
        var reads = new CyclicBarrier(2);
        var concurrentForms = mock(InspectionFormMapper.class, delegatesTo(forms));
        doAnswer(call -> {
            InspectionForm form = forms.selectById((Long) call.getArgument(0));
            reads.await(10, TimeUnit.SECONDS);
            return form;
        }).when(concurrentForms).selectById(anyLong());
        ReflectionTestUtils.setField(service, "inspectionFormMapper", concurrentForms);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int succeeded = 0;
        try {
            var futures = java.util.List.of(pool.submit(() -> approve(1001L)), pool.submit(() -> approve(1001L)));
            for (var future : futures) {
                try {
                    future.get(20, TimeUnit.SECONDS); succeeded++;
                } catch (ExecutionException exception) {
                    assertInstanceOf(BusinessException.class, exception.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(1, succeeded);
        assertEquals(0, new BigDecimal("40").compareTo(fixture.stages.selectById(stage).getCompletionRatio()));
        verify(flow, times(1)).approve(any(), anyLong());
    }

    @Test void failedApprovalRollsBackWithoutChangingStageOrForm() {
        insert(1001L, "40");
        BigDecimal initialRatio = fixture.stages.selectById(stage).getCompletionRatio();
        when(flow.approve(any(), anyLong())).thenThrow(new BusinessException("Approval failed"));
        assertThrows(BusinessException.class, () -> approve(1001L));
        assertEquals(0, forms.selectById(1001L).getInspectionFormStatus());
        assertEquals(initialRatio, fixture.stages.selectById(stage).getCompletionRatio());
    }

    void insert(Long id, String ratio) {
        fixture.jdbc.update("INSERT INTO inspection_form(inspection_form_id,project_id,project_stage_id,apply_user_id,inspection_form_status,completion_ratio) VALUES(?,?,?,?,0,?)",
                id, project.toString(), stage, 7L, new BigDecimal(ratio));
    }

    Void approve(Long id) {
        return fixture.tx.execute(status -> { service.approvalInspection(
                new ApprovalInspectionDto(id, 1, "Approved", null, true), 8L); return null; });
    }
}
