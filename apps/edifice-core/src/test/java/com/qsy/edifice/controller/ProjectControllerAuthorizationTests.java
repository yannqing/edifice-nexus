package com.qsy.edifice.controller;

import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.CreateProjectDto;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProjectControllerAuthorizationTests.TestConfig.class)
class ProjectControllerAuthorizationTests {

    @Autowired
    private ProjectController projectController;

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
