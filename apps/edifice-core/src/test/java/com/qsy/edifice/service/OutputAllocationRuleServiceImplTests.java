package com.qsy.edifice.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qsy.edifice.domain.dto.SaveOutputAllocationRuleDto;
import com.qsy.edifice.domain.entity.OutputAllocationRuleItem;
import com.qsy.edifice.domain.entity.OutputAllocationRulePoolRate;
import com.qsy.edifice.domain.entity.OutputAllocationRuleVersion;
import com.qsy.edifice.domain.entity.ProjectStageTemplate;
import com.qsy.edifice.domain.entity.ProjectType;
import com.qsy.edifice.domain.vo.OutputAllocationRuleVo;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.OutputAllocationRuleItemMapper;
import com.qsy.edifice.mapper.OutputAllocationRulePoolRateMapper;
import com.qsy.edifice.mapper.OutputAllocationRuleVersionMapper;
import com.qsy.edifice.mapper.ProjectStageMapper;
import com.qsy.edifice.mapper.ProjectStageTemplateMapper;
import com.qsy.edifice.service.impl.OutputAllocationRuleServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutputAllocationRuleServiceImplTests {

    private static final Long PROJECT_TYPE_ID = 7L;

    @InjectMocks
    private OutputAllocationRuleServiceImpl service;

    @Mock
    private ProjectTypeService projectTypeService;

    @Mock
    private ProjectStageTemplateService stageTemplateService;

    @Mock
    private ProjectStageTemplateMapper stageTemplateMapper;

    @Mock
    private ProjectStageMapper projectStageMapper;

    @Mock
    private OutputAllocationRuleVersionMapper ruleVersionMapper;

    @Mock
    private OutputAllocationRuleItemMapper ruleItemMapper;

    @Mock
    private OutputAllocationRulePoolRateMapper poolRateMapper;

    private ProjectStageTemplate template;

    @BeforeAll
    static void initializeMybatisMetadata() {
        initializeTableInfo(OutputAllocationRuleVersion.class);
        initializeTableInfo(OutputAllocationRuleItem.class);
        initializeTableInfo(OutputAllocationRulePoolRate.class);
    }

    private static void initializeTableInfo(Class<?> entityType) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), entityType.getName()),
                entityType);
    }

    @BeforeEach
    void setUp() {
        ProjectType projectType = ProjectType.builder()
                .projectTypeId(PROJECT_TYPE_ID)
                .projectTypeCode("A")
                .projectTypeName("全程结算")
                .build();
        template = ProjectStageTemplate.builder()
                .stageId(11L)
                .projectTypeId(PROJECT_TYPE_ID)
                .stageName("项目策划")
                .stageOutput(new BigDecimal("4"))
                .stageStatus(1)
                .build();
        when(projectTypeService.getProjectTypeById(PROJECT_TYPE_ID)).thenReturn(projectType);
        when(stageTemplateService.getEnabledByProjectTypeId(PROJECT_TYPE_ID)).thenReturn(List.of(template));
    }

    @Test
    void savesRuleAndSynchronizesStageOutputWithoutOverwritingHistoricalStages() {
        doAnswer(invocation -> {
            OutputAllocationRuleVersion version = invocation.getArgument(0);
            version.setRuleVersionId(101L);
            return 1;
        }).when(ruleVersionMapper).insert(any(OutputAllocationRuleVersion.class));

        OutputAllocationRuleVo result = service.saveRule(PROJECT_TYPE_ID, validRule("100"), 99L);

        assertEquals(1, result.getVersionNo());
        verify(stageTemplateMapper).updateById(any(ProjectStageTemplate.class));
        verify(projectStageMapper).updateUnallocatedStageOutput(
                PROJECT_TYPE_ID, "项目策划", new BigDecimal("100"));
    }

    @Test
    void rejectsStageOutputsThatDoNotTotalOneHundred() {
        assertThrows(BusinessException.class,
                () -> service.saveRule(PROJECT_TYPE_ID, validRule("90"), 99L));

        verify(stageTemplateMapper, never()).updateById(any(ProjectStageTemplate.class));
        verify(projectStageMapper, never()).updateUnallocatedStageOutput(any(), any(), any());
        verify(ruleVersionMapper, never()).insert(any(OutputAllocationRuleVersion.class));
    }

    private SaveOutputAllocationRuleDto validRule(String stageOutput) {
        List<SaveOutputAllocationRuleDto.WorkRate> workRates = List.of(
                new SaveOutputAllocationRuleDto.WorkRate(
                        0, new BigDecimal("8.06"), new BigDecimal("4"), new BigDecimal("4.06")),
                new SaveOutputAllocationRuleDto.WorkRate(
                        1, new BigDecimal("20.28"), new BigDecimal("20.28"), BigDecimal.ZERO),
                new SaveOutputAllocationRuleDto.WorkRate(
                        2, new BigDecimal("11.66"), new BigDecimal("4"), new BigDecimal("7.66"))
        );
        List<SaveOutputAllocationRuleDto.WorkRule> stageWorkRules = List.of(
                new SaveOutputAllocationRuleDto.WorkRule(0, new BigDecimal("70"), new BigDecimal("4")),
                new SaveOutputAllocationRuleDto.WorkRule(1, new BigDecimal("10"), null),
                new SaveOutputAllocationRuleDto.WorkRule(2, new BigDecimal("20"), new BigDecimal("4"))
        );
        SaveOutputAllocationRuleDto.StageRule stageRule = new SaveOutputAllocationRuleDto.StageRule(
                "项目策划", 1, new BigDecimal(stageOutput), stageWorkRules);
        return new SaveOutputAllocationRuleDto(
                new BigDecimal("40"), new BigDecimal("60"), workRates, List.of(stageRule));
    }
}
