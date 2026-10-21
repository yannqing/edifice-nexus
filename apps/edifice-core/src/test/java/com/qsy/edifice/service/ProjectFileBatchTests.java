package com.qsy.edifice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.qsy.edifice.config.MybatisPlusConfig;
import com.qsy.edifice.domain.dto.*;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.ApprovalRecordVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.*;
import com.qsy.edifice.service.impl.ProjectFilesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real SQL/row locks/transactions for draft membership, with external services mocked. */
class ProjectFileBatchTests {
    ProjectFilesServiceImpl service;
    ProjectFilesMapper mapper;
    FilesMapper files;
    ApprovalFlowService flow;
    ApprovalRecordsMapper records;
    ProjectMapper projects;
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    Map<Long, Files> sourceFiles;

    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE project_files (
                  project_file_id BIGINT PRIMARY KEY, project_id VARCHAR(64), project_stage_id BIGINT, file_id BIGINT,
                  file_name VARCHAR(255), upload_user_id BIGINT, file_category VARCHAR(64), description VARCHAR(512),
                  approval_status INT DEFAULT 0 NOT NULL, current_record_id BIGINT,
                  batch_header INT DEFAULT 0 NOT NULL, batch_id BIGINT, draft_expires_at TIMESTAMP, submitted_time TIMESTAMP,
                  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  is_delete INT DEFAULT 0 NOT NULL)
                """);
        jdbc.execute("CREATE TABLE project_member (project_id BIGINT, user_id BIGINT, is_delete INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE approval_records (inspection_form_id BIGINT, biz_type_ext VARCHAR(32), approver BIGINT, is_delete INT DEFAULT 0)");
        var factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(source);
        factoryBean.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/ProjectFilesMapper.xml"));
        factoryBean.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor());
        var factory = factoryBean.getObject();
        assertNotNull(factory);
        if (!factory.getConfiguration().hasMapper(ProjectFilesMapper.class)) factory.getConfiguration().addMapper(ProjectFilesMapper.class);
        factory.getConfiguration().addMapper(FilesMapper.class);
        mapper = new SqlSessionTemplate(factory).getMapper(ProjectFilesMapper.class);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        service = new ProjectFilesServiceImpl();
        files = mock(FilesMapper.class); projects = mock(ProjectMapper.class);
        flow = mock(ApprovalFlowService.class); records = mock(ApprovalRecordsMapper.class);
        var members = mock(ProjectMemberService.class);
        var users = mock(SysUserMapper.class);
        ReflectionTestUtils.setField(service, "projectFilesMapper", mapper);
        ReflectionTestUtils.setField(service, "filesMapper", files);
        ReflectionTestUtils.setField(service, "projectMapper", projects);
        ReflectionTestUtils.setField(service, "projectMemberService", members);
        ReflectionTestUtils.setField(service, "projectStageMapper", mock(ProjectStageMapper.class));
        ReflectionTestUtils.setField(service, "sysUserMapper", users);
        ReflectionTestUtils.setField(service, "approvalFlowService", flow);
        ReflectionTestUtils.setField(service, "approvalRecordsMapper", records);
        Project project = new Project(); project.setProjectId(10L); project.setArchiveStatus(0);
        when(projects.selectById(10L)).thenReturn(project);
        when(projects.selectBatchIds(anyCollection())).thenReturn(List.of(project));
        when(members.getProjectMemberByProjectIdAndUserId(10L, 7L)).thenReturn(new ProjectMember());
        when(flow.queryChain(any(), anyLong())).thenReturn(List.of());
        AtomicLong recordIds = new AtomicLong(1000);
        when(flow.submit(any(), eq(7L))).thenAnswer(call -> {
            SubmitApprovalDto dto = call.getArgument(0);
            return ApprovalRecords.builder().approvalRecordId(recordIds.incrementAndGet()).inspectionFormId(dto.getBizId())
                    .bizTypeExt("file").applyUserId(7L).approver(8L).inspectionFormStatus(0).build();
        });
        sourceFiles = new HashMap<>();
        for (long id = 1; id <= 40; id++) {
            Files file = new Files(); file.setFileId(id); file.setUploadUserId(7L); file.setFileName("file-" + id + ".pdf");
            file.setDisplayName(file.getFileName()); file.setFileSize(100L); sourceFiles.put(id, file);
        }
        when(files.selectById(anyLong())).thenAnswer(call -> sourceFiles.get(call.getArgument(0)));
        when(files.selectBatchIds(anyCollection())).thenAnswer(call -> ((Collection<Long>)call.getArgument(0)).stream().map(sourceFiles::get).filter(Objects::nonNull).toList());
        when(files.selectOne(any())).thenAnswer(call -> {
            LambdaQueryWrapper<Files> query = call.getArgument(0); query.getSqlSegment();
            return sourceFiles.get(query.getParamNameValuePairs().values().iterator().next());
        });
    }

    <T> T tx(Supplier<T> action) { return transaction.execute(status -> action.get()); }
    List<String> save(long from, long to) {
        return tx(() -> service.saveDrafts(LongStream.rangeClosed(from, to)
                .mapToObj(id -> new CreateProjectFileDto(10L, null, id, "file-" + id + ".pdf", "图纸", null, null)).toList(), 7L));
    }
    SubmitProjectFileBatchDto batch(List<String> ids) {
        var dto = new SubmitProjectFileBatchDto(); dto.setProjectId(10L); dto.setFirstApproverId(8L);
        dto.setDraftIds(ids.stream().map(Long::valueOf).toList()); return dto;
    }

    @Test void combinesThreeAndNineteenAcrossDaysIntoOneApproval() {
        List<String> ids = new ArrayList<>(save(1, 3));
        jdbc.update("UPDATE project_files SET created_time = DATEADD('DAY', -4, CURRENT_TIMESTAMP), draft_expires_at = DATEADD('DAY', 26, CURRENT_TIMESTAMP)");
        ids.addAll(save(4, 22));
        assertEquals(22, service.listDrafts(10L, null, 1, 100, 7L).getTotal());
        Long batchId = tx(() -> service.submitDrafts(batch(ids), 7L));
        assertEquals(22, jdbc.queryForObject("SELECT COUNT(*) FROM project_files WHERE batch_id = ? AND approval_status = 1", Integer.class, batchId));
        assertNull(mapper.selectById(batchId).getFileId());
        verify(flow, times(1)).submit(any(), eq(7L));
        assertEquals(0, service.listDrafts(10L, null, 1, 100, 7L).getTotal());
        var detail = service.getDetail(batchId, 7L, false);
        assertEquals(22, detail.getBatchFiles().size());
        assertEquals(batchId, service.getDetail(Long.valueOf(ids.get(0)), 7L, false).getProjectFileId());
        var query = new GetProjectFileListDto(); query.setApplicationsOnly(true);
        assertEquals(1, service.listProjectFilesPage(query, 7L, true).getTotal());
        query.setApplicationsOnly(false); query.setProjectId(10L);
        assertEquals(22, service.listProjectFilesPage(query, 7L, true).getTotal());
        assertThrows(BusinessException.class, () -> tx(() -> service.submitDrafts(batch(ids), 7L)));
        assertThrows(BusinessException.class, () -> tx(() -> { service.removeDraft(Long.valueOf(ids.get(0)), 7L); return null; }));
    }

    @Test void rejectsForeignExpiredDuplicateAndCrossProjectDraftsAtomically() {
        List<String> ids = save(1, 3);
        assertThrows(BusinessException.class, () -> tx(() -> service.submitDrafts(batch(ids), 8L)));
        var duplicate = batch(List.of(ids.get(0), ids.get(0)));
        assertThrows(BusinessException.class, () -> tx(() -> service.submitDrafts(duplicate, 7L)));
        jdbc.update("UPDATE project_files SET draft_expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) WHERE project_file_id = ?", ids.get(2));
        assertThrows(BusinessException.class, () -> tx(() -> service.submitDrafts(batch(ids), 7L)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM project_files WHERE batch_header = 1", Integer.class));
        verify(flow, never()).submit(any(), any());
        assertEquals(1, tx(service::expireDrafts));
        assertEquals(2, service.listDrafts(10L, null, 1, 100, 7L).getTotal());
        assertThrows(BusinessException.class, () -> service.getDetail(Long.valueOf(ids.get(0)), 8L, true));
        jdbc.update("UPDATE project_files SET project_id = '20' WHERE project_file_id = ?", ids.get(1));
        assertThrows(BusinessException.class, () -> tx(() -> service.submitDrafts(batch(ids.subList(0, 2)), 7L)));
    }

    @Test void saveRetryPreservesDeadlineAndFailedSaveRollsBack() {
        List<String> ids = save(1, 3);
        var deadline = mapper.selectById(Long.valueOf(ids.get(0))).getDraftExpiresAt();
        assertEquals(new HashSet<>(ids), new HashSet<>(save(1, 3)));
        assertEquals(deadline, mapper.selectById(Long.valueOf(ids.get(0))).getDraftExpiresAt());
        sourceFiles.get(5L).setUploadUserId(99L);
        assertThrows(BusinessException.class, () -> save(4, 5));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM project_files", Integer.class));
        when(flow.submit(any(), any())).thenThrow(new BusinessException("审批流程不允许自审"));
        assertThrows(BusinessException.class, () -> tx(() -> service.submitDrafts(batch(ids), 7L)));
        assertEquals(3, service.listDrafts(10L, null, 1, 100, 7L).getTotal());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM project_files WHERE batch_header = 1", Integer.class));
    }

    @Test void wholeBatchApprovalUpdatesEveryMemberAndClearsCurrentRecord() {
        List<String> ids = save(1, 3);
        Long batchId = tx(() -> service.submitDrafts(batch(ids), 7L));
        ProjectFiles header = mapper.selectById(batchId);
        when(records.selectById(header.getCurrentRecordId())).thenReturn(ApprovalRecords.builder()
                .inspectionFormId(batchId).bizTypeExt("file").build());
        var dto = new ApproveDto(); dto.setRecordId(header.getCurrentRecordId()); dto.setPass(true);
        when(flow.approve(dto, 8L)).thenReturn(new ApprovalFlowService.ApprovalResult(dto.getRecordId(), ApprovalBizType.FILE, batchId, false, true, null));
        tx(() -> { service.approve(dto, 8L); return null; });
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM project_files WHERE approval_status = 2 AND current_record_id IS NULL", Integer.class));
        jdbc.update("UPDATE project_files SET draft_expires_at = DATEADD('DAY', -1, CURRENT_TIMESTAMP)");
        assertEquals(0, tx(service::expireDrafts));
        assertThrows(BusinessException.class, () -> tx(() -> { service.approve(dto, 8L); return null; }));
    }

    @Test void rejectionCopiesToNewDraftsWithoutChangingOriginalBatch() {
        List<String> ids = save(1, 3);
        Long batchId = tx(() -> service.submitDrafts(batch(ids), 7L));
        Long record = mapper.selectById(batchId).getCurrentRecordId();
        when(records.selectById(record)).thenReturn(ApprovalRecords.builder().inspectionFormId(batchId).bizTypeExt("file").build());
        var dto = new ApproveDto(); dto.setRecordId(record); dto.setPass(false);
        when(flow.approve(dto, 8L)).thenReturn(new ApprovalFlowService.ApprovalResult(record, ApprovalBizType.FILE, batchId, true, true, null));
        tx(() -> { service.approve(dto, 8L); return null; });
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM project_files WHERE approval_status = 3", Integer.class));
        var copies = tx(() -> service.copyToDrafts(batchId, 7L));
        assertEquals(3, copies.size());
        assertTrue(Collections.disjoint(ids, copies));
        assertEquals(3, service.getDetail(batchId, 7L, false).getBatchFiles().size());
        assertEquals(new HashSet<>(copies), new HashSet<>(tx(() -> service.copyToDrafts(batchId, 7L))));
    }

    @Test void concurrentSubmitCreatesOnlyOneBatch() throws Exception {
        List<String> ids = save(1, 3);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Boolean> run = () -> { start.await(); try { tx(() -> service.submitDrafts(batch(ids), 7L)); return true; }
                catch (BusinessException e) { return false; } };
            Future<Boolean> one = pool.submit(run), two = pool.submit(run); start.countDown();
            assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM project_files WHERE batch_header = 1", Integer.class));
            verify(flow, times(1)).submit(any(), any());
        } finally { pool.shutdownNow(); }
    }

    @Test void editDoesNotRenewExpiryAndCannotChangeOwnershipProjectOrSource() {
        Long id = Long.valueOf(save(1, 1).get(0));
        LocalDateTime expiry = mapper.selectById(id).getDraftExpiresAt();
        var edit = new CreateProjectFileDto(10L, null, 1L, "更名.pdf", "成果文件", "修改说明", null);
        tx(() -> { service.updateDraft(id, edit, 7L); return null; });
        assertEquals("更名.pdf", mapper.selectById(id).getFileName());
        assertEquals(expiry, mapper.selectById(id).getDraftExpiresAt());
        assertThrows(BusinessException.class, () -> tx(() -> { service.updateDraft(id, edit, 8L); return null; }));
        edit.setFileId(2L);
        assertThrows(BusinessException.class, () -> tx(() -> { service.updateDraft(id, edit, 7L); return null; }));
        ReflectionTestUtils.setField(service, "draftRetentionDays", 7);
        Long another = Long.valueOf(save(2, 2).get(0));
        assertTrue(mapper.selectById(another).getDraftExpiresAt().isBefore(LocalDateTime.now().plusDays(8)));
        assertEquals(7, service.getDraftRetentionDays());
    }

    @Test void forwardingAndWithdrawalAreAtomicForEntireBatch() {
        List<String> ids = save(1, 3);
        Long batchId = tx(() -> service.submitDrafts(batch(ids), 7L));
        Long firstRecord = mapper.selectById(batchId).getCurrentRecordId();
        when(records.selectById(firstRecord)).thenReturn(ApprovalRecords.builder().inspectionFormId(batchId).bizTypeExt("file").build());
        var dto = new ApproveDto(); dto.setRecordId(firstRecord); dto.setPass(true); dto.setNextApproverId(9L);
        when(flow.approve(dto, 8L)).thenReturn(new ApprovalFlowService.ApprovalResult(firstRecord, ApprovalBizType.FILE, batchId, false, false, 2000L));
        tx(() -> { service.approve(dto, 8L); return null; });
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM project_files WHERE approval_status = 1 AND current_record_id = 2000", Integer.class));
        ApprovalRecords next = ApprovalRecords.builder().approvalRecordId(2000L).inspectionFormId(batchId).bizTypeExt("file").build();
        when(flow.getCurrentPending(ApprovalBizType.FILE, batchId)).thenReturn(next);
        when(records.updatePendingResult(next)).thenReturn(1);
        assertThrows(BusinessException.class, () -> tx(() -> { service.cancel(batchId, 9L); return null; }));
        tx(() -> { service.cancel(Long.valueOf(ids.get(1)), 7L); return null; });
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM project_files WHERE approval_status = 4 AND current_record_id IS NULL AND is_delete = 0", Integer.class));
        assertEquals(3, tx(() -> service.copyToDrafts(batchId, 7L)).size());
    }

    @Test void invalidStageOrArchivedProjectCannotCreateOrSubmitDrafts() {
        var invalid = new CreateProjectFileDto(10L, 999L, 1L, "a.pdf", "图纸", null, null);
        assertThrows(BusinessException.class, () -> tx(() -> service.saveDrafts(List.of(invalid), 7L)));
        List<String> ids = save(1, 1);
        projects.selectById(10L).setArchiveStatus(1);
        assertThrows(BusinessException.class, () -> tx(() -> service.submitDrafts(batch(ids), 7L)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM project_files WHERE approval_status = 0", Integer.class));
    }

    @Test void applicationFiltersAndVisibilityDoNotExposeOtherUsersDrafts() {
        List<String> ids = save(1, 3);
        Long batchId = tx(() -> service.submitDrafts(batch(ids), 7L));
        save(4, 4);
        jdbc.update("INSERT INTO approval_records (inspection_form_id, biz_type_ext, approver) VALUES (?, 'file', 8)", batchId);
        var query = new GetProjectFileListDto(); query.setApplicationsOnly(true); query.setFileCategory("图纸"); query.setKeyword("file-2");
        assertEquals(1, service.listProjectFilesPage(query, 8L, false).getTotal());
        assertEquals(0, service.listProjectFilesPage(query, 9L, false).getTotal());
        assertEquals(1, service.getStatistics(8L, false).get("pending"));
        assertEquals(1, service.getStatistics(7L, true).get("total"));
        var chain = List.of(ApprovalRecordVo.builder().approver(8L).build());
        when(flow.queryChain(ApprovalBizType.FILE, batchId)).thenReturn(chain);
        assertEquals(3, service.getDetail(batchId, 8L, false).getBatchFiles().size());
        assertThrows(BusinessException.class, () -> service.getDetail(batchId, 9L, false));
        var members = (ProjectMemberService) ReflectionTestUtils.getField(service, "projectMemberService");
        when(members.getProjectMemberByProjectIdAndUserId(10L, 8L)).thenReturn(new ProjectMember());
        assertEquals(0, service.listDrafts(10L, null, 1, 10, 8L).getTotal());
    }

    @Test void historicalSingleFileApprovalKeepsItsOriginalIdAndChain() {
        var legacy = ProjectFiles.builder().projectFileId(700L).projectId("10").fileId(1L).uploadUserId(7L)
                .fileName("历史单文件.pdf").approvalStatus(1).currentRecordId(900L).batchHeader(0).build();
        mapper.insert(legacy);
        when(records.selectById(900L)).thenReturn(ApprovalRecords.builder().inspectionFormId(700L)
                .approvalRecordType(ApprovalBizType.FILE.getCode()).build());
        var dto = new ApproveDto(); dto.setRecordId(900L); dto.setPass(true);
        when(flow.approve(dto, 8L)).thenReturn(new ApprovalFlowService.ApprovalResult(900L, ApprovalBizType.FILE, 700L, false, true, null));
        tx(() -> { service.approve(dto, 8L); return null; });
        assertEquals(2, mapper.selectById(700L).getApprovalStatus());
        assertNull(mapper.selectById(700L).getCurrentRecordId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM project_files", Integer.class));
        var detail = service.getDetail(700L, 7L, false);
        assertFalse(detail.getBatchHeader()); assertEquals(1L, detail.getFileId());
    }

    @Test void historicalUnsubmittedFilesStayVisibleAndDoNotExpireAsNewDrafts() {
        mapper.insert(ProjectFiles.builder().projectFileId(701L).projectId("10").fileId(1L).uploadUserId(7L)
                .fileName("历史待提交.pdf").approvalStatus(0).batchHeader(0).build());
        save(2, 2);
        assertEquals(1, service.listProjectFiles(10L, null, null).size());
        assertEquals(1, service.getProjectFilesByProjectId("10").size());
        assertEquals(1, service.listProjectFilesPage(new GetProjectFileListDto(), 7L, true).getTotal());
        assertEquals(701L, service.getDetail(701L, 7L, false).getProjectFileId());
        assertEquals(1, service.listDrafts(10L, null, 1, 100, 7L).getTotal());
        assertEquals(0, tx(service::expireDrafts));
        assertEquals(0, mapper.selectById(701L).getApprovalStatus());
    }
}
