package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetUserListDto;
import com.qsy.edifice.domain.dto.SysUserCreateDto;
import com.qsy.edifice.domain.dto.SysUserUpdateDto;
import com.qsy.edifice.domain.dto.UpdateProfileDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.entity.SysDepartment;
import com.qsy.edifice.domain.entity.SysPosition;
import com.qsy.edifice.domain.entity.SysUserDepartment;
import com.qsy.edifice.domain.vo.SysUserDetailVo;
import com.qsy.edifice.domain.vo.SysUserListVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.mapper.SysDepartmentMapper;
import com.qsy.edifice.mapper.SysPositionMapper;
import com.qsy.edifice.mapper.SysUserDepartmentMapper;
import com.qsy.edifice.service.OaUserSyncService;
import com.qsy.edifice.service.SysUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysDepartmentMapper sysDepartmentMapper;

    @Resource
    private SysPositionMapper sysPositionMapper;

    @Resource
    private SysUserDepartmentMapper sysUserDepartmentMapper;

    @Resource
    private PasswordEncoder bCryptPasswordEncoder;

    @Resource
    private OaUserSyncService oaUserSyncService;

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
        applyDepartmentFilter(wrapper, getUserListDto);
        wrapper.orderByDesc(SysUser::getCreatedTime);

        Page<SysUser> sysUserPage = sysUserMapper.selectPage(new Page<>(current, pageSize), wrapper);

        List<SysUserListVo> sysUserListVos = sysUserPage.getRecords().stream()
                .map(SysUserListVo::objToVo)
                .toList();
        fillOrgNames(sysUserListVos);

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
        fillOrgName(userDetailVo);

        //4. 给用户角色赋值

        //5. 打印日志
        log.info("查询用户(id: {})", id);

        //6. 返回
        return userDetailVo;
    }

    private void applyDepartmentFilter(LambdaQueryWrapper<SysUser> wrapper, GetUserListDto dto) {
        if (dto.getDepartmentId() == null) {
            return;
        }
        Set<Long> departmentIds = new HashSet<>();
        departmentIds.add(dto.getDepartmentId());
        if (Boolean.TRUE.equals(dto.getIncludeChildren())) {
            departmentIds.addAll(findChildDepartmentIds(dto.getDepartmentId()));
        }

        List<SysUserDepartment> relations = sysUserDepartmentMapper.selectList(new LambdaQueryWrapper<SysUserDepartment>()
                .in(SysUserDepartment::getDepartmentId, departmentIds));
        Set<Long> relationUserIds = relations.stream()
                .map(SysUserDepartment::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        wrapper.and(w -> {
            w.in(SysUser::getDepartmentId, departmentIds);
            if (!relationUserIds.isEmpty()) {
                w.or().in(SysUser::getUserId, relationUserIds);
            }
        });
    }

    private Set<Long> findChildDepartmentIds(Long departmentId) {
        Set<Long> result = new HashSet<>();
        List<SysDepartment> departments = sysDepartmentMapper.selectList(null);
        Map<Long, List<SysDepartment>> byParent = departments.stream()
                .filter(item -> item.getParentId() != null)
                .collect(Collectors.groupingBy(SysDepartment::getParentId));
        List<Long> queue = new ArrayList<>();
        queue.add(departmentId);
        while (!queue.isEmpty()) {
            Long current = queue.remove(0);
            for (SysDepartment child : byParent.getOrDefault(current, List.of())) {
                if (child.getDepartmentId() != null && result.add(child.getDepartmentId())) {
                    queue.add(child.getDepartmentId());
                }
            }
        }
        return result;
    }

    private void fillOrgNames(List<SysUserListVo> users) {
        if (users == null || users.isEmpty()) return;
        Map<Long, SysDepartment> departments = sysDepartmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDepartment::getDepartmentId, Function.identity(), (a, b) -> a));
        Map<Long, SysPosition> positions = sysPositionMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysPosition::getPositionId, Function.identity(), (a, b) -> a));
        for (SysUserListVo user : users) {
            SysDepartment department = departments.get(user.getDepartmentId());
            if (department != null) user.setDepartmentName(department.getName());
            SysPosition position = positions.get(user.getPositionId());
            if (position != null) user.setPositionName(position.getName());
        }
    }

    private void fillOrgName(SysUserDetailVo user) {
        if (user == null) return;
        if (user.getDepartmentId() != null) {
            SysDepartment department = sysDepartmentMapper.selectById(user.getDepartmentId());
            if (department != null) user.setDepartmentName(department.getName());
        }
        if (user.getPositionId() != null) {
            SysPosition position = sysPositionMapper.selectById(user.getPositionId());
            if (position != null) user.setPositionName(position.getName());
        }
    }

    @Override
    public boolean createUser(SysUserCreateDto sysUserCreateDto) {
        throw new BusinessException(ErrorType.OPERATION_FAILED, "员工主数据请在 OA 系统维护，edifice 会自动同步");
    }

    @Override
    public boolean updateUser(SysUserUpdateDto sysUserUpdateDto) {
        throw new BusinessException(ErrorType.OPERATION_FAILED, "员工主数据请在 OA 系统维护，edifice 会自动同步");
    }

    @Override
    public boolean deleteUser(Integer id) {
        throw new BusinessException(ErrorType.OPERATION_FAILED, "员工主数据请在 OA 系统维护，edifice 会自动同步");
    }

    @Override
    public boolean deleteUserBath(List<Long> ids) {
        throw new BusinessException(ErrorType.OPERATION_FAILED, "员工主数据请在 OA 系统维护，edifice 会自动同步");
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
        SysUserDetailVo vo = SysUserDetailVo.objToVo(user);
        fillOrgName(vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserDetailVo updateProfile(Long userId, UpdateProfileDto dto) {
        throw new BusinessException(ErrorType.OPERATION_FAILED, "个人资料请在 OA 系统维护，edifice 会自动同步");
    }
}
