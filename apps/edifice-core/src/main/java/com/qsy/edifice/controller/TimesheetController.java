package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetTimesheetListDto;
import com.qsy.edifice.domain.dto.TimesheetDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.TimesheetVo;
import com.qsy.edifice.service.TimesheetService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "工时管理")
@RestController
@RequestMapping("/timesheet")
public class TimesheetController {

    @Resource
    private TimesheetService timesheetService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/my-list")
    @Operation(summary = "查询我的工时记录", description = "按日期范围查询当前用户的工时记录")
    public BaseResponse<List<TimesheetVo>> getMyTimesheets(GetTimesheetListDto dto, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        List<TimesheetVo> result = timesheetService.getMyTimesheets(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/weekly-stats")
    @Operation(summary = "周工时统计", description = "按工作类型汇总本周工时")
    public BaseResponse<Map<String, Object>> getWeeklyStats(
            @RequestParam String startDate,
            @RequestParam String endDate,
            HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Map<String, Object> result = timesheetService.getWeeklyStats(startDate, endDate, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @PostMapping("/save")
    @Operation(summary = "新增/更新工时", description = "新增或更新工时记录")
    public BaseResponse<Long> saveTimesheet(@RequestBody TimesheetDto dto, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long id = timesheetService.saveOrUpdateTimesheet(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, id, "保存成功");
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除工时记录")
    public BaseResponse<Boolean> deleteTimesheet(@PathVariable("id") Long timesheetId, HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        timesheetService.deleteTimesheet(timesheetId, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "删除成功");
    }
}
