package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetUserListDto;
import com.qsy.edifice.domain.dto.SysUserCreateDto;
import com.qsy.edifice.domain.dto.SysUserUpdateDto;
import com.qsy.edifice.domain.dto.UpdateProfileDto;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Integer current = getUserListDto.getCurrent() != null && getUserListDto.getCurrent() > 0 ? getUserListDto.getCurrent() : 1;
        Integer pageSize = getUserListDto.getPageSize() != null && getUserListDto.getPageSize() > 0 ? getUserListDto.getPageSize() : 10;

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();

        // 统一关键字：OR 匹配 username / realName / employeeNo / phone
        if (StringUtils.isNotEmpty(getUserListDto.getKeywords())) {
            String kw = getUserListDto.getKeywords().trim();
            wrapper.and(w -> w.like(SysUser::getUsername, kw)
                    .or().like(SysUser::getRealName, kw)
                    .or().like(SysUser::getEmployeeNo, kw)
                    .or().like(SysUser::getPhone, kw));
        }

        // 独立字段过滤（保留给未来精确筛选用）
        wrapper.like(StringUtils.isNotEmpty(getUserListDto.getUsername()), SysUser::getUsername, getUserListDto.getUsername());
        wrapper.like(StringUtils.isNotEmpty(getUserListDto.getRealName()), SysUser::getRealName, getUserListDto.getRealName());
        wrapper.like(StringUtils.isNotEmpty(getUserListDto.getEmployeeNo()), SysUser::getEmployeeNo, getUserListDto.getEmployeeNo());
        wrapper.like(StringUtils.isNotEmpty(getUserListDto.getEmail()), SysUser::getEmail, getUserListDto.getEmail());
        wrapper.like(StringUtils.isNotEmpty(getUserListDto.getPhone()), SysUser::getPhone, getUserListDto.getPhone());
        wrapper.like(StringUtils.isNotEmpty(getUserListDto.getPosition()), SysUser::getPosition, getUserListDto.getPosition());
        if (getUserListDto.getEmploymentStatus() != null) {
            wrapper.eq(SysUser::getEmploymentStatus, getUserListDto.getEmploymentStatus());
        }
        if (getUserListDto.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, getUserListDto.getStatus());
        }
        wrapper.orderByDesc(SysUser::getCreatedTime);

        Page<SysUser> sysUserPage = sysUserMapper.selectPage(new Page<>(current, pageSize), wrapper);

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
        if (sysUserCreateDto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        String username = sysUserCreateDto.getUsername();
        if (StringUtils.isEmpty(username)) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "用户名不能为空");
        }

        // 精确匹配用户名，防止重复
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        if (sysUserMapper.exists(wrapper)) {
            throw new BusinessException(ErrorType.USER_ALREADY_EXISTS);
        }

        SysUser sysUser = SysUserCreateDto.dtoToObj(sysUserCreateDto);

        // 初始密码，新增用户默认 12345678（后续走"首次登录强制改密"流程）
        sysUser.setPassword(bCryptPasswordEncoder.encode("12345678"));
        // 默认在职、账号启用
        if (sysUser.getEmploymentStatus() == null) sysUser.setEmploymentStatus(1);
        if (sysUser.getStatus() == null) sysUser.setStatus(1);

        int count = sysUserMapper.insert(sysUser);
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

        //2. 执行删除操作
        int count = sysUserMapper.deleteByIds(ids);

        //3. 返回删除结果
        return count > 0;
    }

    // ==================== 个人中心 ====================

    @Override
    public SysUserDetailVo getProfile(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorType.USER_CANNOT_NULL);
        }
        return SysUserDetailVo.objToVo(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserDetailVo updateProfile(Long userId, UpdateProfileDto dto) {
        if (userId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        if (dto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        SysUser existing = sysUserMapper.selectById(userId);
        if (existing == null) {
            throw new BusinessException(ErrorType.USER_CANNOT_NULL);
        }

        // 仅更新允许自助修改的字段，其它字段（员工编号/身份证/入职/离职/账号状态等）维持原值
        if (dto.getRealName() != null) existing.setRealName(dto.getRealName());
        if (dto.getGender() != null) existing.setGender(dto.getGender());
        if (dto.getEthnicity() != null) existing.setEthnicity(dto.getEthnicity());
        if (dto.getBirthDate() != null) existing.setBirthDate(dto.getBirthDate());
        if (dto.getEmail() != null) existing.setEmail(dto.getEmail());
        if (dto.getPhone() != null) existing.setPhone(dto.getPhone());
        if (dto.getAvatar() != null) existing.setAvatar(dto.getAvatar());
        if (dto.getEducation() != null) existing.setEducation(dto.getEducation());
        if (dto.getSchool() != null) existing.setSchool(dto.getSchool());
        if (dto.getMajor() != null) existing.setMajor(dto.getMajor());
        if (dto.getCertificates() != null) existing.setCertificates(dto.getCertificates());
        if (dto.getDomicile() != null) existing.setDomicile(dto.getDomicile());
        if (dto.getAddress() != null) existing.setAddress(dto.getAddress());
        if (dto.getRemark() != null) existing.setRemark(dto.getRemark());

        sysUserMapper.updateById(existing);
        return SysUserDetailVo.objToVo(existing);
    }
}
