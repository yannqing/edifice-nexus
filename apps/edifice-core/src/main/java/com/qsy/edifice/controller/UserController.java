package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.GetUserListDto;
import com.qsy.edifice.domain.dto.SysUserCreateDto;
import com.qsy.edifice.domain.dto.SysUserUpdateDto;
import com.qsy.edifice.domain.dto.UpdateProfileDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.SysUserDetailVo;
import com.qsy.edifice.domain.vo.SysUserListVo;
import com.qsy.edifice.service.SysUserService;
import com.qsy.edifice.service.UserExcelService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/users")
public class UserController {


    @Resource
    private SysUserService sysUserService;

    @Resource
    private UserExcelService userExcelService;

    @Autowired
    private JwtUtils jwtUtils;


    @GetMapping("/profile")
    @Operation(summary = "获取当前登录用户的个人资料")
    public BaseResponse<SysUserDetailVo> getProfile(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        SysUserDetailVo vo = sysUserService.getProfile(loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, vo);
    }

    @PutMapping("/profile")
    @Operation(summary = "更新当前登录用户的个人资料", description = "员工主数据以 OA 为准，当前接口会拒绝修改")
    public BaseResponse<SysUserDetailVo> updateProfile(@RequestBody UpdateProfileDto dto,
                                                       HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        SysUserDetailVo vo = sysUserService.updateProfile(loginUser.getUserId(), dto);
        return ResultUtils.success(Code.SUCCESS, vo, "更新成功");
    }


    @GetMapping("/all")
    @Operation(summary = "查询所有用户", description = "条件 + 分页查询所有用户")
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Page<SysUserListVo>> getAllUsers( GetUserListDto getUserListDto) {
        Page<SysUserListVo> userListVoList = sysUserService.getAllUsers(getUserListDto);

        return ResultUtils.success(Code.SUCCESS, userListVoList);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据id查询单个用户")
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
    public BaseResponse<SysUserDetailVo> getUserById(@PathVariable Integer id) {
        SysUserDetailVo userDetailVo = sysUserService.getUserById(id);

        return ResultUtils.success(Code.SUCCESS, userDetailVo);
    }

    @PostMapping("/create")
    @Operation(summary = "新增用户")
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
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
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
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
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
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
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Boolean> deleteUsers(@RequestParam("ids") List<Long> ids) {
        boolean result = sysUserService.deleteUserBath(ids);

        if (result) {
            return ResultUtils.success(Code.SUCCESS, true);
        } else {
            return ResultUtils.failure(Code.FAILURE, false);
        }
    }

    // ==================== Excel 导入 / 模板 ====================

    @GetMapping("/export/template")
    @Operation(summary = "下载用户导入模板")
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        userExcelService.downloadTemplate(response);
    }

    @PostMapping("/import")
    @Operation(summary = "批量导入用户", description = "按花名册 Excel 批量导入；用户名自动生成（手机号/邮箱本地部分/员工编号），初始密码 12345678")
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
    public BaseResponse<String> importUsers(@RequestParam("file") MultipartFile file) throws IOException {
        String result = userExcelService.importUsers(file);
        return ResultUtils.success(Code.SUCCESS, result);
    }

}
