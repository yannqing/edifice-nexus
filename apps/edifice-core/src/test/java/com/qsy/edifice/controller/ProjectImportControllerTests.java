package com.qsy.edifice.controller;

import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ProjectImportResultVo;
import com.qsy.edifice.exception.GlobalExceptionHandler;
import com.qsy.edifice.service.*;
import com.qsy.edifice.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProjectImportControllerTests.Config.class)
class ProjectImportControllerTests {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProjectController controller() { return new ProjectController(); }
    }

    @Autowired private ProjectController controller;
    @MockBean private ProjectService projects;
    @MockBean private SettlementProjectService settlements;
    @MockBean private ProjectTypeService types;
    @MockBean private ProjectStageTemplateService templates;
    @MockBean private ProjectStageService stages;
    @MockBean private ProjectExcelService excel;
    @MockBean private StoredProjectImportService stored;
    @MockBean private JwtUtils jwt;
    private MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        reset(excel, stored, jwt);
        when(jwt.getUserFromToken("import-token")).thenReturn(SysUser.builder().userId(42L).build());
        authenticate("menu:all-projects");
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void legacyMultipartForwardsOnlyFileIdsToSameAuthorizedImportService() throws Exception {
        var file = new MockMultipartFile("file", "projects.xlsx", "application/octet-stream", new byte[]{1});
        when(excel.importProjects(any(), eq(List.of(11L, 12L)), eq(42L), any())).thenReturn(result(2, 0));
        mvc.perform(multipart("/project/import").file(file).header("token", "import-token")
                        .param("contractFileIds", "11", "12"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failCount").value(0));
        verify(excel).importProjects(any(), eq(List.of(11L, 12L)), eq(42L), any(HttpServletRequest.class));
        verifyNoInteractions(stored);
    }

    @Test void legacyMultipartWithoutContractsStillDelegatesAndReturnsZeroSuccess() throws Exception {
        var file = new MockMultipartFile("file", "projects.xlsx", "application/octet-stream", new byte[]{1});
        when(excel.importProjects(any(), isNull(), eq(42L), any())).thenReturn(result(0, 1));
        mvc.perform(multipart("/project/import").file(file).header("token", "import-token")
                        .param("contractFileUrl", "https://example.test/contract.pdf"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failCount").value(1));
        verify(excel).importProjects(any(), isNull(), eq(42L), any(HttpServletRequest.class));
        verifyNoInteractions(projects);
    }

    @Test void storedEntryParsesUploadedFileIdsAndReturnsStructuredResult() throws Exception {
        when(stored.importFile(eq(7L), eq(List.of(11L, 12L)), eq(42L), any())).thenReturn(result(1, 1));
        mvc.perform(post("/project/import-file").header("token", "import-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"7\",\"contractFileIds\":[\"11\",\"12\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failCount").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value("Missing contract"));
        verify(stored).importFile(eq(7L), eq(List.of(11L, 12L)), eq(42L), any(HttpServletRequest.class));
        verifyNoInteractions(excel, projects);
    }

    @Test void bothImportEntriesRemainRestrictedToGlobalProjectManagement() {
        authenticate("menu:my-projects");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.importProjects(new MockMultipartFile("file", new byte[]{1}), List.of(1L), null));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.importStoredProject(new com.qsy.edifice.domain.dto.ImportProjectDto(), null));
        verifyNoInteractions(excel, stored, projects, jwt);
    }

    private ProjectImportResultVo result(int success, int failure) {
        return new ProjectImportResultVo(success, failure, failure > 0 ? List.of("Missing contract") : List.of(), "Import result");
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "42", "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }
}
