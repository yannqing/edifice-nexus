package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetUserListDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.SysUserListVo;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.SysUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    public Page<SysUserListVo> getAllUsers(GetUserListDto getUserListDto) {

        //1. 获取全部参数
        String username = getUserListDto.getUsername();
        String realName = getUserListDto.getRealName();
        String email = getUserListDto.getEmail();
        String phone = getUserListDto.getPhone();
        Integer current = getUserListDto.getCurrent();
        Integer pageSize = getUserListDto.getPageSize();

        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(StringUtils.isNotEmpty(username), "username", username);
        queryWrapper.like(StringUtils.isNotEmpty(realName), "realName", realName);
        queryWrapper.like(StringUtils.isNotEmpty(email), "email", email);
        queryWrapper.like(StringUtils.isNotEmpty(phone), "phone", phone);

//        List<SysUser> sysUsers = sysUserMapper.selectList(queryWrapper);

        Page<SysUser> sysUserPage = sysUserMapper.selectPage(new Page<>(current, pageSize), queryWrapper);

        List<SysUserListVo> sysUserListVos = sysUserPage.getRecords().stream().map(SysUserListVo::objToVo).toList();

        return new Page<SysUserListVo>(current, pageSize, sysUserPage.getTotal()).setRecords(sysUserListVos);
    }
}
