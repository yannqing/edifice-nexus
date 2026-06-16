package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.entity.SysPosition;
import com.qsy.edifice.domain.entity.SysRole;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ConfigOptionBundleVo;
import com.qsy.edifice.domain.vo.ConfigOptionVo;
import com.qsy.edifice.mapper.SysPositionMapper;
import com.qsy.edifice.mapper.SysRoleMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.ConfigOptionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConfigOptionServiceImpl implements ConfigOptionService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysPositionMapper sysPositionMapper;

    @Override
    public ConfigOptionBundleVo getOptions() {
        return ConfigOptionBundleVo.builder()
                .users(sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getStatus, 1)
                                .eq(SysUser::getEmploymentStatus, 1)
                                .orderByAsc(SysUser::getRealName)
                                .orderByAsc(SysUser::getUsername))
                        .stream()
                        .map(user -> ConfigOptionVo.builder()
                                .value(user.getUserId())
                                .label(displayUser(user))
                                .type("user")
                                .description(user.getUsername())
                                .build())
                        .toList())
                .roles(sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                                .eq(SysRole::getStatus, 1)
                                .orderByAsc(SysRole::getRoleName))
                        .stream()
                        .map(role -> ConfigOptionVo.builder()
                                .value(role.getRoleId())
                                .label(role.getRoleName())
                                .type("role")
                                .description(role.getRoleCode())
                                .build())
                        .toList())
                .positions(sysPositionMapper.selectList(new LambdaQueryWrapper<SysPosition>()
                                .eq(SysPosition::getStatus, 1)
                                .orderByAsc(SysPosition::getName))
                        .stream()
                        .map(position -> ConfigOptionVo.builder()
                                .value(position.getPositionId())
                                .label(position.getName())
                                .type("position")
                                .description(position.getRemark())
                                .build())
                        .toList())
                .build();
    }

    private String displayUser(SysUser user) {
        if (StringUtils.hasText(user.getRealName())) return user.getRealName();
        if (StringUtils.hasText(user.getUsername())) return user.getUsername();
        return String.valueOf(user.getUserId());
    }
}
