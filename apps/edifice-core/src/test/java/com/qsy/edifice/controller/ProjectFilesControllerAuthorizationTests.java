package com.qsy.edifice.controller;

import com.qsy.edifice.domain.dto.CreateProjectFileDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.dto.SubmitProjectFileBatchDto;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ApprovalRecordsMapper;
import com.qsy.edifice.service.ApprovalFlowService;
import com.qsy.edifice.service.ProjectFilesService;
import com.qsy.edifice.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProjectFilesControllerAuthorizationTests.Config.class)
class ProjectFilesControllerAuthorizationTests {
    @Autowired ProjectFilesController controller;
    @Autowired ApprovalFlowController genericController;
    @Autowired ProjectFilesService service;
    @Autowired ApprovalFlowService flow;
    @MockBean JwtUtils jwt;
    @Autowired ApprovalRecordsMapper records;
    HttpServletRequest request;

    @BeforeEach void setup() throws Exception {
        reset(service, flow, jwt, records);
        request = mock(HttpServletRequest.class);
        when(request.getHeader("token")).thenReturn("test-token");
        when(jwt.getUserFromToken("test-token")).thenReturn(SysUser.builder().userId(7L).build());
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "test-user", "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }

    @Test void projectPermissionsCanSaveAndSubmitDraftsUsingAuthenticatedOwner() throws Exception {
        var items = List.of(new CreateProjectFileDto(10L, null, 1L, "a.pdf", null, null, null));
        for (String authority : List.of("menu:my-projects", "menu:all-projects", "ROLE_SUPER_ADMIN")) {
            authenticate(authority);
            controller.saveDrafts(items, request);
            controller.submitDrafts(new SubmitProjectFileBatchDto(), request);
        }
        verify(service, times(3)).saveDrafts(items, 7L);
        verify(service, times(3)).submitDrafts(any(), eq(7L));
    }

    @Test void unrelatedMenuCannotSaveOrSubmitDrafts() {
        authenticate("menu:workbench");
        assertThrows(AccessDeniedException.class, () -> controller.saveDrafts(List.of(), request));
        assertThrows(AccessDeniedException.class, () -> controller.submitDrafts(new SubmitProjectFileBatchDto(), request));
        verifyNoInteractions(service);
    }

    @Test void genericEndpointsCannotBypassFileBatchStateEvenForAdmin() {
        authenticate("ROLE_SUPER_ADMIN");
        assertThrows(BusinessException.class, () -> genericController.submit(new SubmitApprovalDto("FILE", 10L, 8L, null), request));
        when(records.selectById(100L)).thenReturn(ApprovalRecords.builder().inspectionFormId(10L).bizTypeExt("file").build());
        var dto = new ApproveDto(); dto.setRecordId(100L); dto.setPass(true);
        assertThrows(BusinessException.class, () -> genericController.approve(dto, request));
        verifyNoInteractions(flow);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean ProjectFilesController controller() { return new ProjectFilesController(); }
        @Bean ApprovalFlowController genericController() { return new ApprovalFlowController(); }
        @Bean ProjectFilesService service() { return mock(ProjectFilesService.class); }
        @Bean ApprovalFlowService flow() { return mock(ApprovalFlowService.class); }
        @Bean ApprovalRecordsMapper records() { return mock(ApprovalRecordsMapper.class); }
    }
}
