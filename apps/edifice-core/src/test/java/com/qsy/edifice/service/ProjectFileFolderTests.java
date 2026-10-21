package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.qsy.edifice.domain.entity.ProjectFileFolder;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ProjectFileFolderMapper;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ProjectFileFolderTests {
    SettlementProjectTests fixture;
    ProjectFileFolderService service;
    ProjectFileFolderMapper mapper;
    Long project;

    @BeforeEach void setup() throws Exception {
        fixture = new SettlementProjectTests(); fixture.setup();
        project = fixture.root("A", "100000");
        fixture.jdbc.execute("""
                CREATE TABLE project_file_folder (folder_id BIGINT PRIMARY KEY, project_id BIGINT NOT NULL,
                parent_folder_id BIGINT NOT NULL DEFAULT 0, folder_name VARCHAR(128) NOT NULL, created_by BIGINT NOT NULL,
                created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, is_delete INT DEFAULT 0,
                UNIQUE(project_id,parent_folder_id,folder_name,is_delete))
                """);
        fixture.jdbc.execute("CREATE TABLE approval_records (approval_record_id BIGINT)");
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(fixture.jdbc.getDataSource());
        var sessionFactory = Objects.requireNonNull(factory.getObject());
        sessionFactory.getConfiguration().addMapper(ProjectFileFolderMapper.class);
        mapper = new SqlSessionTemplate(sessionFactory).getMapper(ProjectFileFolderMapper.class);
        service = new ProjectFileFolderService();
        ReflectionTestUtils.setField(service, "folderMapper", mapper);
        ReflectionTestUtils.setField(service, "projectMapper", fixture.mapper);
        ReflectionTestUtils.setField(service, "projectMemberService", fixture.members);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    ProjectFileFolder create(Long parent, String name) {
        return fixture.tx.execute(status -> service.create(project, parent, name, 7L));
    }

    @Test void createsNestedFoldersWithoutApprovalAndReportsFullPaths() {
        var root = create(0L, "Drawings"); var child = create(root.getFolderId(), "First floor");
        assertEquals(2, service.list(project, 7L).size());
        assertEquals("Drawings/First floor", service.pathsFor(Set.of(child.getFolderId())).get(child.getFolderId()));
        assertEquals(0, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM approval_records", Integer.class));
        assertNull(service.validateFolder(project, 0L));
        assertNull(service.validateFolder(project, null));
    }

    @Test void existingDirectoriesAreReusedButManualDuplicateNamesAreRejected() {
        var root = create(null, "Drawings");
        assertThrows(BusinessException.class, () -> create(0L, "Drawings"));
        var result = fixture.tx.execute(status -> service.resolvePaths(project, 0L,
                List.of("Drawings", "Drawings/First floor", "Drawings/Second floor"), 7L));
        assertEquals(String.valueOf(root.getFolderId()), result.get("Drawings"));
        assertEquals(3, service.list(project, 7L).size());
        assertEquals(result, fixture.tx.execute(status -> service.resolvePaths(project, null,
                List.of("Drawings", "Drawings/First floor", "Drawings/Second floor"), 7L)));
    }

    @Test void foreignParentsAndParentMembershipCannotAuthorizeChildFolders() {
        Long other = fixture.child(project, "A1", "20000", 8L);
        var folder = fixture.tx.execute(status -> service.create(other, null, "Private", 8L));
        assertThrows(BusinessException.class, () -> create(folder.getFolderId(), "Invalid"));
        assertThrows(BusinessException.class, () -> service.validateFolder(project, folder.getFolderId()));
        assertThrows(BusinessException.class, () -> service.list(other, 7L));
        assertThrows(BusinessException.class, () -> fixture.tx.execute(status -> service.create(other, null, "Invalid", 7L)));
    }

    @Test void archivedProjectsRemainReadableButCannotCreateDirectories() {
        create(null, "Before archive");
        fixture.jdbc.update("UPDATE project SET archive_status=1 WHERE project_id=?", project);
        assertEquals(1, service.list(project, 7L).size());
        assertThrows(BusinessException.class, () -> create(null, "After archive"));
        authenticate("ROLE_SUPER_ADMIN");
        assertThrows(BusinessException.class, () -> create(null, "Admin after archive"));
    }

    @Test void unrelatedUsersAreDeniedAndFileAdministratorsKeepAccess() {
        assertThrows(BusinessException.class, () -> service.list(project, 99L));
        assertThrows(BusinessException.class, () -> fixture.tx.execute(status -> service.create(project, null, "Private", 99L)));
        authenticate("menu:project-files-approval");
        assertNotNull(fixture.tx.execute(status -> service.create(project, null, "Shared", 99L)));
        assertEquals(1, service.list(project, 99L).size());
    }

    @Test void invalidRelativePathsAreRejectedBeforeAnyDirectoryIsCreated() {
        for (String path : List.of("../Secret", "/absolute", "Normal//Invalid", "C:/Windows", "Good/.", "Good/..", "Good\\Bad", "Good/\u0000bad")) {
            assertThrows(BusinessException.class, () -> fixture.tx.execute(status -> service.resolvePaths(project, null,
                    List.of("Valid", path), 7L)), path);
        }
        assertEquals(0, service.list(project, 7L).size());
        assertThrows(BusinessException.class, () -> create(null, " "));
        assertThrows(BusinessException.class, () -> create(null, "x".repeat(129)));
    }

    @Test void depthLimitIncludesDestinationFolder() {
        String path = String.join("/", Collections.nCopies(20, "a"));
        String id = fixture.tx.execute(status -> service.resolvePaths(project, 0L, List.of(path), 7L)).get(path);
        assertThrows(BusinessException.class, () -> create(Long.valueOf(id), "b"));
        assertThrows(BusinessException.class, () -> fixture.tx.execute(status -> service.resolvePaths(project, Long.valueOf(id), List.of("b"), 7L)));
        assertEquals(20, service.list(project, 7L).size());
    }

    @Test void concurrentDirectoryUploadsReuseTheSameTree() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Map<String, String>> task = () -> {
                start.await(5, TimeUnit.SECONDS);
                return fixture.tx.execute(status -> service.resolvePaths(project, 0L,
                        List.of("Reports/January", "Reports/February"), 7L));
            };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(3, service.list(project, 7L).size());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test void directoryIdsAreSerializedAsStrings() throws Exception {
        var folder = create(null, "Reports");
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(folder);
        assertTrue(json.get("folderId").isTextual());
        assertEquals(String.valueOf(project), json.get("projectId").asText());
    }

    @Test void emptyOversizedAndMissingProjectRequestsAreRejected() {
        assertThrows(BusinessException.class, () -> fixture.tx.execute(status -> service.resolvePaths(project, null, List.of(), 7L)));
        assertThrows(BusinessException.class, () -> fixture.tx.execute(status -> service.resolvePaths(project, null, Collections.nCopies(201, "a"), 7L)));
        assertThrows(BusinessException.class, () -> service.list(-1L, 7L));
        assertThrows(BusinessException.class, () -> service.list(project, null));
        assertEquals(0, service.list(project, 7L).size());
    }

    void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "admin", "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }
}
