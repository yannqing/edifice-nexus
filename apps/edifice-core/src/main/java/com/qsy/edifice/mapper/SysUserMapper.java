package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统用户 Mapper 接口
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户信息
     */
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 根据手机号查询用户
     * @param phone 手机号
     * @return 用户信息
     */
    SysUser selectByPhone(@Param("phone") String phone);

    /**
     * 根据邮箱查询用户
     * @param email 邮箱
     * @return 用户信息
     */
    SysUser selectByEmail(@Param("email") String email);

    /**
     * 根据用户ID查询用户角色列表
     * @param userId 用户ID
     * @return 角色列表
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询用户权限列表
     * @param userId 用户ID
     * @return 权限编码列表
     */
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

}
