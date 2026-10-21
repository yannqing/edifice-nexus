package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.CreateProjectDto;
import com.qsy.edifice.domain.dto.GetAllProjectListDto;
import com.qsy.edifice.domain.vo.SettlementProjectPageVo;

public interface SettlementProjectService {
    Long create(Long parentId, CreateProjectDto dto, Long userId, boolean canManageAll);

    SettlementProjectPageVo list(Long parentId, GetAllProjectListDto dto, Long userId,
                                 boolean canViewAll, boolean canManageAll);
}
