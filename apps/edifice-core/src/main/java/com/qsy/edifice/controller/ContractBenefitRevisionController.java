package com.qsy.edifice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.ReviseBenefitDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ContractBenefitRevisionVo;
import com.qsy.edifice.service.ContractBenefitRevisionService;
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
 * 合同效益修正（v0.4）
 */
@Tag(name = "合同效益修正")
@RestController
@RequestMapping("/contract")
public class ContractBenefitRevisionController {

    @Resource
    private ContractBenefitRevisionService revisionService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/{id}/benefit-revision")
    @Operation(summary = "提交一次效益修正",
            description = "更新合同 benefit_amount 并写入历史；isFinal=true 后合同结算锁定")
    @PreAuthorize("hasAuthority('menu:contract-management') or hasAuthority('menu:all-projects') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Long> revise(@PathVariable("id") Long contractId,
                                     @RequestBody ReviseBenefitDto dto,
                                     HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long revisionId = revisionService.revise(contractId, dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, revisionId, "已修正");
    }

    @GetMapping("/{id}/benefit-history")
    @Operation(summary = "查询合同效益修正历史", description = "按时间倒序")
    @PreAuthorize("hasAuthority('menu:contract-management') or hasAuthority('menu:all-projects') or hasRole('SUPER_ADMIN')")
    public BaseResponse<List<ContractBenefitRevisionVo>> history(@PathVariable("id") Long contractId) {
        return ResultUtils.success(Code.SUCCESS, revisionService.listByContract(contractId));
    }
}
