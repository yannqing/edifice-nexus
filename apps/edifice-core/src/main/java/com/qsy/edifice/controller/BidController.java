package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateBidDto;
import com.qsy.edifice.domain.dto.UpdateBidDto;
import com.qsy.edifice.domain.dto.UpdateBidStatusDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.BidVo;
import com.qsy.edifice.service.BidService;
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

/**
 * 投标管理（Phase 3 #5）
 *
 * 生命周期：
 * - 创建（草稿, 筹备） → 编辑基础信息 / 上传附件 → 提交审批
 * - 审批通过 → 负责人更新业务状态：已投递 → 中标 / 未中标 / 终止
 */
@Tag(name = "投标管理")
@RestController
@RequestMapping("/bids")
@PreAuthorize("hasAuthority('menu:bids') or hasRole('SUPER_ADMIN')")
public class BidController {

    @Resource
    private BidService bidService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/create")
    @Operation(summary = "创建投标", description = "初始状态：业务 筹备 + 审批 草稿")
    public BaseResponse<Long> create(@RequestBody CreateBidDto dto,
                                     HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        return ResultUtils.success(Code.SUCCESS,
                bidService.create(dto, loginUser.getUserId()), "创建成功");
    }

    @PutMapping("/update")
    @Operation(summary = "更新投标基础信息 / 附件",
            description = "审批中 / 已通过 禁止修改；传 files 视为全量替换")
    public BaseResponse<Boolean> update(@RequestBody UpdateBidDto dto,
                                        HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        bidService.update(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "更新成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除投标")
    public BaseResponse<Boolean> delete(@PathVariable("id") Long id) {
        bidService.delete(id);
        return ResultUtils.success(Code.SUCCESS, true, "删除成功");
    }

    @PutMapping("/status")
    @Operation(summary = "更新业务状态",
            description = "筹备 → 已投递 → 中标 / 未中标；任意非终态可直接 终止")
    public BaseResponse<Boolean> updateStatus(@RequestBody UpdateBidStatusDto dto) {
        bidService.updateBidStatus(dto);
        return ResultUtils.success(Code.SUCCESS, true, "状态已更新");
    }

    @PostMapping("/submit-approval")
    @Operation(summary = "提交审批（投标文件内部走审批链）")
    public BaseResponse<Boolean> submitApproval(
            @RequestParam("bidId") Long bidId,
            @RequestParam("firstApproverId") Long firstApproverId,
            HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        bidService.submitApproval(bidId, firstApproverId, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "已提交审批");
    }

    @PostMapping("/approve")
    @Operation(summary = "审批（通过 / 驳回）")
    public BaseResponse<Boolean> approve(@RequestBody ApproveDto dto,
                                         HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        bidService.approve(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "审批完成");
    }

    @GetMapping("/list")
    @Operation(summary = "投标列表",
            description = "支持 bidStatus / approvalStatus / keyword 过滤；前端可用同一接口绘制看板")
    public BaseResponse<List<BidVo>> list(
            @RequestParam(value = "bidStatus", required = false) Integer bidStatus,
            @RequestParam(value = "approvalStatus", required = false) Integer approvalStatus,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResultUtils.success(Code.SUCCESS,
                bidService.list(bidStatus, approvalStatus, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "投标详情（含附件 + 审批链）")
    public BaseResponse<BidVo> detail(@PathVariable("id") Long id) {
        return ResultUtils.success(Code.SUCCESS, bidService.getDetail(id));
    }

    @GetMapping("/my-pending")
    @Operation(summary = "我的待审投标")
    public BaseResponse<List<BidVo>> myPending(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        return ResultUtils.success(Code.SUCCESS,
                bidService.listMyPending(loginUser.getUserId()));
    }
}
