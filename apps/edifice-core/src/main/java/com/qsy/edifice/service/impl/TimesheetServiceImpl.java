package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.GetTimesheetListDto;
import com.qsy.edifice.domain.dto.TimesheetDto;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.entity.ProjectStage;
import com.qsy.edifice.domain.entity.Timesheet;
import com.qsy.edifice.domain.vo.TimesheetVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.TimesheetMapper;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.service.ProjectStageService;
import com.qsy.edifice.service.TimesheetService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TimesheetServiceImpl implements TimesheetService {

    @Resource
    private TimesheetMapper timesheetMapper;

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectStageService projectStageService;

    @Override
    public List<TimesheetVo> getMyTimesheets(GetTimesheetListDto dto, Long userId) {
        LambdaQueryWrapper<Timesheet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Timesheet::getUserId, userId);

        if (StringUtils.hasText(dto.getStartDate())) {
            wrapper.ge(Timesheet::getWorkDate, LocalDate.parse(dto.getStartDate()));
        }
        if (StringUtils.hasText(dto.getEndDate())) {
            wrapper.le(Timesheet::getWorkDate, LocalDate.parse(dto.getEndDate()));
        }
        if (dto.getProjectId() != null) {
            wrapper.eq(Timesheet::getProjectId, dto.getProjectId());
        }

        wrapper.orderByDesc(Timesheet::getWorkDate).orderByDesc(Timesheet::getCreatedTime);

        List<Timesheet> records = timesheetMapper.selectList(wrapper);

        return records.stream().map(this::convertToVo).collect(Collectors.toList());
    }

    @Override
    public Long saveOrUpdateTimesheet(TimesheetDto dto, Long userId) {
        if (dto.getProjectId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择项目");
        }
        if (dto.getWorkType() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择工作类型");
        }
        if (dto.getWorkDate() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择工作日期");
        }
        if (dto.getHours() == null || dto.getHours().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "工作时长必须大于0");
        }
        if (dto.getHours().compareTo(new BigDecimal("24")) > 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "单日工时不能超过24小时");
        }

        if (dto.getTimesheetId() != null) {
            // 更新
            Timesheet existing = timesheetMapper.selectById(dto.getTimesheetId());
            if (existing == null || !existing.getUserId().equals(userId)) {
                throw new BusinessException(ErrorType.NO_AUTH_ERROR);
            }

            existing.setProjectId(dto.getProjectId());
            existing.setProjectStageId(dto.getProjectStageId());
            existing.setWorkType(dto.getWorkType());
            existing.setWorkDate(dto.getWorkDate());
            existing.setHours(dto.getHours());
            existing.setDescription(dto.getDescription());
            existing.setStatus(dto.getStatus() != null ? dto.getStatus() : existing.getStatus());
            timesheetMapper.updateById(existing);
            return existing.getTimesheetId();
        } else {
            // 新增
            Timesheet record = Timesheet.builder()
                    .userId(userId)
                    .projectId(dto.getProjectId())
                    .projectStageId(dto.getProjectStageId())
                    .workType(dto.getWorkType())
                    .workDate(dto.getWorkDate())
                    .hours(dto.getHours())
                    .description(dto.getDescription())
                    .status(dto.getStatus() != null ? dto.getStatus() : 1)
                    .build();
            timesheetMapper.insert(record);
            return record.getTimesheetId();
        }
    }

    @Override
    public void deleteTimesheet(Long timesheetId, Long userId) {
        if (timesheetId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "工时记录ID不能为空");
        }
        Timesheet record = timesheetMapper.selectById(timesheetId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR);
        }
        timesheetMapper.deleteById(timesheetId);
    }

    @Override
    public Map<String, Object> getWeeklyStats(String startDate, String endDate, Long userId) {
        LambdaQueryWrapper<Timesheet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Timesheet::getUserId, userId);
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(Timesheet::getWorkDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(Timesheet::getWorkDate, LocalDate.parse(endDate));
        }

        List<Timesheet> records = timesheetMapper.selectList(wrapper);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal management = BigDecimal.ZERO;
        BigDecimal basic = BigDecimal.ZERO;
        BigDecimal intellectual = BigDecimal.ZERO;

        for (Timesheet r : records) {
            total = total.add(r.getHours());
            switch (r.getWorkType()) {
                case 0 -> management = management.add(r.getHours());
                case 1 -> basic = basic.add(r.getHours());
                case 2 -> intellectual = intellectual.add(r.getHours());
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalHours", total);
        stats.put("managementHours", management);
        stats.put("basicHours", basic);
        stats.put("intellectualHours", intellectual);
        return stats;
    }

    private TimesheetVo convertToVo(Timesheet record) {
        TimesheetVo vo = new TimesheetVo();
        BeanUtils.copyProperties(record, vo);

        // 项目信息
        Project project = projectService.getProjectById(record.getProjectId());
        if (project != null) {
            vo.setProjectName(project.getProjectName());
            vo.setProjectCode(project.getProjectCode());
        }

        // 阶段信息
        if (record.getProjectStageId() != null) {
            ProjectStage stage = projectStageService.getProjectStageById(record.getProjectStageId());
            if (stage != null) {
                vo.setStageName(stage.getStageName());
            }
        }

        return vo;
    }
}
