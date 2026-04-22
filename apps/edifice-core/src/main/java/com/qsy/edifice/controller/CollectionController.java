package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.CreateCollectionRecordDto;
import com.qsy.edifice.domain.dto.GetCollectionListDto;
import com.qsy.edifice.domain.dto.UpdateCollectionRecordDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.CollectionDetailVo;
import com.qsy.edifice.domain.vo.CollectionStatisticsVo;
import com.qsy.edifice.domain.vo.CollectionSummaryVo;
import com.qsy.edifice.service.CollectionService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "回款管理")
@RestController
@RequestMapping("/collection")
public class CollectionController {

    @Resource
    private CollectionService collectionService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/list")
    @Operation(summary = "回款汇总列表", description = "按项目聚合，支持关键字/状态筛选 + 分页")
    public BaseResponse<Page<CollectionSummaryVo>> getList(GetCollectionListDto dto) {
        Page<CollectionSummaryVo> result = collectionService.getCollectionList(dto);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/statistics")
    @Operation(summary = "回款统计总览", description = "应收 / 已收 / 回款率 / 待回款")
    public BaseResponse<CollectionStatisticsVo> getStatistics() {
        CollectionStatisticsVo result = collectionService.getStatistics();
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/detail/{projectId}")
    @Operation(summary = "项目回款详情", description = "包含阶段聚合与原始记录，用于详情弹窗")
    public BaseResponse<CollectionDetailVo> getDetail(@PathVariable("projectId") Long projectId) {
        CollectionDetailVo result = collectionService.getCollectionDetail(projectId);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @PostMapping("/record")
    @Operation(summary = "新建回款记录")
    public BaseResponse<Long> createRecord(@RequestBody CreateCollectionRecordDto dto,
                                           HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long id = collectionService.createCollectionRecord(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, id, "录入成功");
    }

    @PutMapping("/record")
    @Operation(summary = "更新回款记录")
    public BaseResponse<Boolean> updateRecord(@RequestBody UpdateCollectionRecordDto dto) {
        collectionService.updateCollectionRecord(dto);
        return ResultUtils.success(Code.SUCCESS, true, "更新成功");
    }

    @DeleteMapping("/record/{id}")
    @Operation(summary = "删除回款记录")
    public BaseResponse<Boolean> deleteRecord(@PathVariable("id") Long id) {
        collectionService.deleteCollectionRecord(id);
        return ResultUtils.success(Code.SUCCESS, true, "删除成功");
    }
}
