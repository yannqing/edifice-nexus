package com.qsy.edifice.controller;

import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.CreateProjectDto;
import com.qsy.edifice.domain.dto.UpdateProjectDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.entity.ProjectStage;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.service.ProjectExcelService;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.service.ProjectStageService;
import com.qsy.edifice.service.ProjectStageTemplateService;
import com.qsy.edifice.service.ProjectTypeService;
import com.qsy.edifice.service.SettlementProjectService;
import com.qsy.edifice.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProjectControllerAuthorizationTests.TestConfig.class)
class ProjectControllerAuthorizationTests {

    @Autowired
    private ProjectController projectController;

    @Autowired private ProjectTreeController projectTreeController;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectStageService projectStageService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private SettlementProjectService settlementProjectService;

    @MockBean
    private com.qsy.edifice.service.StoredProjectImportService storedProjectImportService;

    @BeforeEach
    void resetMocks() {
        reset(projectService, jwtUtils, settlementProjectService, projectStageService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void myProjectsPermissionCanCreateProject() throws Exception {
        authenticateWith("menu:my-projects");
        CreateProjectDto dto = new CreateProjectDto();
        HttpServletRequest request = mock(HttpServletRequest.class);
        SysUser user = SysUser.builder().userId(42L).build();

        when(request.getHeader("token")).thenReturn("access-token");
        when(jwtUtils.getUserFromToken("access-token")).thenReturn(user);
        when(projectService.createProject(dto, 42L)).thenReturn(99L);

        BaseResponse<Long> response = projectController.createProject(dto, request);

        assertEquals(200, response.getCode());
        assertEquals(99L, response.getData());
        verify(projectService).createProject(dto, 42L);
    }

    @Test
    void unrelatedPermissionCannotCreateProject() {
        authenticateWith("menu:workbench");

        assertThrows(
                AccessDeniedException.class,
                () -> projectController.createProject(new CreateProjectDto(), mock(HttpServletRequest.class))
        );
        verifyNoInteractions(jwtUtils, projectService);
    }

    @Test
    void myProjectsCreationDoesNotGrantGlobalParentManagement() throws Exception {
        authenticateWith("menu:my-projects");
        CreateProjectDto dto = new CreateProjectDto();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("token")).thenReturn("access-token");
        when(jwtUtils.getUserFromToken("access-token")).thenReturn(SysUser.builder().userId(42L).build());
        projectController.createSettlementProject(10L, dto, request);
        verify(settlementProjectService).create(10L, dto, 42L, false);
    }

    @Test
    void projectAdministratorCanManageAllParents() throws Exception {
        authenticateWith("menu:all-projects");
        CreateProjectDto dto = new CreateProjectDto();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("token")).thenReturn("access-token");
        when(jwtUtils.getUserFromToken("access-token")).thenReturn(SysUser.builder().userId(42L).build());
        projectController.createSettlementProject(10L, dto, request);
        verify(settlementProjectService).create(10L, dto, 42L, true);
    }

    @Test
    void unrelatedPermissionCannotCreateSettlementProject() {
        authenticateWith("menu:workbench");
        assertThrows(AccessDeniedException.class, () -> projectController.createSettlementProject(
                10L, new CreateProjectDto(), mock(HttpServletRequest.class)));
        verifyNoInteractions(jwtUtils, settlementProjectService);
    }

    @Test
    void myProjectsManagerStageActionsRequireObjectAuthorization() throws Exception {
        authenticateWith("menu:my-projects");
        var request = mock(HttpServletRequest.class);
        when(request.getHeader("token")).thenReturn("access-token");
        when(jwtUtils.getUserFromToken("access-token")).thenReturn(SysUser.builder().userId(42L).build());
        when(projectStageService.getProjectStageById(20L)).thenReturn(ProjectStage.builder().projectId(10L).build());
        projectController.startStages(List.of(20L), request);
        verify(projectService).ensureProjectManager(10L, 42L, false);
        verify(projectStageService).startStages(java.util.Set.of(20L));
        doThrow(new BusinessException("not a manager")).when(projectService).ensureProjectManager(10L, 42L, false);
        assertThrows(BusinessException.class, () -> projectController.restartStage(20L, request));
        verify(projectStageService, org.mockito.Mockito.never()).restartStage(20L);
    }

    @Test
    void myProjectsManagerCanEditOwnProjectWithoutGlobalPermission() throws Exception {
        authenticateWith("menu:my-projects");
        var dto = new UpdateProjectDto(); dto.setProjectId(10L);
        projectController.updateProjectFull(dto, authenticatedRequest());
        verify(projectService).ensureProjectManager(10L, 42L, false);
        verify(projectService).updateProjectFull(dto);
    }

    @Test
    void myProjectsPermissionDoesNotAllowEditingAnotherManagersProject() throws Exception {
        authenticateWith("menu:my-projects");
        var dto = new UpdateProjectDto(); dto.setProjectId(10L);
        doThrow(new BusinessException("not a manager")).when(projectService).ensureProjectManager(10L, 42L, false);
        assertThrows(BusinessException.class, () -> projectController.updateProjectFull(dto, authenticatedRequest()));
        verify(projectService, never()).updateProjectFull(any());
    }

    @Test
    void myProjectsManagerCanChangeCoefficientWithoutOverwritingStageProgress() throws Exception {
        authenticateWith("menu:my-projects");
        var request = authenticatedRequest();
        when(projectStageService.getProjectStageById(20L)).thenReturn(ProjectStage.builder()
                .projectStageId(20L).projectId(10L).completionRatio(new BigDecimal("40")).stageStatus(1).build());
        projectController.updateStageCoefficient(20L, new BigDecimal("1.05"), request);
        verify(projectService).ensureProjectManager(10L, 42L, false);
        var update = org.mockito.ArgumentCaptor.forClass(ProjectStage.class);
        verify(projectStageService).updateProjectStage(update.capture());
        assertEquals(new BigDecimal("1.05"), update.getValue().getCoefficient());
        org.junit.jupiter.api.Assertions.assertNull(update.getValue().getCompletionRatio());
        org.junit.jupiter.api.Assertions.assertNull(update.getValue().getStageStatus());
    }

    @Test
    void nonManagerCannotChangeCoefficientAndInvalidCoefficientsAreRejected() throws Exception {
        authenticateWith("menu:my-projects");
        var request = authenticatedRequest();
        when(projectStageService.getProjectStageById(20L)).thenReturn(ProjectStage.builder().projectId(10L).build());
        doThrow(new BusinessException("not a manager")).when(projectService).ensureProjectManager(10L, 42L, false);
        assertThrows(BusinessException.class, () -> projectController.updateStageCoefficient(20L, BigDecimal.ONE, request));
        assertThrows(BusinessException.class, () -> projectController.updateStageCoefficient(20L, BigDecimal.ZERO, request));
        assertThrows(BusinessException.class, () -> projectController.updateStageCoefficient(20L, new BigDecimal("100"), request));
        verify(projectStageService, never()).updateProjectStage(any());
    }

    @Test
    void globalProjectAdministratorKeepsProjectEditingAuthority() throws Exception {
        authenticateWith("menu:all-projects");
        var dto = new UpdateProjectDto(); dto.setProjectId(10L);
        projectController.updateProjectFull(dto, authenticatedRequest());
        verify(projectService).ensureProjectManager(10L, 42L, true);
        verify(projectService).updateProjectFull(dto);
    }

    @Test
    void unrelatedMenuDoesNotAuthorizeProjectEditingOrCoefficientChanges() {
        authenticateWith("menu:workbench");
        assertThrows(AccessDeniedException.class, () -> projectController.updateProjectFull(
                new UpdateProjectDto(), mock(HttpServletRequest.class)));
        assertThrows(AccessDeniedException.class, () -> projectController.updateStageCoefficient(
                20L, BigDecimal.ONE, mock(HttpServletRequest.class)));
        verifyNoInteractions(projectService, projectStageService, jwtUtils);
    }

    @Test void treeAllEndpointsRequireGlobalPermission() {
        authenticateWith("menu:my-projects");
        var q = new com.qsy.edifice.domain.dto.GetAllProjectListDto();
        var request = mock(HttpServletRequest.class);
        assertThrows(AccessDeniedException.class, () -> projectTreeController.all(q, request));
        assertThrows(AccessDeniedException.class, () -> projectTreeController.allChildren(1L, q, request));
        assertThrows(AccessDeniedException.class, () -> projectTreeController.allStatistics(q, request));
        verifyNoInteractions(projectService, jwtUtils);
    }

    @Test void mineTreeNeverUsesGlobalVisibilityEvenForAdministrator() throws Exception {
        authenticateWith("ROLE_SUPER_ADMIN");
        var q = new com.qsy.edifice.domain.dto.GetAllProjectListDto();
        var request = authenticatedRequest();
        projectTreeController.mine(q, request);
        projectTreeController.mineChildren(1L, q, request);
        projectTreeController.mineStatistics(q, request);
        verify(projectService).getProjectTree(q, 42L, false);
        verify(projectService).getProjectTreeChildren(1L, q, 42L, false);
        verify(projectService).getProjectTreeStatistics(q, 42L, false);
    }

    @Test void allTreeUsesAuthenticatedIdentityAndExistingGlobalPermission() throws Exception {
        authenticateWith("menu:all-projects");
        var q = new com.qsy.edifice.domain.dto.GetAllProjectListDto();
        var request = authenticatedRequest();
        projectTreeController.all(q, request);
        projectTreeController.allChildren(1L, q, request);
        projectTreeController.allStatistics(q, request);
        verify(projectService).getProjectTree(q, 42L, true);
        verify(projectService).getProjectTreeChildren(1L, q, 42L, true);
        verify(projectService).getProjectTreeStatistics(q, 42L, true);
    }

    @Test void anonymousCannotReadMineTreeOrItsCounts() {
        SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.AnonymousAuthenticationToken(
                "test-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        var q = new com.qsy.edifice.domain.dto.GetAllProjectListDto();
        var request = mock(HttpServletRequest.class);
        assertThrows(AccessDeniedException.class, () -> projectTreeController.mine(q, request));
        assertThrows(AccessDeniedException.class, () -> projectTreeController.mineChildren(1L, q, request));
        assertThrows(AccessDeniedException.class, () -> projectTreeController.mineStatistics(q, request));
        verifyNoInteractions(projectService, jwtUtils);
    }

    private HttpServletRequest authenticatedRequest() throws Exception {
        var request = mock(HttpServletRequest.class);
        when(request.getHeader("token")).thenReturn("access-token");
        when(jwtUtils.getUserFromToken("access-token")).thenReturn(SysUser.builder().userId(42L).build());
        return request;
    }

    private void authenticateWith(String authority) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                "test-user",
                "n/a",
                List.of(new SimpleGrantedAuthority(authority))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfig {

        @Bean ProjectTreeController projectTreeController() { return new ProjectTreeController(); }

        @Bean
        ProjectController projectController() {
            return new ProjectController();
        }

        @Bean
        ProjectService projectService() {
            return mock(ProjectService.class);
        }

        @Bean
        ProjectTypeService projectTypeService() {
            return mock(ProjectTypeService.class);
        }

        @Bean
        ProjectStageTemplateService projectStageTemplateService() {
            return mock(ProjectStageTemplateService.class);
        }

        @Bean
        ProjectExcelService projectExcelService() {
            return mock(ProjectExcelService.class);
        }

        @Bean
        ProjectStageService projectStageService() {
            return mock(ProjectStageService.class);
        }

    }
}
