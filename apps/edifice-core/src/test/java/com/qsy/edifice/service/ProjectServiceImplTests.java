package com.qsy.edifice.service;

import com.qsy.edifice.domain.entity.InspectionForm;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.entity.ProjectStage;
import com.qsy.edifice.domain.vo.ProjectDetailVo;
import com.qsy.edifice.mapper.InspectionFormMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectServiceImplTests {

    private ProjectMapper projectMapper;
    private ProjectStageService projectStageService;
    private InspectionFormMapper inspectionFormMapper;
    private ContractService contractService;
    private ProjectMemberService projectMemberService;
    private ProjectServiceImpl service;

    @BeforeEach
    void setUp() {
        projectMapper = mock(ProjectMapper.class);
        projectStageService = mock(ProjectStageService.class);
        inspectionFormMapper = mock(InspectionFormMapper.class);
        contractService = mock(ContractService.class);
        projectMemberService = mock(ProjectMemberService.class);

        service = new ProjectServiceImpl();
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "projectStageService", projectStageService);
        ReflectionTestUtils.setField(service, "inspectionFormMapper", inspectionFormMapper);
        ReflectionTestUtils.setField(service, "contractService", contractService);
        ReflectionTestUtils.setField(service, "projectMemberService", projectMemberService);
    }

    @Test
    void projectDetailIncludesPendingInspectionRatioForEachStage() {
        Project project = Project.builder()
                .projectId(10L)
                .projectName("测试项目")
                .build();
        ProjectStage stage = ProjectStage.builder()
                .projectStageId(20L)
                .projectId(10L)
                .stageName("成果编制")
                .stageStatus(1)
                .completionRatio(new BigDecimal("25"))
                .build();
        InspectionForm pending = inspection(100L, 20L, 0, "15");
        InspectionForm reviewing = inspection(101L, 20L, 1, "10");
        InspectionForm rejected = inspection(102L, 20L, 2, "50");

        when(projectMapper.selectById(10L)).thenReturn(project);
        when(projectStageService.getProjectStagesByProjectId(10L)).thenReturn(List.of(stage));
        when(inspectionFormMapper.selectByProjectId("10"))
                .thenReturn(List.of(pending, reviewing, rejected));
        when(projectMemberService.getProjectMembersByProjectId(10L)).thenReturn(Collections.emptyList());

        ProjectDetailVo detail = service.getProjectDetailById(10L, 1L, true);

        assertEquals(new BigDecimal("25"), detail.getProjectStages().get(0).getPendingInspectionRatio());
    }

    private InspectionForm inspection(Long id, Long stageId, int status, String ratio) {
        return InspectionForm.builder()
                .inspectionFormId(id)
                .projectStageId(stageId)
                .inspectionFormStatus(status)
                .completionRatio(new BigDecimal(ratio))
                .build();
    }
}
