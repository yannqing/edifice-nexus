package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetInspectionFormListDto;
import com.qsy.edifice.domain.vo.InspectionFormDetailVo;
import com.qsy.edifice.domain.vo.InspectionFormListVo;

/**
 * 验工单服务接口
 */
public interface InspectionFormService {

    /**
     * 查询我的验工单列表
     *
     * @param getInspectionFormListDto 查询条件
     * @return 封装 vo 返回
     */
    Page<InspectionFormListVo> getMyInspections(GetInspectionFormListDto getInspectionFormListDto);

    /**
     * 根据id查询验工单详情
     *
     * @param id 用户 id
     * @return 返回验工单详情 vo
     */
    InspectionFormDetailVo getInspectionById(Long id);
}







