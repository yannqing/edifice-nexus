package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qsy.edifice.domain.entity.SysUserRole;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.mapper.SysUserRoleMapper;
import com.qsy.edifice.service.SysUserRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {
}
