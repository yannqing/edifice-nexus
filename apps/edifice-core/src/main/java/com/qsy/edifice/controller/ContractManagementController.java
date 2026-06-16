package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetContractListDto;
import com.qsy.edifice.domain.dto.UpdateContractDto;
import com.qsy.edifice.domain.vo.ContractListVo;
import com.qsy.edifice.service.ContractService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "合同管理")
@RestController
@RequestMapping("/contracts")
@PreAuthorize("hasAuthority('menu:contract-management') or hasRole('SUPER_ADMIN')")
public class ContractManagementController {

    @Resource
    private ContractService contractService;

    @GetMapping("/list")
    @Operation(summary = "合同管理列表", description = "分页查询合同，并返回关联项目基础信息")
    public BaseResponse<Page<ContractListVo>> list(GetContractListDto dto) {
        return ResultUtils.success(Code.SUCCESS, contractService.getContractList(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "合同详情", description = "根据合同 ID 查询合同详情")
    public BaseResponse<ContractListVo> detail(@PathVariable("id") Long contractId) {
        return ResultUtils.success(Code.SUCCESS, contractService.getContractDetail(contractId));
    }

    @PutMapping("/update")
    @Operation(summary = "更新合同", description = "维护合同基础信息，不处理合同文件替换")
    public BaseResponse<Boolean> update(@RequestBody UpdateContractDto dto) {
        contractService.updateContractInfo(dto);
        return ResultUtils.success(Code.SUCCESS, true, "更新成功");
    }
}
