package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.PerformanceRestoreVo;
import com.qsy.edifice.service.PerformanceRestoreService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 绩效还原（v0.2 最小可用）
 */
@Tag(name = "绩效还原")
@RestController
@RequestMapping("/performance/restore")
@PreAuthorize("hasAuthority('menu:all-projects') or hasRole('SUPER_ADMIN')")
public class PerformanceRestoreController {

    @Resource
    private PerformanceRestoreService performanceRestoreService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/list")
    @Operation(summary = "还原记录列表", description = "支持按 quarter / status / userId 过滤")
    public BaseResponse<List<PerformanceRestoreVo>> list(
            @RequestParam(value = "quarter", required = false) String quarter,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "userId", required = false) Long userId) {
        List<PerformanceRestoreVo> list = performanceRestoreService.list(quarter, status, userId);
        return ResultUtils.success(Code.SUCCESS, list);
    }

    @PostMapping("/generate")
    @Operation(summary = "按季度生成还原记录",
            description = "扫描该季度所有已确认产值分配单，为每人的实得金额生成待还原记录（幂等）")
    public BaseResponse<Map<String, Object>> generate(@RequestParam("quarter") String quarter) {
        return ResultUtils.success(Code.SUCCESS,
                performanceRestoreService.generateFromQuarter(quarter), "生成成功");
    }

    @PutMapping("/mark/{id}")
    @Operation(summary = "标记单条为已还原")
    public BaseResponse<Boolean> markOne(@PathVariable("id") Long id,
                                         HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        performanceRestoreService.markRestored(id, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "已标记");
    }

    @PutMapping("/mark-quarter")
    @Operation(summary = "按季度一键标记已还原", description = "把该季度所有待还原记录批量置为已还原")
    public BaseResponse<Map<String, Object>> markQuarter(
            @RequestParam("quarter") String quarter,
            HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        return ResultUtils.success(Code.SUCCESS,
                performanceRestoreService.markQuarterRestored(quarter, loginUser.getUserId()), "标记成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除待还原记录", description = "仅 status=0 可删")
    public BaseResponse<Boolean> delete(@PathVariable("id") Long id) {
        performanceRestoreService.deleteRestore(id);
        return ResultUtils.success(Code.SUCCESS, true, "删除成功");
    }
}
