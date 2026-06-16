package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetMessageCenterListDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.MessageCenterItemVo;
import com.qsy.edifice.service.MessageCenterService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "消息中心")
@RestController
@RequestMapping("/message-center")
@PreAuthorize("hasAuthority('menu:message-center') or hasRole('SUPER_ADMIN')")
public class MessageCenterController {

    @Resource
    private MessageCenterService messageCenterService;

    @Resource
    private JwtUtils jwtUtils;

    @GetMapping("/list")
    @Operation(summary = "统一消息列表")
    public BaseResponse<Page<MessageCenterItemVo>> list(GetMessageCenterListDto dto, HttpServletRequest request)
            throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, messageCenterService.list(userId(request), dto));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读消息数量")
    public BaseResponse<Long> unreadCount(HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, messageCenterService.unreadCount(userId(request)));
    }

    @PutMapping("/read/{sourceType}/{sourceId}")
    @Operation(summary = "标记消息已读")
    public BaseResponse<Boolean> markRead(@PathVariable String sourceType, @PathVariable Long sourceId,
                                          HttpServletRequest request) throws JsonProcessingException {
        messageCenterService.markRead(userId(request), sourceType, sourceId);
        return ResultUtils.success(Code.SUCCESS, true, "已标记为已读");
    }

    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public BaseResponse<Boolean> markAllRead(HttpServletRequest request) throws JsonProcessingException {
        messageCenterService.markAllRead(userId(request));
        return ResultUtils.success(Code.SUCCESS, true, "已全部标记为已读");
    }

    private Long userId(HttpServletRequest request) throws JsonProcessingException {
        SysUser user = jwtUtils.getUserFromToken(request.getHeader("token"));
        return user.getUserId();
    }
}
