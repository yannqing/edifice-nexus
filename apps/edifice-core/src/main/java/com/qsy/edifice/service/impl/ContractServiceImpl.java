package com.qsy.edifice.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetContractListDto;
import com.qsy.edifice.domain.dto.UpdateContractDto;
import com.qsy.edifice.domain.entity.Contract;
import com.qsy.edifice.domain.entity.ContractChangeLog;
import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.excel.ContractExcelData;
import com.qsy.edifice.domain.vo.ContractChangeLogVo;
import com.qsy.edifice.domain.vo.ContractListVo;
import com.qsy.edifice.domain.vo.FilesVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ContractChangeLogMapper;
import com.qsy.edifice.mapper.ContractMapper;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.ContractService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 合同服务实现类
 */
@Slf4j
@Service
public class ContractServiceImpl implements ContractService {

    @Resource
    private ContractMapper contractMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private FilesMapper filesMapper;

    @Resource
    private ContractChangeLogMapper contractChangeLogMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    private static final DateTimeFormatter EXPORT_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<Integer, String> CONTRACT_TYPE_LABELS = Map.of(
            0, "基本收费", 1, "基本+效益"
    );
    private static final Map<Integer, String> BENEFIT_STATUS_LABELS = Map.of(
            0, "预计中", 1, "已最终确认"
    );
    private static final Map<Integer, String> PROJECT_STATUS_LABELS = Map.of(
            0, "未开始", 1, "进行中", 2, "待验收", 3, "验收中", 4, "已结束"
    );

    @Override
    public Contract getContractById(Long contractId) {
        return contractMapper.selectById(contractId);
    }

    @Override
    public Contract getContractByProjectId(Long projectId) {
        LambdaQueryWrapper<Contract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Contract::getProjectId, projectId)
               .orderByDesc(Contract::getCreatedTime)
               .last("LIMIT 1");
        return contractMapper.selectOne(wrapper);
    }

    @Override
    public Contract getContractByCode(String contractCode) {
        return contractMapper.selectByContractCode(contractCode);
    }

    @Override
    public Page<Contract> getContractPage(Integer current, Integer pageSize) {
        return contractMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public Page<ContractListVo> getContractList(GetContractListDto dto) {
        if (dto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        Integer current = dto.getCurrent() == null || dto.getCurrent() < 1 ? 1 : dto.getCurrent();
        Integer pageSize = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10 : dto.getPageSize();

        LambdaQueryWrapper<Contract> wrapper = buildContractQuery(dto);
        Page<Contract> page = contractMapper.selectPage(new Page<>(current, pageSize), wrapper);
        Page<ContractListVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::convertToListVo).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public ContractListVo getContractDetail(Long contractId) {
        if (contractId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "合同ID不能为空");
        }
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException(ErrorType.CONTRACT_NOT_FOUND);
        }
        return convertToListVo(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContractInfo(UpdateContractDto dto, Long operatorId) {
        if (dto == null || dto.getContractId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "合同ID不能为空");
        }
        Contract contract = contractMapper.selectById(dto.getContractId());
        if (contract == null) {
            throw new BusinessException(ErrorType.CONTRACT_NOT_FOUND);
        }
        validateMoney(dto.getContractAmount(), "合同金额", true);
        validateMoney(dto.getBaseAmount(), "基本收费金额", false);
        if (dto.getContractType() != null && dto.getContractType() != 0 && dto.getContractType() != 1) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "合同类型不正确");
        }
        if (dto.getBenefitAmount() != null
                && (contract.getBenefitAmount() == null
                || dto.getBenefitAmount().compareTo(contract.getBenefitAmount()) != 0)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "预计效益金额请通过效益修正流程调整");
        }

        List<ContractChangeLog> logs = new ArrayList<>();
        recordChange(logs, contract, "contractName", "合同名称", contract.getContractName(), dto.getContractName(),
                value -> contract.setContractName(value.trim()), operatorId);
        recordChange(logs, contract, "contractCode", "合同编号", contract.getContractCode(), dto.getContractCode(),
                value -> contract.setContractCode(value.trim()), operatorId);
        recordChange(logs, contract, "contractType", "合同类型", contract.getContractType(), dto.getContractType(),
                contract::setContractType, operatorId);
        recordChange(logs, contract, "contractAmount", "合同金额", contract.getContractAmount(), dto.getContractAmount(),
                contract::setContractAmount, operatorId);
        recordChange(logs, contract, "baseAmount", "基本收费金额", contract.getBaseAmount(), dto.getBaseAmount(),
                contract::setBaseAmount, operatorId);
        recordChange(logs, contract, "benefitRules", "效益规则", contract.getBenefitRules(), dto.getBenefitRules(),
                contract::setBenefitRules, operatorId);
        recordChange(logs, contract, "signingDate", "签订日期", contract.getSigningDate(), dto.getSigningDate(),
                contract::setSigningDate, operatorId);
        recordChange(logs, contract, "preStartDate", "预计开始日期", contract.getPreStartDate(), dto.getPreStartDate(),
                contract::setPreStartDate, operatorId);
        recordChange(logs, contract, "preEndDate", "预计结束日期", contract.getPreEndDate(), dto.getPreEndDate(),
                contract::setPreEndDate, operatorId);

        contractMapper.updateById(contract);
        logs.forEach(contractChangeLogMapper::insert);
    }

    @Override
    public List<ContractChangeLogVo> getContractChangeLogs(Long contractId) {
        if (contractId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "合同ID不能为空");
        }
        List<ContractChangeLog> logs = contractChangeLogMapper.selectList(new LambdaQueryWrapper<ContractChangeLog>()
                .eq(ContractChangeLog::getContractId, contractId)
                .orderByDesc(ContractChangeLog::getCreatedTime));
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }
        return logs.stream().map(log -> {
            ContractChangeLogVo vo = new ContractChangeLogVo();
            BeanUtils.copyProperties(log, vo);
            if (log.getOperatorId() != null) {
                SysUser user = sysUserMapper.selectById(log.getOperatorId());
                if (user != null) {
                    vo.setOperatorName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void exportContracts(GetContractListDto dto, HttpServletResponse response) throws IOException {
        GetContractListDto query = dto == null ? new GetContractListDto() : dto;
        List<ContractListVo> contracts = contractMapper.selectList(buildContractQuery(query)).stream()
                .map(this::convertToListVo)
                .collect(Collectors.toList());
        List<ContractExcelData> rows = contracts.stream()
                .map(this::toExcelData)
                .collect(Collectors.toList());
        setExcelResponseHeader(response, "合同数据");
        EasyExcel.write(response.getOutputStream(), ContractExcelData.class)
                .sheet("合同管理")
                .doWrite(rows);
    }

    @Override
    public boolean saveContract(Contract contract) {
        return contractMapper.insert(contract) > 0;
    }

    @Override
    public boolean updateContract(Contract contract) {
        return contractMapper.updateById(contract) > 0;
    }

    @Override
    public boolean deleteContract(Long contractId) {
        return contractMapper.deleteById(contractId) > 0;
    }

    private void validateMoney(BigDecimal amount, String fieldName, boolean mustPositive) {
        if (amount == null) {
            return;
        }
        int signum = amount.signum();
        if ((mustPositive && signum <= 0) || (!mustPositive && signum < 0)) {
            throw new BusinessException(ErrorType.ARGS_INVALID,
                    fieldName + (mustPositive ? "必须大于0" : "不能小于0"));
        }
    }

    private ContractListVo convertToListVo(Contract contract) {
        ContractListVo vo = new ContractListVo();
        BeanUtils.copyProperties(contract, vo);
        if (contract.getProjectId() != null) {
            Project project = projectMapper.selectById(contract.getProjectId());
            if (project != null) {
                vo.setProjectName(project.getProjectName());
                vo.setProjectCode(project.getProjectCode());
                vo.setProjectStatus(project.getProjectStatus());
            }
        }
        if (Objects.equals(contract.getContractType(), 0) && vo.getBenefitAmount() == null) {
            vo.setBenefitAmount(BigDecimal.ZERO);
        }
        vo.setContractFileDetail(toFileVo(contract.getContractFile()));
        vo.setContractAttachmentFiles(parseFileIds(contract.getContractOtherFiles()).stream()
                .map(this::toFileVo)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        return vo;
    }

    private LambdaQueryWrapper<Contract> buildContractQuery(GetContractListDto dto) {
        LambdaQueryWrapper<Contract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getProjectId() != null, Contract::getProjectId, dto.getProjectId());
        wrapper.eq(dto.getContractType() != null, Contract::getContractType, dto.getContractType());

        if (StringUtils.hasText(dto.getKeywords())) {
            String keyword = dto.getKeywords().trim();
            List<Long> projectIds = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                            .and(w -> w.like(Project::getProjectName, keyword)
                                    .or()
                                    .like(Project::getProjectCode, keyword)))
                    .stream()
                    .map(Project::getProjectId)
                    .collect(Collectors.toList());
            wrapper.and(w -> {
                w.like(Contract::getContractName, keyword)
                        .or()
                        .like(Contract::getContractCode, keyword);
                if (!projectIds.isEmpty()) {
                    w.or().in(Contract::getProjectId, projectIds);
                }
            });
        }
        wrapper.orderByDesc(Contract::getUpdatedTime).orderByDesc(Contract::getCreatedTime);
        return wrapper;
    }

    private ContractExcelData toExcelData(ContractListVo item) {
        return ContractExcelData.builder()
                .contractName(item.getContractName())
                .contractCode(item.getContractCode())
                .contractType(CONTRACT_TYPE_LABELS.getOrDefault(item.getContractType(), "未知"))
                .contractAmount(item.getContractAmount())
                .baseAmount(item.getBaseAmount())
                .benefitAmount(item.getBenefitAmount())
                .benefitStatus(BENEFIT_STATUS_LABELS.getOrDefault(item.getBenefitStatus(), "-"))
                .projectName(item.getProjectName())
                .projectCode(item.getProjectCode())
                .projectStatus(PROJECT_STATUS_LABELS.getOrDefault(item.getProjectStatus(), "-"))
                .signingDate(formatTime(item.getSigningDate()))
                .preStartDate(formatTime(item.getPreStartDate()))
                .preEndDate(formatTime(item.getPreEndDate()))
                .benefitRules(item.getBenefitRules())
                .build();
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : EXPORT_TIME_FMT.format(value);
    }

    private void setExcelResponseHeader(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private <T> void recordChange(List<ContractChangeLog> logs,
                                  Contract contract,
                                  String fieldName,
                                  String fieldLabel,
                                  T oldValue,
                                  T newValue,
                                  Consumer<T> setter,
                                  Long operatorId) {
        if (newValue == null) {
            return;
        }
        if (newValue instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        if (Objects.equals(normalizeValue(oldValue), normalizeValue(newValue))) {
            return;
        }
        setter.accept(newValue);
        logs.add(ContractChangeLog.builder()
                .contractId(contract.getContractId())
                .projectId(contract.getProjectId())
                .fieldName(fieldName)
                .fieldLabel(fieldLabel)
                .oldValue(normalizeValue(oldValue))
                .newValue(normalizeValue(newValue))
                .operatorId(operatorId)
                .createdTime(LocalDateTime.now())
                .build());
    }

    private String normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    private FilesVo toFileVo(Long fileId) {
        if (fileId == null) {
            return null;
        }
        Files file = filesMapper.selectById(fileId);
        return file == null ? null : FilesVo.objToVo(file);
    }

    private List<Long> parseFileIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(raw);
        while (matcher.find()) {
            try {
                ids.add(Long.parseLong(matcher.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }
}
