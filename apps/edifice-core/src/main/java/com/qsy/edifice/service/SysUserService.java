package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetUserListDto;
import com.qsy.edifice.domain.vo.SysUserListVo;

import java.util.List;

public interface SysUserService {

    /**
     * 查询所有用户
     *
     * @param getUserListDto 查询条件
     * @return 封装 vo 返回
     */
    Page<SysUserListVo> getAllUsers(GetUserListDto getUserListDto);
}
