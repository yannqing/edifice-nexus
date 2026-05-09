package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.CreateOutputValueDto;
import com.qsy.edifice.domain.vo.OutputValueVo;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface OutputValueService {

    /**
     * 查询产值分配单列表
     * @param status 状态过滤（null=全部）
     * @return 列表
     */
    List<OutputValueVo> getOutputValueList(Integer status);

    /**
     * 创建产值分配单
     * @param dto 创建参数
     * @param userId 当前用户id
     * @return 分配单id
     */
    Long createOutputValue(CreateOutputValueDto dto, Long userId);

    /**
     * 确认分配（待确认→待审核）
     * @param outputValueId 分配单id
     */
    void confirmOutputValue(Long outputValueId);

    /**
     * 审批通过（待审核→已审批）
     * @param outputValueId 分配单id
     * @param userId 审批人id
     */
    void approveOutputValue(Long outputValueId, Long userId);

    /**
     * 发放产值（已审批→已发放）
     * @param outputValueId 分配单id
     */
    void payOutputValue(Long outputValueId);

    /**
     * 统计数据
     * @return 各状态数量 + 已发放总额
     */
    Map<String, Object> getStatistics();

    /**
     * 导出产值分配单
     * @param status 状态过滤（null=全部）
     * @param keyword 项目名称/编码关键字
     * @param response HTTP 响应
     */
    void exportOutputValues(Integer status, String keyword, HttpServletResponse response) throws IOException;
}
