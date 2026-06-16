package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetTodoCenterListDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.TodoCenterDetailVo;
import com.qsy.edifice.domain.vo.TodoCenterItemVo;
import com.qsy.edifice.domain.vo.TodoCenterStatsVo;
import com.qsy.edifice.service.TodoCenterService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "统一待办中心")
@RestController
@RequestMapping("/todo-center")
@PreAuthorize("hasAuthority('menu:todo-center') or hasRole('SUPER_ADMIN')")
public class TodoCenterController {

    @Resource
    private TodoCenterService todoCenterService;

    @Resource
    private JwtUtils jwtUtils;

    @GetMapping("/pending")
    @Operation(summary = "待我处理")
    public BaseResponse<Page<TodoCenterItemVo>> pending(GetTodoCenterListDto dto, HttpServletRequest request)
            throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, todoCenterService.pending(userId(request), dto));
    }

    @GetMapping("/initiated")
    @Operation(summary = "我发起的")
    public BaseResponse<Page<TodoCenterItemVo>> initiated(GetTodoCenterListDto dto, HttpServletRequest request)
            throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, todoCenterService.initiated(userId(request), dto));
    }

    @GetMapping("/processed")
    @Operation(summary = "我已处理")
    public BaseResponse<Page<TodoCenterItemVo>> processed(GetTodoCenterListDto dto, HttpServletRequest request)
            throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, todoCenterService.processed(userId(request), dto));
    }

    @GetMapping("/{recordId}")
    @Operation(summary = "待办详情")
    public BaseResponse<TodoCenterDetailVo> detail(@PathVariable Long recordId, HttpServletRequest request)
            throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, todoCenterService.detail(userId(request), recordId));
    }

    @GetMapping("/statistics")
    @Operation(summary = "待办统计")
    public BaseResponse<TodoCenterStatsVo> statistics(HttpServletRequest request) throws JsonProcessingException {
        return ResultUtils.success(Code.SUCCESS, todoCenterService.statistics(userId(request)));
    }

    private Long userId(HttpServletRequest request) throws JsonProcessingException {
        SysUser user = jwtUtils.getUserFromToken(request.getHeader("token"));
        return user.getUserId();
    }
}
