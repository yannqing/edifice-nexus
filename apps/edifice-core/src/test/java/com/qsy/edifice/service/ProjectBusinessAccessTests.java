package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.CreateAcceptanceDto;
import com.qsy.edifice.domain.dto.TimesheetDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ProjectAcceptanceMapper;
import com.qsy.edifice.mapper.TimesheetMapper;
import com.qsy.edifice.service.impl.AcceptanceServiceImpl;
import com.qsy.edifice.service.impl.TimesheetServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectBusinessAccessTests {
    SettlementProjectTests fixture;
    AcceptanceServiceImpl acceptance;
    TimesheetServiceImpl timesheets;
    ProjectAcceptanceMapper acceptanceMapper;
    TimesheetMapper timesheetMapper;
    ApprovalFlowService flow;
    Long parent, child, parentStage, childStage;

    @BeforeEach void setup() throws Exception {
        fixture = new SettlementProjectTests(); fixture.setup();
        parent = fixture.root("A", "1000000");
        child = fixture.child(parent, "A1", "120000", 8L);
        parentStage = fixture.stageService.getProjectStagesByProjectId(parent).get(0).getProjectStageId();
        childStage = fixture.stageService.getProjectStagesByProjectId(child).get(0).getProjectStageId();
        acceptance = new AcceptanceServiceImpl(); timesheets = new TimesheetServiceImpl();
        acceptanceMapper = mock(ProjectAcceptanceMapper.class); timesheetMapper = mock(TimesheetMapper.class);
        flow = mock(ApprovalFlowService.class);
        ReflectionTestUtils.setField(acceptance, "projectService", fixture.projects);
        ReflectionTestUtils.setField(acceptance, "acceptanceMapper", acceptanceMapper);
        ReflectionTestUtils.setField(acceptance, "approvalFlowService", flow);
        ReflectionTestUtils.setField(acceptance, "projectMemberService", fixture.members);
        ReflectionTestUtils.setField(timesheets, "projectService", fixture.projects);
        ReflectionTestUtils.setField(timesheets, "timesheetMapper", timesheetMapper);
        when(acceptanceMapper.insert(any(ProjectAcceptance.class))).thenAnswer(call -> {
            ((ProjectAcceptance) call.getArgument(0)).setAcceptanceId(100L); return 1;
        });
        when(timesheetMapper.insert(any(Timesheet.class))).thenAnswer(call -> {
            ((Timesheet) call.getArgument(0)).setTimesheetId(200L); return 1;
        });
        when(flow.submit(any(), anyLong())).thenReturn(ApprovalRecords.builder().approvalRecordId(300L).build());
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void childMemberCanSubmitOwnAcceptanceAndTimesheet() {
        authenticate("menu:oa-applications", "menu:timesheet");
        assertEquals(100L, acceptance.createAndSubmit(acceptance(child, childStage), 8L));
        assertEquals(200L, timesheets.saveOrUpdateTimesheet(timesheet(child, childStage), 8L));
    }

    @Test void parentMembershipDoesNotAuthorizeChildBusiness() {
        authenticate("menu:oa-applications", "menu:timesheet");
        assertThrows(BusinessException.class, () -> acceptance.createAndSubmit(acceptance(child, childStage), 7L));
        assertThrows(BusinessException.class, () -> timesheets.saveOrUpdateTimesheet(timesheet(child, childStage), 7L));
        verifyNoInteractions(acceptanceMapper, timesheetMapper, flow);
    }

    @Test void parentCannotUseChildStageAndChildCannotUseParentStage() {
        assertThrows(BusinessException.class, () -> acceptance.createAndSubmit(acceptance(parent, childStage), 7L));
        assertThrows(BusinessException.class, () -> timesheets.saveOrUpdateTimesheet(timesheet(parent, childStage), 7L));
        assertThrows(BusinessException.class, () -> acceptance.createAndSubmit(acceptance(child, parentStage), 8L));
        assertThrows(BusinessException.class, () -> timesheets.saveOrUpdateTimesheet(timesheet(child, parentStage), 8L));
        verifyNoInteractions(acceptanceMapper, timesheetMapper, flow);
    }

    @Test void projectAdministratorMayActWithoutMembershipButCannotMixStages() {
        authenticate("menu:all-projects");
        assertEquals(100L, acceptance.createAndSubmit(acceptance(child, childStage), 99L));
        assertEquals(200L, timesheets.saveOrUpdateTimesheet(timesheet(child, childStage), 99L));
        assertThrows(BusinessException.class, () -> acceptance.createAndSubmit(acceptance(parent, childStage), 99L));
        assertThrows(BusinessException.class, () -> timesheets.saveOrUpdateTimesheet(timesheet(parent, childStage), 99L));
    }

    @Test void archivedProjectsRejectBusinessEvenForSuperAdministrator() {
        authenticate("ROLE_SUPER_ADMIN");
        fixture.jdbc.update("UPDATE project SET archive_status=1 WHERE project_id=?", child);
        assertThrows(BusinessException.class, () -> acceptance.createAndSubmit(acceptance(child, childStage), 99L));
        assertThrows(BusinessException.class, () -> timesheets.saveOrUpdateTimesheet(timesheet(child, childStage), 99L));
        verifyNoInteractions(acceptanceMapper, timesheetMapper, flow);
    }

    @Test void missingProjectsAndStagesAreRejectedBeforePersistence() {
        assertThrows(BusinessException.class, () -> acceptance.createAndSubmit(acceptance(-1L, childStage), 8L));
        assertThrows(BusinessException.class, () -> timesheets.saveOrUpdateTimesheet(timesheet(child, -1L), 8L));
        verifyNoInteractions(acceptanceMapper, timesheetMapper, flow);
    }

    @Test void projectLevelBusinessMayOmitOptionalStage() {
        CreateAcceptanceDto dto = acceptance(child, null); dto.setAcceptanceType(0);
        assertEquals(100L, acceptance.createAndSubmit(dto, 8L));
        assertEquals(200L, timesheets.saveOrUpdateTimesheet(timesheet(child, null), 8L));
        dto.setAcceptanceType(2);
        assertThrows(BusinessException.class, () -> acceptance.createAndSubmit(dto, 8L));
    }

    @Test void existingTimesheetCannotBeMovedIntoUnauthorizedProject() {
        TimesheetDto dto = timesheet(parent, parentStage); dto.setTimesheetId(200L);
        when(timesheetMapper.selectById(200L)).thenReturn(Timesheet.builder()
                .timesheetId(200L).projectId(child).userId(8L).build());
        assertThrows(BusinessException.class, () -> timesheets.saveOrUpdateTimesheet(dto, 8L));
        verify(timesheetMapper, never()).updateById(any(Timesheet.class));
    }

    @Test void projectMembershipDoesNotPermitEditingAnotherPersonsTimesheet() {
        TimesheetDto dto = timesheet(child, childStage); dto.setTimesheetId(200L);
        when(timesheetMapper.selectById(200L)).thenReturn(Timesheet.builder()
                .timesheetId(200L).projectId(child).userId(7L).build());
        assertThrows(BusinessException.class, () -> timesheets.saveOrUpdateTimesheet(dto, 8L));
        verify(timesheetMapper, never()).updateById(any(Timesheet.class));
    }

    @Test void archivedTimesheetCannotBeMovedOutOfItsOriginalProject() {
        Long other = fixture.child(parent, "A2", "50000", 8L);
        TimesheetDto dto = timesheet(other, null); dto.setTimesheetId(200L);
        when(timesheetMapper.selectById(200L)).thenReturn(Timesheet.builder()
                .timesheetId(200L).projectId(child).projectStageId(childStage).userId(8L).build());
        fixture.jdbc.update("UPDATE project SET archive_status=1 WHERE project_id=?", child);
        assertThrows(BusinessException.class, () -> timesheets.saveOrUpdateTimesheet(dto, 8L));
        verify(timesheetMapper, never()).updateById(any(Timesheet.class));
    }

    CreateAcceptanceDto acceptance(Long project, Long stage) {
        return new CreateAcceptanceDto(project, stage, 2, "Acceptance", null, null, 9L);
    }

    TimesheetDto timesheet(Long project, Long stage) {
        return new TimesheetDto(null, project, stage, 1, LocalDate.of(2026, 10, 21), BigDecimal.ONE, "Work", 1);
    }

    void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "test-user", "n/a", java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
