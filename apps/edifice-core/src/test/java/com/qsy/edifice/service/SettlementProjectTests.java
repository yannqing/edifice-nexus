package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.qsy.edifice.config.MybatisPlusConfig;
import com.qsy.edifice.domain.dto.*;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.model.OutputAllocationContext;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.*;
import com.qsy.edifice.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettlementProjectTests {
    JdbcTemplate jdbc;
    ProjectMapper mapper;
    ProjectStageMapper stages;
    ProjectServiceImpl projects;
    ProjectStageServiceImpl stageService;
    SettlementProjectServiceImpl settlements;
    ContractService contracts;
    ProjectMemberService members;
    ProjectFilesMapper files;
    TransactionTemplate tx;
    Map<Long, Contract> contractByProject;

    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE project (project_id BIGINT PRIMARY KEY, parent_project_id BIGINT,
                  project_name VARCHAR(255), project_code VARCHAR(100), project_type BIGINT, project_status INT,
                  is_show INT, project_start_time TIMESTAMP, project_end_time TIMESTAMP,
                  archive_status INT DEFAULT 0, archive_time TIMESTAMP, archive_user_id BIGINT, archive_remark VARCHAR(512),
                  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  is_delete INT DEFAULT 0)
                """);
        jdbc.execute("""
                CREATE TABLE project_stage (project_stage_id BIGINT PRIMARY KEY, project_id BIGINT,
                  stage_name VARCHAR(100), stage_status INT, stage_output DECIMAL(12,4), benefit_inclusion_ratio DECIMAL(12,4),
                  completion_ratio DECIMAL(12,4), coefficient DECIMAL(12,4),
                  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  is_delete INT DEFAULT 0)
                """);
        jdbc.execute("CREATE TABLE project_member (project_id BIGINT, user_id BIGINT, project_role BIGINT, is_delete INT DEFAULT 0)");
        jdbc.execute("""
                CREATE TABLE contract (contract_id BIGINT PRIMARY KEY, project_id BIGINT, contract_type INT,
                  contract_amount DECIMAL(18,2), base_amount DECIMAL(18,2), benefit_amount DECIMAL(18,2), is_delete INT DEFAULT 0)
                """);
        var bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(source);
        bean.setMapperLocations(new ClassPathResource("mapper/ProjectMapper.xml"), new ClassPathResource("mapper/ProjectStageMapper.xml"));
        bean.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor());
        var factory = Objects.requireNonNull(bean.getObject());
        var sql = new SqlSessionTemplate(factory);
        mapper = sql.getMapper(ProjectMapper.class);
        stages = sql.getMapper(ProjectStageMapper.class);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        stageService = new ProjectStageServiceImpl();
        field(stageService, "projectMapper", mapper); field(stageService, "projectStageMapper", stages);
        projects = new ProjectServiceImpl();
        field(projects, "projectMapper", mapper); field(projects, "projectStageService", stageService);
        field(projects, "jdbcTemplate", jdbc);
        contracts = mock(ContractService.class); contractByProject = new HashMap<>();
        AtomicLong contractIds = new AtomicLong(10000);
        when(contracts.saveContract(any())).thenAnswer(call -> {
            Contract c = call.getArgument(0); c.setContractId(contractIds.incrementAndGet());
            contractByProject.put(c.getProjectId(), c);
            jdbc.update("INSERT INTO contract(contract_id,project_id,contract_type,contract_amount,base_amount,benefit_amount) VALUES(?,?,?,?,?,?)",
                    c.getContractId(), c.getProjectId(), c.getContractType(), c.getContractAmount(), c.getBaseAmount(), c.getBenefitAmount());
            return true;
        });
        when(contracts.getContractByProjectId(anyLong())).thenAnswer(call -> contractByProject.get(call.getArgument(0)));
        members = mock(ProjectMemberService.class);
        when(members.saveProjectMember(any())).thenAnswer(call -> {
            ProjectMember m = call.getArgument(0);
            jdbc.update("INSERT INTO project_member(project_id,user_id,project_role) VALUES(?,?,?)", m.getProjectId(), m.getUserId(), m.getProjectRole());
            return true;
        });
        when(members.getProjectMembersByProjectId(anyLong())).thenAnswer(call -> memberList(call.getArgument(0), null));
        when(members.getProjectMemberByProjectIdAndUserId(anyLong(), anyLong())).thenAnswer(call -> {
            List<ProjectMember> list = memberList(call.getArgument(0), call.getArgument(1));
            return list.isEmpty() ? null : list.get(0);
        });
        var templates = mock(ProjectStageTemplateService.class);
        when(templates.getEnabledByProjectTypeId(1L)).thenReturn(List.of(
                ProjectStageTemplate.builder().stageName("Planning").stageOutput(new BigDecimal("4")).build(),
                ProjectStageTemplate.builder().stageName("Delivery").stageOutput(new BigDecimal("96")).build()));
        var users = mock(SysUserMapper.class);
        when(users.selectBatchIds(anyCollection())).thenAnswer(call -> ((Collection<Long>) call.getArgument(0)).stream()
                .map(id -> SysUser.builder().userId(id).status(1).employmentStatus(1).build()).toList());
        files = mock(ProjectFilesMapper.class);
        field(projects, "contractService", contracts); field(projects, "projectMemberService", members);
        field(projects, "projectStageTemplateService", templates); field(projects, "sysUserMapper", users);
        field(projects, "projectTypeService", mock(ProjectTypeService.class));
        field(projects, "contractBenefitRevisionMapper", mock(ContractBenefitRevisionMapper.class));
        field(projects, "projectFilesMapper", files);
        field(projects, "inspectionFormMapper", mock(InspectionFormMapper.class));
        settlements = new SettlementProjectServiceImpl();
        field(settlements, "projectMapper", mapper); field(settlements, "projectService", projects);
        field(settlements, "projectMemberService", members);
    }

    @Test void createsIndependentContractsAndStagesWithoutRewritingParent() {
        Long parent = root("A", "1000000");
        Long a1 = child(parent, "A1", "120000", 7L);
        Long a2 = child(parent, "A2", "2500000", 7L);
        assertEquals(parent, mapper.selectById(a1).getParentProjectId());
        assertEquals(parent, mapper.selectByProjectCode("A1").getParentProjectId());
        assertEquals(2, stageService.getProjectStagesByProjectId(a1).size());
        assertEquals(2, stageService.getProjectStagesByProjectId(a2).size());
        Set<Long> childStages = new HashSet<>(stageService.getProjectStagesByProjectId(a1).stream().map(ProjectStage::getProjectStageId).toList());
        assertTrue(stageService.getProjectStagesByProjectId(parent).stream().noneMatch(s -> childStages.contains(s.getProjectStageId())));
        assertEquals(new BigDecimal("1000000"), contracts.getContractByProjectId(parent).getContractAmount());
        assertEquals(new BigDecimal("120000"), contracts.getContractByProjectId(a1).getContractAmount());
        assertEquals(new BigDecimal("2500000"), contracts.getContractByProjectId(a2).getContractAmount());
        var summary = mapper.selectSettlementSummary(parent, 7L, true);
        assertEquals(2L, summary.getProjectCount());
        assertEquals(new BigDecimal("2620000.00"), summary.getContractAmount());
        assertEquals("A", projects.getProjectDetailById(a1, 7L, false).getParentProjectName());
    }

    @Test void usesChildAmountsAndNeverBorrowsParentStageOrBenefit() {
        Long parent = root("A", "1000000");
        Long child = child(parent, "A1", "120000", 7L);
        ProjectStage parentStage = stageService.getProjectStagesByProjectId(parent).get(0);
        ProjectStage childStage = stageService.getProjectStagesByProjectId(child).get(0);
        childStage.setStageStatus(6); childStage.setCompletionRatio(new BigDecimal("100"));
        stageService.updateProjectStage(childStage);
        var output = new OutputValueServiceImpl();
        var rules = mock(BusinessRuleConfigService.class);
        when(rules.booleanValue("output", "require_stage_inspection_passed", true)).thenReturn(true);
        var outputs = mock(OutputValueMapper.class);
        when(outputs.selectList(any())).thenReturn(List.of());
        var allocation = mock(OutputAllocationRuleService.class);
        when(allocation.calculate(anyLong(), anyLong(), any())).thenReturn(new OutputAllocationContext(
                1L, 1, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
        field(output, "projectStageService", stageService); field(output, "contractService", contracts);
        field(output, "businessRuleConfigService", rules); field(output, "outputValueMapper", outputs);
        field(output, "outputAllocationRuleService", allocation);
        var preview = output.previewOutputValue(child, childStage.getProjectStageId());
        assertEquals(new BigDecimal("120000.00"), preview.getBaseAmount());
        assertEquals(new BigDecimal("20000.00"), preview.getBenefitAmount());
        assertEquals(new BigDecimal("5600.00"), preview.getCurrentStageAmount());
        assertEquals(0, stages.selectById(parentStage.getProjectStageId()).getStageStatus());
        assertThrows(BusinessException.class, () -> output.previewOutputValue(child, parentStage.getProjectStageId()));
        assertThrows(BusinessException.class, () -> output.previewOutputValue(parent, childStage.getProjectStageId()));
    }

    @Test void summaryAndPagesRespectChildMembershipAndNeverIncludeParentMoney() {
        Long parent = root("A", "1000000");
        Long a1 = child(parent, "A1", "120000", 7L);
        child(parent, "A2", "2500000", 8L);
        var visible = settlements.list(parent, new GetAllProjectListDto(), 7L, false, false);
        assertEquals(1L, visible.getProjects().getTotal());
        assertEquals(a1, visible.getProjects().getRecords().get(0).getProjectId());
        assertEquals(new BigDecimal("120000.00"), visible.getSummary().getContractAmount());
        assertTrue(visible.isCanCreate());
        var all = settlements.list(parent, new GetAllProjectListDto(), 99L, true, true);
        assertEquals(2L, all.getProjects().getTotal());
        assertThrows(BusinessException.class, () -> settlements.list(parent, new GetAllProjectListDto(), 99L, false, false));
        var dto = new GetAllProjectListDto(); dto.setPageSize(1); dto.setCurrent(2);
        assertEquals(1, settlements.list(parent, dto, 99L, true, true).getProjects().getRecords().size());
        GetMyProjectListDto mine = new GetMyProjectListDto(); mine.setProjectLevel(0);
        assertEquals(1L, projects.getMyProjectPage(7L, mine).getTotal());
        mine.setProjectLevel(1);
        assertEquals(1L, projects.getMyProjectPage(7L, mine).getTotal());
        mine.setParentProjectId(a1);
        assertEquals(0L, projects.getMyProjectPage(7L, mine).getTotal());
    }

    @Test void ordinaryMemberCannotCreateOrGainSiblingOrParentAccess() {
        Long parent = root("A", "1000000");
        jdbc.update("INSERT INTO project_member(project_id,user_id,project_role) VALUES(?,?,102)", parent, 9L);
        assertThrows(BusinessException.class, () -> tx.execute(status -> settlements.create(parent, dto("bad", "1", 9L), 9L, false)));
        assertFalse(settlements.list(parent, new GetAllProjectListDto(), 9L, false, false).isCanCreate());
        Long child = child(parent, "A1", "120000", 8L);
        assertThrows(BusinessException.class, () -> projects.getProjectDetailById(child, 7L, false));
        var detail = projects.getProjectDetailById(child, 8L, false);
        assertEquals(parent, detail.getParentProjectId());
        assertNull(detail.getParentProjectName());
        assertNull(detail.getParentProjectCode());
    }

    @Test void rejectsNestedArchivedAndMissingParentsAndProtectsDeletionAndArchive() {
        Long parent = root("A", "1000000");
        Long child = child(parent, "A1", "120000", 7L);
        assertThrows(BusinessException.class, () -> tx.execute(status -> settlements.create(child, dto("nested", "1", 7L), 7L, true)));
        assertThrows(BusinessException.class, () -> tx.execute(status -> settlements.create(-1L, dto("missing", "1", 7L), 7L, true)));
        assertThrows(BusinessException.class, () -> tx.execute(status -> projects.deleteProject(parent)));
        assertThrows(BusinessException.class, () -> tx.execute(status -> { projects.archiveProject(parent, 7L, null); return null; }));
        jdbc.update("UPDATE project SET archive_status=1 WHERE project_id=?", parent);
        assertThrows(BusinessException.class, () -> tx.execute(status -> settlements.create(parent, dto("bad", "1", 7L), 7L, true)));
        jdbc.update("UPDATE project SET archive_status=1 WHERE project_id=?", child);
        assertThrows(BusinessException.class, () -> tx.execute(status -> { projects.unarchiveProject(child); return null; }));
        assertEquals(1, mapper.selectById(child).getArchiveStatus());
    }

    @Test void failedCreationRollsBackChildContractStagesAndMembership() {
        Long parent = root("A", "1000000");
        doThrow(new IllegalStateException("failed member insert")).when(members).saveProjectMember(any());
        assertThrows(IllegalStateException.class, () -> child(parent, "bad", "120000", 7L));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM project", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM contract", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM project_stage", Integer.class));
    }

    @Test void managerCanCreateWithoutGlobalRightsAndGeneratedCodesAreIndependent() {
        Long parent = root("A", "1000000");
        var first = dto("A1", "100", 7L); first.setProjectCode(null);
        var second = dto("A2", "200", 7L); second.setProjectCode(null);
        Long a1 = tx.execute(status -> settlements.create(parent, first, 7L, false));
        Long a2 = tx.execute(status -> settlements.create(parent, second, 7L, false));
        assertNotEquals(mapper.selectById(a1).getProjectCode(), mapper.selectById(a2).getProjectCode());
        assertThrows(BusinessException.class, () -> child(parent, "A", "100", 7L));
    }

    @Test void approvingAllChildStagesLeavesParentAndSiblingUnchanged() {
        Long parent = root("A", "1000000");
        Long a1 = child(parent, "A1", "120000", 7L);
        Long a2 = child(parent, "A2", "2500000", 7L);
        var inspections = new InspectionFormServiceImpl();
        var forms = mock(InspectionFormMapper.class);
        var flow = mock(ApprovalFlowService.class);
        field(inspections, "projectService", projects); field(inspections, "projectStageService", stageService);
        field(inspections, "projectStageMapper", stages); field(inspections, "inspectionFormMapper", forms);
        field(inspections, "businessRuleConfigService", mock(BusinessRuleConfigService.class));
        field(inspections, "approvalFlowService", flow);
        when(forms.selectByProjectStageIdForUpdate(anyLong())).thenReturn(List.of());
        Map<Long, InspectionForm> stored = new HashMap<>();
        AtomicLong ids = new AtomicLong(50000);
        when(forms.insert(any(InspectionForm.class))).thenAnswer(call -> {
            InspectionForm form = call.getArgument(0); form.setInspectionFormId(ids.incrementAndGet());
            stored.put(form.getInspectionFormId(), form); return 1;
        });
        when(forms.selectById(anyLong())).thenAnswer(call -> stored.get(call.getArgument(0)));
        when(flow.submit(any(), anyLong())).thenReturn(ApprovalRecords.builder().approvalRecordId(100L).build());
        when(flow.getCurrentPending(eq(ApprovalBizType.INSPECTION), anyLong()))
                .thenReturn(ApprovalRecords.builder().approvalRecordId(100L).build());
        List<ProjectStage> childStages = stageService.getProjectStagesByProjectId(a1);
        tx.execute(status -> { stageService.startStages(new HashSet<>(childStages.stream().map(ProjectStage::getProjectStageId).toList())); return null; });
        for (ProjectStage stage : childStages) {
            Long id = tx.execute(status -> inspections.applyInspection(new ApplyInspectionDto(
                    a1, stage.getProjectStageId(), "Completed A1", "[1]", 8L, new BigDecimal("100"), null), 7L));
            when(flow.approve(any(), eq(8L))).thenReturn(new ApprovalFlowService.ApprovalResult(
                    100L, ApprovalBizType.INSPECTION, id, false, true, null));
            tx.execute(status -> { inspections.approvalInspection(new ApprovalInspectionDto(id, 1, "Approved", null, true), 8L); return null; });
        }
        assertEquals(4, mapper.selectById(a1).getProjectStatus());
        assertEquals(0, mapper.selectById(parent).getProjectStatus());
        assertEquals(0, mapper.selectById(a2).getProjectStatus());
        assertTrue(stageService.getProjectStagesByProjectId(parent).stream().allMatch(stage -> stage.getStageStatus() == 0));
        assertTrue(stageService.getProjectStagesByProjectId(a2).stream().allMatch(stage -> stage.getStageStatus() == 0));
        assertThrows(BusinessException.class, () -> tx.execute(status -> inspections.applyInspection(new ApplyInspectionDto(
                a2, childStages.get(0).getProjectStageId(), "wrong project", "[1]", 8L, BigDecimal.ONE, null), 7L)));
    }

    @Test void archivesOnlyAfterChildrenAndDoesNotCascadeIntoTheirRecords() {
        Long parent = root("A", "1000000");
        Long child = child(parent, "A1", "120000", 7L);
        jdbc.update("UPDATE project_stage SET stage_status=6");
        var blocked = assertThrows(BusinessException.class,
                () -> tx.execute(status -> { projects.archiveProject(parent, 7L, null); return null; }));
        assertTrue(blocked.getMessage().contains("子项目"));
        tx.execute(status -> { projects.archiveProject(child, 7L, "child"); return null; });
        assertEquals(0, mapper.selectById(parent).getArchiveStatus());
        tx.execute(status -> { projects.archiveProject(parent, 7L, "parent"); return null; });
        assertEquals(1, mapper.selectById(parent).getArchiveStatus());
        assertEquals("child", mapper.selectById(child).getArchiveRemark());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM contract", Integer.class));
        tx.execute(status -> { projects.unarchiveProject(parent); projects.unarchiveProject(child); return null; });
        assertEquals(0, mapper.selectById(child).getArchiveStatus());
    }

    @Test void parentMembershipDoesNotAuthorizeChildStageOrInspectionManagement() {
        Long parent = root("A", "1000000");
        Long child = child(parent, "A1", "120000", 8L);
        assertThrows(BusinessException.class, () -> projects.ensureProjectManager(child, 7L, false));
        assertDoesNotThrow(() -> projects.ensureProjectManager(child, 8L, false));
        assertDoesNotThrow(() -> projects.ensureProjectManager(child, 99L, true));
        var service = new InspectionFormServiceImpl();
        field(service, "projectService", projects);
        assertThrows(BusinessException.class, () -> service.applyInspection(new ApplyInspectionDto(
                child, stageService.getProjectStagesByProjectId(child).get(0).getProjectStageId(),
                "not allowed", "[1]", 9L, BigDecimal.ONE, null), 7L));
    }

    @Test void filtersBeforePaginationAndCountsEachOwnContractOnlyOnce() {
        Long parent = root("A", "1000000");
        child(parent, "A1", "120000", 7L);
        child(parent, "A2", "120000", 7L);
        Long archived = child(parent, "A3", "120000", 7L);
        jdbc.update("UPDATE project SET archive_status=1, project_status=4 WHERE project_id=?", archived);
        var dto = new GetAllProjectListDto(); dto.setProjectLevel(1); dto.setArchiveStatus(0);
        dto.setPageSize(1); dto.setCurrent(2);
        var page = projects.getAllProjectPage(dto);
        assertEquals(2L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertNotEquals(archived, page.getRecords().get(0).getProjectId());
        var mine = new GetMyProjectListDto(); mine.setProjectLevel(1); mine.setArchiveStatus(1);
        assertEquals(archived, projects.getMyProjectPage(7L, mine).getRecords().get(0).getProjectId());
        assertEquals(new BigDecimal("360000.00"), mapper.selectSettlementSummary(parent, 7L, true).getContractAmount());
        // The summary is not stored as a second amount on the parent.
        assertEquals(1240000d, mapper.selectProjectStatistics().getTotalContractAmount());
    }

    Long root(String name, String amount) { return tx.execute(status -> projects.createProject(dto(name, amount, 7L), 7L)); }
    Long child(Long parent, String name, String amount, Long manager) {
        return tx.execute(status -> settlements.create(parent, dto(name, amount, manager), manager, true));
    }
    CreateProjectDto dto(String name, String amount, Long manager) {
        var dto = new CreateProjectDto(); dto.setProjectName(name); dto.setProjectCode(name); dto.setProjectType(1L);
        dto.setContractType(1); dto.setContractAmount(new BigDecimal(amount)); dto.setBaseAmount(new BigDecimal(amount));
        dto.setBenefitAmount(new BigDecimal("20000")); dto.setContractFile(1L); dto.setProjectCharges(List.of(manager));
        return dto;
    }
    List<ProjectMember> memberList(Long projectId, Long userId) {
        return jdbc.query("SELECT project_id,user_id,project_role FROM project_member WHERE project_id=? AND is_delete=0",
                (row, index) -> ProjectMember.builder().projectId(row.getLong(1)).userId(row.getLong(2)).projectRole(row.getLong(3)).build(), projectId)
                .stream().filter(member -> userId == null || userId.equals(member.getUserId())).toList();
    }
    void field(Object target, String name, Object value) { ReflectionTestUtils.setField(target, name, value); }
}
