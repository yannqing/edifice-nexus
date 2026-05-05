package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ApprovalRecordVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.service.ApprovalFlowService;
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

/**
 * 通用审批流（Phase 3 #1）
 *
 * 所有需要多级审批的业务（项目文件 / 验工单 / 投标 / 验收 / 产值 / 工时）
 * 都可以通过这组接口完成链式审批。业务侧只需要在终审 / 驳回时监听
 * {@link ApprovalFlowService.ApprovalResult} 更新自己的主表状态。
 */
@Tag(name = "通用审批流")
@RestController
@RequestMapping("/approval-flow")
public class ApprovalFlowController {

    @Resource
    private ApprovalFlowService approvalFlowService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/submit")
    @Operation(summary = "提交审批",
            description = "创建第一条待审核节点；同一业务存在未结束审批时拒绝重复提交")
    public BaseResponse<Long> submit(@RequestBody SubmitApprovalDto dto,
                                     HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        ApprovalRecords record = approvalFlowService.submit(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, record.getApprovalRecordId(), "提交成功");
    }

    @PostMapping("/approve")
    @Operation(summary = "审批（通过 / 驳回）",
            description = "通过且 nextApproverId 非空时级联创建下一级；不传 nextApproverId 视为终审")
    public BaseResponse<ApprovalFlowService.ApprovalResult> approve(
            @RequestBody ApproveDto dto,
            HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        ApprovalFlowService.ApprovalResult result = approvalFlowService.approve(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, result, "审批完成");
    }

    @GetMapping("/chain")
    @Operation(summary = "查询审批链", description = "按时间升序返回该业务的全部审批记录")
    public BaseResponse<List<ApprovalRecordVo>> chain(
            @RequestParam("bizType") String bizType,
            @RequestParam("bizId") Long bizId) {
        ApprovalBizType bt = ApprovalBizType.fromExt(bizType);
        if (bt == null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "未知业务类型：" + bizType);
        }
        return ResultUtils.success(Code.SUCCESS, approvalFlowService.queryChain(bt, bizId));
    }

    @GetMapping("/current-pending")
    @Operation(summary = "查询当前待审核节点",
            description = "业务侧调用此接口判断是否已提交、当前审批人是谁")
    public BaseResponse<ApprovalRecordVo> currentPending(
            @RequestParam("bizType") String bizType,
            @RequestParam("bizId") Long bizId) {
        ApprovalBizType bt = ApprovalBizType.fromExt(bizType);
        if (bt == null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "未知业务类型：" + bizType);
        }
        ApprovalRecords r = approvalFlowService.getCurrentPending(bt, bizId);
        if (r == null) return ResultUtils.success(Code.SUCCESS, null);
        // 复用 queryChain 的转换逻辑，简单做法：返回该单条即可
        List<ApprovalRecordVo> chain = approvalFlowService.queryChain(bt, bizId);
        ApprovalRecordVo vo = chain.stream()
                .filter(v -> r.getApprovalRecordId().equals(v.getApprovalRecordId()))
                .findFirst().orElse(null);
        return ResultUtils.success(Code.SUCCESS, vo);
    }

    @GetMapping("/my-pending")
    @Operation(summary = "我的待审批",
            description = "当前登录用户的待审批节点列表，可按 bizType 过滤")
    public BaseResponse<List<ApprovalRecordVo>> myPending(
            @RequestParam(value = "bizType", required = false) String bizType,
            HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        ApprovalBizType bt = null;
        if (bizType != null && !bizType.isBlank()) {
            bt = ApprovalBizType.fromExt(bizType);
            if (bt == null) throw new BusinessException(ErrorType.ARGS_INVALID, "未知业务类型：" + bizType);
        }
        return ResultUtils.success(Code.SUCCESS,
                approvalFlowService.listPendingByApprover(loginUser.getUserId(), bt));
    }

    @GetMapping("/my-pending-counts")
    @Operation(summary = "我的待审批计数（按业务类型分桶）",
            description = "返回以 ext 为 key（file/inspection/bid/acceptance/output/timesheet）的待办数；用于侧边栏 badge")
    public BaseResponse<Map<String, Long>> myPendingCounts(HttpServletRequest request)
            throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        return ResultUtils.success(Code.SUCCESS,
                approvalFlowService.countPendingByApprover(loginUser.getUserId()));
    }
}
