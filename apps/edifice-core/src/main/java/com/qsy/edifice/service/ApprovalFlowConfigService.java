package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetApprovalFlowConfigListDto;
import com.qsy.edifice.domain.dto.SaveApprovalFlowConfigDto;
import com.qsy.edifice.domain.vo.ApprovalFlowConfigVo;

public interface ApprovalFlowConfigService {
    Page<ApprovalFlowConfigVo> list(GetApprovalFlowConfigListDto dto);
    ApprovalFlowConfigVo detail(Long id);
    Long save(SaveApprovalFlowConfigDto dto, Long userId);
    void toggle(Long id, Integer enabled, Long userId);
    void delete(Long id);
    ApprovalFlowConfigVo getEnabledByBizType(String bizType);
}
