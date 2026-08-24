package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.ApplyInspectionDto;
import com.qsy.edifice.domain.dto.ApprovalInspectionDto;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.domain.entity.InspectionForm;
import com.qsy.edifice.domain.entity.ProjectStage;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.InspectionFormMapper;
import com.qsy.edifice.mapper.ProjectStageMapper;
import com.qsy.edifice.service.impl.InspectionFormServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionFormServiceImplTests {

    private InspectionFormMapper inspectionFormMapper;
    private ProjectStageMapper projectStageMapper;
    private ProjectService projectService;
    private ProjectStageService projectStageService;
    private ApprovalFlowService approvalFlowService;
    private BusinessRuleConfigService businessRuleConfigService;
    private InspectionFormServiceImpl service;

    @BeforeEach
    void setUp() {
        inspectionFormMapper = mock(InspectionFormMapper.class);
        projectStageMapper = mock(ProjectStageMapper.class);
        projectService = mock(ProjectService.class);
        projectStageService = mock(ProjectStageService.class);
        approvalFlowService = mock(ApprovalFlowService.class);
        businessRuleConfigService = mock(BusinessRuleConfigService.class);

        service = new InspectionFormServiceImpl();
        ReflectionTestUtils.setField(service, "inspectionFormMapper", inspectionFormMapper);
        ReflectionTestUtils.setField(service, "projectStageMapper", projectStageMapper);
        ReflectionTestUtils.setField(service, "projectService", projectService);
        ReflectionTestUtils.setField(service, "projectStageService", projectStageService);
        ReflectionTestUtils.setField(service, "approvalFlowService", approvalFlowService);
        ReflectionTestUtils.setField(service, "businessRuleConfigService", businessRuleConfigService);
    }

    @Test
    void rejectedInspectionKeepsStageInProgress() {
        InspectionForm form = InspectionForm.builder()
                .inspectionFormId(100L)
                .projectStageId(20L)
                .applyUserId(1L)
                .inspectionFormStatus(0)
                .build();
        ProjectStage stage = ProjectStage.builder()
                .projectStageId(20L)
                .projectId(10L)
                .stageName("成果编制")
                .stageStatus(1)
                .build();
        ApprovalRecords pending = ApprovalRecords.builder()
                .approvalRecordId(200L)
                .inspectionFormId(100L)
                .approver(2L)
                .inspectionFormStatus(0)
                .build();

        when(inspectionFormMapper.selectById(100L)).thenReturn(form);
        when(approvalFlowService.getCurrentPending(ApprovalBizType.INSPECTION, 100L)).thenReturn(pending);
        when(approvalFlowService.approve(any(), any())).thenReturn(
                new ApprovalFlowService.ApprovalResult(
                        200L, ApprovalBizType.INSPECTION, 100L, true, true, null));
        when(projectStageService.getProjectStageById(20L)).thenReturn(stage);

        service.approvalInspection(new ApprovalInspectionDto(100L, 2, "材料需要补充", null, true), 2L);

        assertEquals(2, form.getInspectionFormStatus());
        assertEquals(1, stage.getStageStatus());
        verify(inspectionFormMapper).updateById(form);
        verify(projectStageService).updateProjectStage(stage);
        verify(projectStageService).syncProjectStatus(10L);
    }

    @Test
    void resubmissionRestartsLegacyRejectedStageAndCreatesNewInspection() {
        InspectionForm rejected = InspectionForm.builder()
                .inspectionFormId(100L)
                .projectId("10")
                .projectStageId(20L)
                .applyUserId(1L)
                .inspectionFormStatus(2)
                .completionRatio(new BigDecimal("40"))
                .build();
        ProjectStage stage = ProjectStage.builder()
                .projectStageId(20L)
                .projectId(10L)
                .stageName("成果编制")
                .stageStatus(4)
                .build();
        ApprovalRecords firstRecord = ApprovalRecords.builder()
                .approvalRecordId(300L)
                .build();

        when(businessRuleConfigService.booleanValue("inspection", "require_materials", true))
                .thenReturn(true);
        when(projectStageMapper.selectByIdForUpdate(20L)).thenReturn(stage);
        when(inspectionFormMapper.selectByProjectStageIdForUpdate(20L)).thenReturn(List.of(rejected));
        when(inspectionFormMapper.insert(any(InspectionForm.class))).thenAnswer(invocation -> {
            InspectionForm inserted = invocation.getArgument(0);
            inserted.setInspectionFormId(101L);
            return 1;
        });
        when(approvalFlowService.submit(any(), any())).thenReturn(firstRecord);

        ApplyInspectionDto dto = new ApplyInspectionDto(
                10L,
                20L,
                "补充验收材料后重新提交",
                "[9001]",
                2L,
                new BigDecimal("40"),
                100L
        );

        Long newId = service.applyInspection(dto, 1L);

        assertEquals(101L, newId);
        assertEquals(1, stage.getStageStatus());
        verify(projectStageMapper).updateById(stage);
        verify(projectStageService).syncProjectStatus(10L);

        ArgumentCaptor<InspectionForm> formCaptor = ArgumentCaptor.forClass(InspectionForm.class);
        verify(inspectionFormMapper).insert(formCaptor.capture());
        InspectionForm created = formCaptor.getValue();
        assertEquals(0, created.getInspectionFormStatus());
        assertEquals("补充验收材料后重新提交", created.getInspectionFormDescription());
        assertEquals(new BigDecimal("40"), created.getCompletionRatio());
    }

    @Test
    void completionRatioCannotExceedApprovedAndPendingRemainder() {
        ProjectStage stage = ProjectStage.builder()
                .projectStageId(20L)
                .projectId(10L)
                .stageName("成果编制")
                .stageStatus(1)
                .build();
        InspectionForm approved = InspectionForm.builder()
                .inspectionFormId(100L)
                .projectStageId(20L)
                .inspectionFormStatus(3)
                .completionRatio(new BigDecimal("40"))
                .build();
        InspectionForm pending = InspectionForm.builder()
                .inspectionFormId(101L)
                .projectStageId(20L)
                .inspectionFormStatus(0)
                .completionRatio(new BigDecimal("30"))
                .build();

        when(projectStageMapper.selectByIdForUpdate(20L)).thenReturn(stage);
        when(inspectionFormMapper.selectByProjectStageIdForUpdate(20L))
                .thenReturn(List.of(approved, pending));

        ApplyInspectionDto dto = new ApplyInspectionDto(
                10L,
                20L,
                "申请剩余阶段验工",
                "[9001]",
                2L,
                new BigDecimal("31"),
                null
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.applyInspection(dto, 1L));

        assertTrue(exception.getMessage().contains("本次最多可申请30%"));
    }
}
