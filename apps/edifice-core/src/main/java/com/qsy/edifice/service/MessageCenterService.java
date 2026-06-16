package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetMessageCenterListDto;
import com.qsy.edifice.domain.vo.MessageCenterItemVo;

public interface MessageCenterService {
    Page<MessageCenterItemVo> list(Long userId, GetMessageCenterListDto dto);
    long unreadCount(Long userId);
    void markRead(Long userId, String sourceType, Long sourceId);
    void markAllRead(Long userId);
}
