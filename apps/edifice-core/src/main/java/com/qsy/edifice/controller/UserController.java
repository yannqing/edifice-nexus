package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetUserListDto;
import com.qsy.edifice.domain.vo.SysUserListVo;
import com.qsy.edifice.service.SysUserService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {


    @Resource
    private SysUserService sysUserService;


    @GetMapping("/all")
    public BaseResponse<Page<SysUserListVo>> getAllUsers(@RequestBody GetUserListDto getUserListDto) {
        Page<SysUserListVo> userListVoList = sysUserService.getAllUsers(getUserListDto);


        return ResultUtils.success(Code.SUCCESS, userListVoList);
    }
}
