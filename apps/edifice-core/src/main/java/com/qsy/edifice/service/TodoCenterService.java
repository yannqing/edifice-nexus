package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetTodoCenterListDto;
import com.qsy.edifice.domain.vo.TodoCenterDetailVo;
import com.qsy.edifice.domain.vo.TodoCenterItemVo;
import com.qsy.edifice.domain.vo.TodoCenterStatsVo;

public interface TodoCenterService {
    Page<TodoCenterItemVo> pending(Long userId, GetTodoCenterListDto dto);
    Page<TodoCenterItemVo> initiated(Long userId, GetTodoCenterListDto dto);
    Page<TodoCenterItemVo> processed(Long userId, GetTodoCenterListDto dto);
    TodoCenterDetailVo detail(Long userId, Long recordId);
    TodoCenterStatsVo statistics(Long userId);
}
