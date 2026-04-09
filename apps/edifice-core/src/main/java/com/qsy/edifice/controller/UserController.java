package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetUserListDto;
import com.qsy.edifice.domain.dto.SysUserCreateDto;
import com.qsy.edifice.domain.dto.SysUserUpdateDto;
import com.qsy.edifice.domain.vo.SysUserDetailVo;
import com.qsy.edifice.domain.vo.SysUserListVo;
import com.qsy.edifice.service.SysUserService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/users")
public class UserController {


    @Resource
    private SysUserService sysUserService;


    @GetMapping("/all")
    @Operation(summary = "查询所有用户", description = "条件 + 分页查询所有用户")
    public BaseResponse<Page<SysUserListVo>> getAllUsers( GetUserListDto getUserListDto) {
        Page<SysUserListVo> userListVoList = sysUserService.getAllUsers(getUserListDto);

        return ResultUtils.success(Code.SUCCESS, userListVoList);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据id查询单个用户")
    public BaseResponse<SysUserDetailVo> getUserById(@PathVariable Integer id) {
        SysUserDetailVo userDetailVo = sysUserService.getUserById(id);

        return ResultUtils.success(Code.SUCCESS, userDetailVo);
    }

    @PostMapping("/create")
    @Operation(summary = "新增用户")
    public BaseResponse<Boolean> createUser(@RequestBody SysUserCreateDto sysUserCreateDto) {
        boolean result = sysUserService.createUser(sysUserCreateDto);

        if (result) {
            return ResultUtils.success(Code.SUCCESS, true);
        } else {
            return ResultUtils.failure(Code.FAILURE, false);
        }
    }

    @PutMapping("/update")
    @Operation(summary = "更新用户")
    public BaseResponse<Boolean> updateUser(@RequestBody SysUserUpdateDto sysUserUpdateDto) {
        boolean result = sysUserService.updateUser(sysUserUpdateDto);

        if (result) {
            return ResultUtils.success(Code.SUCCESS, true);
        } else {
            return ResultUtils.failure(Code.FAILURE, false);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "根据id删除用户")
    public BaseResponse<Boolean> deleteUser(@PathVariable Integer id) {
        boolean result = sysUserService.deleteUser(id);

        if (result) {
            return ResultUtils.success(Code.SUCCESS, true);
        } else {
            return ResultUtils.failure(Code.FAILURE, false);
        }
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除用户")
    public BaseResponse<Boolean> deleteUsers(@RequestParam("ids") List<Long> ids) {
        boolean result = sysUserService.deleteUserBath(ids);

        if (result) {
            return ResultUtils.success(Code.SUCCESS, true);
        } else {
            return ResultUtils.failure(Code.FAILURE, false);
        }
    }


}
