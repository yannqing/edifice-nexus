package com.qsy.edifice.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.qsy.edifice.controller.ProjectController;
import com.qsy.edifice.domain.dto.CreateProjectDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.excel.ProjectExcelData;
import com.qsy.edifice.domain.excel.ProjectMemberExcelData;
import com.qsy.edifice.domain.vo.ProjectImportResultVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.impl.FileServiceImpl;
import com.qsy.edifice.service.impl.ProjectExcelServiceImpl;
import com.qsy.edifice.service.impl.ProjectServiceImpl;
import com.qsy.edifice.service.support.FileObjectStorage;
import com.qsy.edifice.utils.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real EasyExcel parsing and file authorization; real project creation through a Spring transaction proxy. */
class ProjectExcelServiceImplTests {
    private ProjectExcelServiceImpl excel;
    private ProjectService creator;
    private FileServiceImpl files;
    private ProjectMapper projects;
    private ProjectMemberService members;
    private JdbcTemplate jdbc;
    private final Map<Long, Files> uploaded = new HashMap<>();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        // Keep the in-memory database alive across the separate per-row transactions.
        source.setUrl(source.getUrl() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE import_project (id BIGINT PRIMARY KEY, code VARCHAR(100), name VARCHAR(255))");
        jdbc.execute("CREATE TABLE import_contract (project_id BIGINT, file_id BIGINT)");
        jdbc.execute("CREATE TABLE import_stage (project_id BIGINT)");
        jdbc.execute("CREATE TABLE import_member (project_id BIGINT, user_id BIGINT)");
        projects = mock(ProjectMapper.class);
        AtomicLong ids = new AtomicLong(100);
        when(projects.insert(any(Project.class))).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            Project project = call.getArgument(0);
            project.setProjectId(ids.incrementAndGet());
            return jdbc.update("INSERT INTO import_project VALUES (?, ?, ?)", project.getProjectId(), project.getProjectCode(), project.getProjectName());
        });
        when(projects.selectByProjectCode(anyString())).thenAnswer(call -> jdbc.query(
                "SELECT id, code FROM import_project WHERE code = ?", (rs, row) -> Project.builder()
                        .projectId(rs.getLong("id")).projectCode(rs.getString("code")).build(),
                call.<String>getArgument(0)).stream().findFirst().orElse(null));

        var contracts = mock(ContractService.class);
        when(contracts.saveContract(any())).thenAnswer(call -> {
            Contract contract = call.getArgument(0);
            jdbc.update("INSERT INTO import_contract VALUES (?, ?)", contract.getProjectId(), contract.getContractFile());
            return true;
        });
        var templates = mock(ProjectStageTemplateService.class);
        when(templates.getEnabledByProjectTypeId(1L)).thenReturn(List.of(ProjectStageTemplate.builder()
                .stageName("Stage").stageOutput(new BigDecimal("100")).build()));
        var stages = mock(ProjectStageService.class);
        when(stages.saveProjectStage(any())).thenAnswer(call -> {
            ProjectStage stage = call.getArgument(0);
            jdbc.update("INSERT INTO import_stage VALUES (?)", stage.getProjectId());
            return true;
        });
        members = mock(ProjectMemberService.class);
        when(members.saveProjectMember(any())).thenAnswer(call -> {
            persistMember(call.getArgument(0));
            return true;
        });
        var users = mock(SysUserMapper.class);
        var user = SysUser.builder().userId(42L).realName("Manager").status(1).employmentStatus(1).build();
        when(users.selectList(null)).thenReturn(List.of(user));
        when(users.selectBatchIds(anyCollection())).thenReturn(List.of(user));
        var target = new ProjectServiceImpl();
        ReflectionTestUtils.setField(target, "projectMapper", projects);
        ReflectionTestUtils.setField(target, "contractService", contracts);
        ReflectionTestUtils.setField(target, "projectStageTemplateService", templates);
        ReflectionTestUtils.setField(target, "projectStageService", stages);
        ReflectionTestUtils.setField(target, "projectMemberService", members);
        ReflectionTestUtils.setField(target, "sysUserMapper", users);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        creator = (ProjectService) proxy.getProxy();

        files = new FileServiceImpl();
        var fileMapper = mock(FilesMapper.class);
        when(fileMapper.selectById(anyLong())).thenAnswer(call -> uploaded.get(call.getArgument(0)));
        ReflectionTestUtils.setField(files, "baseMapper", fileMapper);
        var jwt = mock(JwtUtils.class);
        request.addHeader("token", "import-token");
        when(jwt.getUserIdFromToken("import-token")).thenReturn(42L);
        ReflectionTestUtils.setField(files, "jwtUtils", jwt);
        ReflectionTestUtils.setField(files, "jdbcTemplate", mock(JdbcTemplate.class));
        authenticate("menu:all-projects");
        addFile(1L, "合同甲.pdf");
        addFile(2L, "合同乙.pdf");

        var types = mock(ProjectTypeService.class);
        when(types.getAllEnabledProjectTypes()).thenReturn(List.of(ProjectType.builder()
                .projectTypeId(1L).projectTypeCode("A").build()));
        excel = new ProjectExcelServiceImpl();
        ReflectionTestUtils.setField(excel, "projectMapper", projects);
        ReflectionTestUtils.setField(excel, "projectTypeService", types);
        ReflectionTestUtils.setField(excel, "sysUserMapper", users);
        ReflectionTestUtils.setField(excel, "projectService", creator);
        ReflectionTestUtils.setField(excel, "fileService", files);
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.execute("SHUTDOWN");
    }

    @Test void mapsMultipleContractsByServerOriginalNameNotUploadOrderOrDisplayName() throws Exception {
        uploaded.get(1L).setDisplayName("Renamed.pdf");
        var result = importRows(List.of(row("P1", " 合同甲.pdf "), row("P2", "合同乙.pdf")), List.of(2L, 1L), false);
        assertCounts(result, 2, 0);
        assertEquals(List.of(1L, 2L), jdbc.queryForList(
                "SELECT c.file_id FROM import_contract c JOIN import_project p ON c.project_id=p.id ORDER BY p.code", Long.class));
        assertEquals(2, count("import_member"));
        assertEquals(2, count("import_stage"));
    }

    @Test void generatedTemplateImportsSuccessfullyWithMatchingContract() throws Exception {
        var response = new MockHttpServletResponse();
        excel.downloadTemplate(response);
        List<ProjectExcelData> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .head(ProjectExcelData.class).sheet(0).doReadSync();
        assertEquals("示例合同.pdf", rows.get(0).getContractFileName());
        addFile(3L, "示例合同.pdf");
        var workbook = new MockMultipartFile("file", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", response.getContentAsByteArray());
        assertCounts(excel.importProjects(workbook, List.of(3L), 42L, request), 1, 0);
        assertEquals(3L, jdbc.queryForObject("SELECT file_id FROM import_contract", Long.class));
    }

    @Test void oldTemplateWithoutColumnAllowsOnlySingleProjectSingleContractFallback() throws Exception {
        assertCounts(importRows(List.of(row("P1", null)), List.of(1L), true), 1, 0);
        assertEquals(1L, jdbc.queryForObject("SELECT file_id FROM import_contract", Long.class));
    }

    @Test void oldTemplateWithMultipleProjectsNeverGuessesFromOneOrMultipleContracts() throws Exception {
        var rows = List.of(row("P1", null), row("P2", null));
        assertCounts(importRows(rows, List.of(1L), true), 0, 2);
        assertCounts(importRows(rows, List.of(1L, 2L), true), 0, 2);
        assertNoProjects();
    }

    @Test void missingAndMismatchedContractsFailWithoutCreatingProjects() throws Exception {
        assertCounts(importRows(List.of(row("P1", "合同甲.pdf")), List.of(), false), 0, 1);
        var mismatch = importRows(List.of(row("P1", "不存在.pdf")), List.of(1L), false);
        assertCounts(mismatch, 0, 1);
        assertTrue(mismatch.getErrors().get(0).contains("未找到"));
        assertNoProjects();
    }

    @Test void singleProjectCannotGuessAmongSeveralContracts() throws Exception {
        assertCounts(importRows(List.of(row("P1", null)), List.of(1L, 2L), false), 0, 1);
        assertNoProjects();
    }

    @Test void duplicateUploadedNamesAreRejectedBeforeAnyProjectCreation() {
        uploaded.get(2L).setOriginalName(" 合同甲.pdf");
        var error = assertThrows(BusinessException.class, () -> importRows(
                List.of(row("P1", "合同甲.pdf")), List.of(1L, 2L), false));
        assertTrue(error.getMessage().contains("文件名重复"));
        assertNoProjects();
    }

    @Test void invalidDuplicateOrExcessiveFileIdsAreRejected() {
        List<List<Long>> invalidIds = List.of(List.of(1L, 1L), Arrays.asList(1L, null), List.of(0L),
                List.of(-1L), LongStream.rangeClosed(1, 21).boxed().toList());
        for (var ids : invalidIds) {
            assertThrows(BusinessException.class, () -> importRows(List.of(row("P1", "合同甲.pdf")), ids, false));
        }
        assertNoProjects();
    }

    @Test void explicitContractNameCanBeReusedByMultipleProjects() throws Exception {
        var result = importRows(List.of(row("P1", "合同甲.pdf"), row("P2", " 合同甲.pdf ")), List.of(1L), false);
        assertCounts(result, 2, 0);
        assertEquals(List.of(1L, 1L), jdbc.queryForList("SELECT file_id FROM import_contract", Long.class));
    }

    @Test void globalDownloadPermissionDoesNotAllowAssociatingAnotherUsersFile() {
        uploaded.get(1L).setUploadUserId(99L);
        var error = assertThrows(BusinessException.class, () -> importRows(
                List.of(row("P1", "合同甲.pdf")), List.of(1L), false));
        assertTrue(error.getMessage().contains("当前用户上传"));
        assertNoProjects();
    }

    @Test void publicDownloadPermissionDoesNotAllowAssociatingAnotherUsersFile() {
        authenticate("menu:workbench");
        uploaded.get(1L).setPermissionLevel(0);
        uploaded.get(1L).setUploadUserId(99L);
        var error = assertThrows(BusinessException.class, () -> importRows(
                List.of(row("P1", "合同甲.pdf")), List.of(1L), false));
        assertTrue(error.getMessage().contains("当前用户上传"));
        assertNoProjects();
    }

    @Test void privateFileAccessCheckIsNotBypassed() {
        authenticate("menu:workbench");
        uploaded.get(1L).setUploadUserId(99L);
        var error = assertThrows(BusinessException.class, () -> importRows(
                List.of(row("P1", "合同甲.pdf")), List.of(1L), false));
        assertEquals(ErrorType.NO_AUTH_ERROR.getCode(), error.getCode());
        assertNoProjects();
    }

    @Test void missingIncompleteFailedAndDeletedFilesCannotBeAssociated() {
        assertThrows(BusinessException.class, () -> importRows(List.of(row("P1", "合同甲.pdf")), List.of(999L), false));
        for (int status : new int[]{0, 2}) {
            uploaded.get(1L).setStatus(status);
            assertThrows(BusinessException.class, () -> importRows(List.of(row("P1", "合同甲.pdf")), List.of(1L), false));
        }
        uploaded.get(1L).setStatus(1);
        uploaded.get(1L).setIsDeleted(1);
        assertThrows(BusinessException.class, () -> importRows(List.of(row("P1", "合同甲.pdf")), List.of(1L), false));
        assertNoProjects();
    }

    @Test void arbitraryUrlInWorkbookIsNotAContractAssociation() throws Exception {
        assertCounts(importRows(List.of(row("P1", "https://example.test/合同甲.pdf")), List.of(1L), false), 0, 1);
        assertNoProjects();
    }

    @Test void existingAndRepeatedExplicitProjectCodesAreRejected() throws Exception {
        assertCounts(importRows(List.of(row("P1", "合同甲.pdf")), List.of(1L), false), 1, 0);
        assertCounts(importRows(List.of(row("P1", "合同乙.pdf")), List.of(2L), false), 0, 1);
        var duplicates = importRows(List.of(row("P2", "合同甲.pdf"), row(" P2 ", "合同乙.pdf")), List.of(1L, 2L), false);
        assertCounts(duplicates, 0, 2);
        assertEquals(1, count("import_project"));
        assertTrue(duplicates.getErrors().stream().allMatch(error -> error.contains("项目编码重复或已存在")));
    }

    @Test void twoBlankProjectCodesInOneWorkbookReceiveDifferentGeneratedCodes() throws Exception {
        assertCounts(importRows(List.of(row(null, "合同甲.pdf"), row("  ", "合同乙.pdf")), List.of(1L, 2L), false), 2, 0);
        var codes = jdbc.queryForList("SELECT code FROM import_project", String.class);
        assertEquals(2, new HashSet<>(codes).size());
        assertTrue(codes.stream().allMatch(code -> code.matches("PJ[0-9]+")));
    }

    @Test void emptyWorkbookReturnsStructuredZeroSuccess() throws Exception {
        var result = importRows(List.of(), List.of(), false);
        assertCounts(result, 0, 0);
        assertEquals("Excel 项目信息为空", result.getMessage());
        assertNoProjects();
    }

    @Test void legacyMultipartControllerCannotBypassActualContractOwnershipChecks() throws Exception {
        var controller = new ProjectController();
        ReflectionTestUtils.setField(controller, "projectExcelService", excel);
        var jwt = mock(JwtUtils.class);
        when(jwt.getUserFromToken("import-token")).thenReturn(SysUser.builder().userId(42L).build());
        ReflectionTestUtils.setField(controller, "jwtUtils", jwt);
        var workbook = workbook(List.of(row("P1", "合同甲.pdf")), false);
        uploaded.get(1L).setUploadUserId(99L);
        assertThrows(BusinessException.class, () -> controller.importProjects(workbook, List.of(1L), request));
        assertCounts(controller.importProjects(workbook, null, request).getData(), 0, 1);
        assertNoProjects();
    }

    @Test void failedProjectRollsBackAllWritesAndNextProjectCanCommit() throws Exception {
        AtomicBoolean failed = new AtomicBoolean();
        doAnswer(call -> {
            persistMember(call.getArgument(0));
            if (!failed.getAndSet(true)) throw new BusinessException("成员写入失败");
            return true;
        }).when(members).saveProjectMember(any());
        var result = importRows(List.of(row("FAIL", "合同甲.pdf"), row("OK", "合同乙.pdf")), List.of(1L, 2L), false);
        assertCounts(result, 1, 1);
        assertTrue(result.getErrors().get(0).contains("成员写入失败"));
        assertEquals(List.of("OK"), jdbc.queryForList("SELECT code FROM import_project", String.class));
        for (String table : List.of("import_contract", "import_stage", "import_member")) assertEquals(1, count(table));
        assertEquals(2L, jdbc.queryForObject("SELECT file_id FROM import_contract", Long.class));
    }

    @Test void normalProjectCreationStillRequiresContractMainFile() {
        CreateProjectDto dto = new CreateProjectDto();
        dto.setProjectName("Project"); dto.setProjectType(1L); dto.setContractType(0);
        dto.setContractAmount(BigDecimal.TEN); dto.setProjectCharges(List.of(42L));
        var error = assertThrows(BusinessException.class, () -> creator.createProject(dto, 42L));
        assertEquals("请上传合同主文件", error.getMessage());
        assertNoProjects();
    }

    @Test void storedExcelUsesTheSameContractMappingAndAuthorization() throws Exception {
        var workbook = workbook(List.of(row("P1", "合同甲.pdf")), false);
        Files stored = addFile(3L, "项目.xlsx");
        stored.setFileExtension("xlsx"); stored.setFileSize(workbook.getSize());
        var storage = mock(FileObjectStorage.class);
        when(storage.open(stored)).thenAnswer(call -> workbook.getInputStream());
        var imports = new StoredProjectImportService();
        ReflectionTestUtils.setField(imports, "files", files);
        ReflectionTestUtils.setField(imports, "storage", storage);
        ReflectionTestUtils.setField(imports, "excel", excel);
        uploaded.get(1L).setUploadUserId(99L);
        assertThrows(BusinessException.class, () -> imports.importFile(3L, List.of(1L), 42L, request));
        assertNoProjects();
        uploaded.get(1L).setUploadUserId(42L);
        assertCounts(imports.importFile(3L, List.of(1L), 42L, request), 1, 0);
    }

    @Test void storedExcelMustAlsoBelongToImporter() {
        Files stored = addFile(3L, "项目.xlsx");
        stored.setFileExtension("xlsx"); stored.setUploadUserId(99L);
        var storage = mock(FileObjectStorage.class);
        var imports = new StoredProjectImportService();
        ReflectionTestUtils.setField(imports, "files", files);
        ReflectionTestUtils.setField(imports, "storage", storage);
        ReflectionTestUtils.setField(imports, "excel", excel);
        assertThrows(BusinessException.class, () -> imports.importFile(3L, List.of(1L), 42L, request));
        verifyNoInteractions(storage);
        assertNoProjects();
    }

    private ProjectImportResultVo importRows(List<ProjectExcelData> rows, List<Long> fileIds, boolean legacy) throws Exception {
        return excel.importProjects(workbook(rows, legacy), fileIds, 42L, request);
    }

    private MockMultipartFile workbook(List<ProjectExcelData> rows, boolean legacy) {
        var out = new ByteArrayOutputStream();
        var builder = EasyExcel.write(out);
        if (legacy) builder.excludeColumnFieldNames(Set.of("contractFileName"));
        try (ExcelWriter writer = builder.build()) {
            writer.write(rows, EasyExcel.writerSheet(0, "项目信息").head(ProjectExcelData.class).build());
            writer.write(List.of(), EasyExcel.writerSheet(1, "项目成员").head(ProjectMemberExcelData.class).build());
        }
        return new MockMultipartFile("file", "项目.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private ProjectExcelData row(String code, String contractName) {
        return ProjectExcelData.builder().projectName("Project " + code).projectCode(code).projectTypeCode("A")
                .contractType("基本收费").contractAmount(new BigDecimal("100"))
                .contractFileName(contractName).build();
    }

    private Files addFile(long id, String name) {
        Files file = new Files(); file.setFileId(id); file.setUploadUserId(42L);
        file.setOriginalName(name); file.setDisplayName(name); file.setFileSize(100L);
        file.setFileExtension("pdf"); file.setStatus(1); file.setIsDeleted(0); file.setPermissionLevel(1);
        uploaded.put(id, file); return file;
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "42", "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }

    private void persistMember(ProjectMember member) {
        jdbc.update("INSERT INTO import_member VALUES (?, ?)", member.getProjectId(), member.getUserId());
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    private void assertNoProjects() {
        for (String table : List.of("import_project", "import_contract", "import_stage", "import_member")) assertEquals(0, count(table));
    }

    private void assertCounts(ProjectImportResultVo result, int success, int failure) {
        assertEquals(success, result.getSuccessCount()); assertEquals(failure, result.getFailCount());
        assertEquals(Math.min(5, failure), result.getErrors().size());
    }
}
