package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetUserListDto;
import com.qsy.edifice.domain.dto.SysUserCreateDto;
import com.qsy.edifice.domain.dto.SysUserUpdateDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.SysUserDetailVo;
import com.qsy.edifice.domain.vo.SysUserListVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.SysUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PasswordEncoder bCryptPasswordEncoder;

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

    @Override
    public SysUserDetailVo getUserById(Integer id) {

        // 1. 参数校验
        if (id == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        //2. 查询数据库，获取用户原始数据
        SysUser sysUser = sysUserMapper.selectById(id);

        if (sysUser == null) {
            throw new BusinessException(ErrorType.USER_CANNOT_NULL);
        }

        // 3. 封装为 vo
        SysUserDetailVo userDetailVo = SysUserDetailVo.objToVo(sysUser);

        //4. 给用户角色赋值

        //5. 打印日志
        log.info("查询用户(id: {})", id);

        //6. 返回
        return userDetailVo;
    }

    @Override
    public boolean createUser(SysUserCreateDto sysUserCreateDto) {

        //1. 校验参数
        if (sysUserCreateDto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        //2. 获取全部参数并校验
        String username = sysUserCreateDto.getUsername();

        if (StringUtils.isEmpty(username)) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(StringUtils.isNotEmpty(username), "username", username);
        boolean exists = sysUserMapper.exists(queryWrapper);
        if (exists) {
            throw new BusinessException(ErrorType.USER_ALREADY_EXISTS);
        }

        //3. 转换为实体类
        SysUser sysUser = SysUserCreateDto.dtoToObj(sysUserCreateDto);

        //4. 给一些数据赋值（初始化）
        sysUser.setPassword(bCryptPasswordEncoder.encode("12345678"));

        //5. 插入数据
        int count = sysUserMapper.insert(sysUser);

        //6. 返回结果
        return count > 0;
    }

    @Override
    public boolean updateUser(SysUserUpdateDto sysUserUpdateDto) {

        //1. 参数校验
        if (sysUserUpdateDto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        //2. 获取参数并校验
        Long userId = sysUserUpdateDto.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        //3. 有效性校验
        if (sysUserMapper.selectById(userId) == null) {
            throw new BusinessException(ErrorType.USER_CANNOT_NULL);
        }

        //4. 数据转换
        SysUser sysUser = SysUserUpdateDto.dtoToObj(sysUserUpdateDto);

        //5. 更新操作
        int count = sysUserMapper.updateById(sysUser);

        return count > 0;
    }

    @Override
    public boolean deleteUser(Integer id) {
        //1. 空值校验
        if (id == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

        //2. 有效性校验
        if (sysUserMapper.selectById(id) == null) {
            throw new BusinessException(ErrorType.USER_CANNOT_NULL);
        }

        //3. 执行删除操作
        int count = sysUserMapper.deleteById(id);

        return count > 0;
    }

    @Override
    public boolean deleteUserBath(List<Long> ids) {
        //1. 空值校验
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }

//        //2. 格式转换
//        List<Integer> userIds = Arrays.stream(ids).toList();

        //3. 执行删除操作
        int count = sysUserMapper.deleteByIds(ids);

        //4. 返回删除结果
        return count > 0;
    }
}
