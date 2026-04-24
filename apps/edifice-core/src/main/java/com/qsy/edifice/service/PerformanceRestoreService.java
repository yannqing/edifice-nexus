package com.qsy.edifice.service;

import com.qsy.edifice.domain.vo.PerformanceRestoreVo;

import java.util.List;
import java.util.Map;

/**
 * 绩效还原服务（v0.2 最小可用版）
 *
 * 业务口径：
 * - 按季度扫描 status >= 2（已确认）的产值分配单，为每名员工的实得金额生成一条"待还原"记录；
 * - 财务确认已还原（线下打款等）后，逐条或按季度批量标记为"已还原"；
 * - 本期暂不生成工资明细，工资管理待后续迭代。
 */
public interface PerformanceRestoreService {

    /**
     * 查询绩效还原列表（支持按季度 / 状态 / 用户过滤）。
     */
    List<PerformanceRestoreVo> list(String quarter, Integer status, Long userId);

    /**
     * 按季度生成还原记录（幂等）：扫描该季度所有已确认产值分配单，
     * 为每人实得金额生成或复用一条待还原记录。
     *
     * @return { generated: 新增条数, skipped: 已存在条数, totalAmount: 本次纳入总额 }
     */
    Map<String, Object> generateFromQuarter(String quarter);

    /**
     * 标记单条还原为已还原。
     */
    void markRestored(Long restoreId, Long operatorId);

    /**
     * 按季度批量标记已还原（一键"当季还完"）。
     *
     * @return { restored: 本次标记条数 }
     */
    Map<String, Object> markQuarterRestored(String quarter, Long operatorId);

    /**
     * 删除一条还原记录（仅待还原可删）。
     */
    void deleteRestore(Long restoreId);
}
