package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.PerformanceRestoreVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.*;
import com.qsy.edifice.service.PerformanceRestoreService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 绩效还原服务实现（v0.2 最小可用）
 */
@Slf4j
@Service
public class PerformanceRestoreServiceImpl implements PerformanceRestoreService {

    /** 0-待还原 */
    private static final int STATUS_PENDING = 0;
    /** 1-已还原 */
    private static final int STATUS_RESTORED = 1;

    @Resource
    private PerformanceRestoreMapper performanceRestoreMapper;

    @Resource
    private OutputValueMapper outputValueMapper;

    @Resource
    private OutputValueDistributionMapper distributionMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ProjectMapper projectMapper;

    // ==================== 查询 ====================

    @Override
    public List<PerformanceRestoreVo> list(String quarter, Integer status, Long userId) {
        LambdaQueryWrapper<PerformanceRestore> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(quarter)) {
            w.eq(PerformanceRestore::getQuarter, quarter.trim());
        }
        if (status != null) {
            w.eq(PerformanceRestore::getStatus, status);
        }
        if (userId != null) {
            w.eq(PerformanceRestore::getUserId, userId);
        }
        w.orderByAsc(PerformanceRestore::getStatus)
                .orderByDesc(PerformanceRestore::getCreatedTime);

        List<PerformanceRestore> list = performanceRestoreMapper.selectList(w);
        if (list.isEmpty()) return Collections.emptyList();

        // 批量查姓名 / 项目名 / 操作人
        Set<Long> userIds = list.stream().map(PerformanceRestore::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> projectIds = list.stream().map(PerformanceRestore::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> opIds = list.stream().map(PerformanceRestore::getOperatorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Set<Long> allUserIds = new HashSet<>();
        allUserIds.addAll(userIds);
        allUserIds.addAll(opIds);

        Map<Long, SysUser> userMap = allUserIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(allUserIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));
        Map<Long, Project> projectMap = projectIds.isEmpty() ? Collections.emptyMap()
                : projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getProjectId, p -> p, (a, b) -> a));

        return list.stream().map(e -> {
            SysUser u = e.getUserId() == null ? null : userMap.get(e.getUserId());
            SysUser op = e.getOperatorId() == null ? null : userMap.get(e.getOperatorId());
            Project p = e.getProjectId() == null ? null : projectMap.get(e.getProjectId());
            return PerformanceRestoreVo.builder()
                    .restoreId(e.getRestoreId())
                    .quarter(e.getQuarter())
                    .userId(e.getUserId())
                    .realName(u == null ? "-" : (u.getRealName() != null ? u.getRealName() : u.getUsername()))
                    .projectId(e.getProjectId())
                    .projectName(p == null ? null : p.getProjectName())
                    .restoreAmount(e.getRestoreAmount())
                    .status(e.getStatus())
                    .restoredTime(e.getRestoredTime())
                    .operatorId(e.getOperatorId())
                    .operatorName(op == null ? null : (op.getRealName() != null ? op.getRealName() : op.getUsername()))
                    .remark(e.getRemark())
                    .createdTime(e.getCreatedTime())
                    .build();
        }).collect(Collectors.toList());
    }

    // ==================== 生成（幂等） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateFromQuarter(String quarter) {
        if (!StringUtils.hasText(quarter)) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择季度");
        }
        String q = quarter.trim();

        // 1. 该季度全部已确认（status>=2）产值分配单
        LambdaQueryWrapper<OutputValue> ovW = new LambdaQueryWrapper<>();
        ovW.ge(OutputValue::getStatus, 2).eq(OutputValue::getQuarter, q);
        List<OutputValue> ovs = outputValueMapper.selectList(ovW);
        if (ovs.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("generated", 0);
            empty.put("skipped", 0);
            empty.put("totalAmount", BigDecimal.ZERO);
            return empty;
        }

        Set<Long> ovIds = ovs.stream().map(OutputValue::getOutputValueId).collect(Collectors.toSet());
        Map<Long, Long> ovToProject = ovs.stream()
                .collect(Collectors.toMap(OutputValue::getOutputValueId, OutputValue::getProjectId, (a, b) -> a));

        // 2. 分配明细：只算实得为正的员工（dist_type 0/1：正常/降档；离职=其他金额，不还原给个人）
        LambdaQueryWrapper<OutputValueDistribution> dW = new LambdaQueryWrapper<>();
        dW.in(OutputValueDistribution::getOutputValueId, ovIds);
        List<OutputValueDistribution> dists = distributionMapper.selectList(dW);

        // 3. 已存在（userId+projectId+quarter）的还原记录不重复生成
        LambdaQueryWrapper<PerformanceRestore> exW = new LambdaQueryWrapper<>();
        exW.eq(PerformanceRestore::getQuarter, q);
        List<PerformanceRestore> existed = performanceRestoreMapper.selectList(exW);
        Set<String> existedKeys = existed.stream()
                .map(r -> keyOf(r.getUserId(), r.getProjectId()))
                .collect(Collectors.toSet());

        int generated = 0;
        int skipped = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 聚合：按 (userId, projectId) 求和实得
        Map<String, BigDecimal> amountByKey = new LinkedHashMap<>();
        Map<String, Long[]> metaByKey = new LinkedHashMap<>();

        for (OutputValueDistribution d : dists) {
            BigDecimal amt = d.getAmount();
            if (amt == null || amt.signum() == 0) continue;
            Long uid = d.getUserId();
            if (uid == null) continue;
            Long pid = ovToProject.get(d.getOutputValueId());
            String k = keyOf(uid, pid);
            amountByKey.merge(k, amt, BigDecimal::add);
            metaByKey.putIfAbsent(k, new Long[]{uid, pid});
        }

        for (Map.Entry<String, BigDecimal> e : amountByKey.entrySet()) {
            // Signed benefit deductions must reduce the person's quarter total. A non-positive
            // final total has no payable performance restore record.
            if (e.getValue().signum() <= 0) {
                skipped++;
                continue;
            }
            if (existedKeys.contains(e.getKey())) {
                skipped++;
                continue;
            }
            Long[] meta = metaByKey.get(e.getKey());
            PerformanceRestore pr = PerformanceRestore.builder()
                    .quarter(q)
                    .userId(meta[0])
                    .projectId(meta[1])
                    .restoreAmount(e.getValue())
                    .status(STATUS_PENDING)
                    .build();
            performanceRestoreMapper.insert(pr);
            generated++;
            totalAmount = totalAmount.add(e.getValue());
        }

        log.info("按季度生成绩效还原 quarter={} generated={} skipped={} totalAmount={}",
                q, generated, skipped, totalAmount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generated", generated);
        result.put("skipped", skipped);
        result.put("totalAmount", totalAmount);
        return result;
    }

    private String keyOf(Long userId, Long projectId) {
        return (userId == null ? "-" : userId) + "#" + (projectId == null ? "-" : projectId);
    }

    // ==================== 标记 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRestored(Long restoreId, Long operatorId) {
        PerformanceRestore pr = performanceRestoreMapper.selectById(restoreId);
        if (pr == null) throw new BusinessException(ErrorType.PERFORMANCE_RESTORE_NOT_FOUND);
        if (pr.getStatus() != null && pr.getStatus() == STATUS_RESTORED) {
            throw new BusinessException(ErrorType.PERFORMANCE_RESTORE_STATUS_INVALID, "该记录已还原");
        }
        pr.setStatus(STATUS_RESTORED);
        pr.setRestoredTime(LocalDateTime.now());
        pr.setOperatorId(operatorId);
        performanceRestoreMapper.updateById(pr);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> markQuarterRestored(String quarter, Long operatorId) {
        if (!StringUtils.hasText(quarter)) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择季度");
        }
        LambdaQueryWrapper<PerformanceRestore> w = new LambdaQueryWrapper<>();
        w.eq(PerformanceRestore::getQuarter, quarter.trim())
                .eq(PerformanceRestore::getStatus, STATUS_PENDING);
        List<PerformanceRestore> pending = performanceRestoreMapper.selectList(w);

        LocalDateTime now = LocalDateTime.now();
        for (PerformanceRestore pr : pending) {
            pr.setStatus(STATUS_RESTORED);
            pr.setRestoredTime(now);
            pr.setOperatorId(operatorId);
            performanceRestoreMapper.updateById(pr);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restored", pending.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRestore(Long restoreId) {
        PerformanceRestore pr = performanceRestoreMapper.selectById(restoreId);
        if (pr == null) throw new BusinessException(ErrorType.PERFORMANCE_RESTORE_NOT_FOUND);
        if (pr.getStatus() != null && pr.getStatus() == STATUS_RESTORED) {
            throw new BusinessException(ErrorType.PERFORMANCE_RESTORE_STATUS_INVALID, "已还原记录不可删除");
        }
        performanceRestoreMapper.deleteById(restoreId);
    }
}
