package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.GetTimesheetListDto;
import com.qsy.edifice.domain.dto.TimesheetDto;
import com.qsy.edifice.domain.vo.TimesheetVo;

import java.util.List;
import java.util.Map;

/**
 * 工时服务接口
 */
public interface TimesheetService {

    /**
     * 查询我的工时记录
     * @param dto 查询条件
     * @param userId 当前用户id
     * @return 工时记录列表
     */
    List<TimesheetVo> getMyTimesheets(GetTimesheetListDto dto, Long userId);

    /**
     * 新增或更新工时记录
     * @param dto 工时数据
     * @param userId 当前用户id
     * @return 工时记录id
     */
    Long saveOrUpdateTimesheet(TimesheetDto dto, Long userId);

    /**
     * 删除工时记录
     * @param timesheetId 工时记录id
     * @param userId 当前用户id
     */
    void deleteTimesheet(Long timesheetId, Long userId);

    /**
     * 周统计：按工作类型汇总
     * @param startDate 周开始日期
     * @param endDate 周结束日期
     * @param userId 用户id
     * @return 统计数据
     */
    Map<String, Object> getWeeklyStats(String startDate, String endDate, Long userId);
}
