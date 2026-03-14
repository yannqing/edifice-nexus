package com.qsy.edifice.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.entity.InspectionForm;
import com.qsy.edifice.domain.vo.InspectionFormDetailVo;
import com.qsy.edifice.domain.vo.InspectionFormListVo;
import com.qsy.edifice.service.InspectionFormService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Slf4j
@Tag(name = "验工单管理")
@RestController
@RequestMapping("/inspections")
public class InspectionsController {

    @Resource
    private InspectionFormService inspectionFormService;

    @GetMapping("/my-list")
    @Operation(summary = "我的验工单列表",description = "分页+条件查询")
    public BaseResponse<Page<InspectionFormListVo>> getMyInspections(@RequestBody GetInspectionFormListDto getInspectionFormListDto) {
        Page<InspectionFormListVo> inspectionFormListVoList = inspectionFormService.getMyInspections(getInspectionFormListDto);


        return ResultUtils.success(Code.SUCCESS, inspectionFormListVoList);
    };

    @GetMapping("/{id}")
    @Operation(description = "根据id查看验工单详情")
    public BaseResponse<InspectionFormDetailVo> getInspectionById(@PathVariable Long id) {
        InspectionFormDetailVo inspectionFormDetailVo = inspectionFormService.getInspectionById(id);
        return ResultUtils.success(Code.SUCCESS, inspectionFormDetailVo);
    }
}
    