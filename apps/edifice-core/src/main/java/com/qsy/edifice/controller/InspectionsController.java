package com.qsy.edifice.controller;


import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.entity.InspectionForm;
import com.qsy.edifice.domain.vo.InspectionFormListVo;
import com.qsy.edifice.service.InspectionFormService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inspections")
public class InspectionsController {

    @Resource
    private InspectionFormService inspectionFormService;

    @GetMapping("/my-list")
    public BaseResponse<List<InspectionFormListVo>> getMyInspections(@RequestBody GetInspectionFormListDto getInspectionFormListDto) {
        List<InspectionFormListVo> inspectionFormListVoList = inspectionFormService.getMyInspections(getInspectionFormListDto);


        return ResultUtils.success(Code.SUCCESS, inspectionFormListVoList);
    };


}
