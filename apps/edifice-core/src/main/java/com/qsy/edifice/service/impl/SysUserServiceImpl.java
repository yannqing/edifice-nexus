package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.GetUserListDto;
import com.qsy.edifice.domain.vo.SysUserListVo;
import com.qsy.edifice.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    public List<SysUserListVo> getAllUsers(GetUserListDto getUserListDto) {



        return List.of();
    }
}
