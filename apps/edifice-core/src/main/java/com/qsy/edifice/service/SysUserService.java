package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetUserListDto;
import com.qsy.edifice.domain.dto.SysUserCreateDto;
import com.qsy.edifice.domain.dto.SysUserUpdateDto;
import com.qsy.edifice.domain.dto.UpdateProfileDto;
import com.qsy.edifice.domain.vo.SysUserCandidateVo;
import com.qsy.edifice.domain.vo.SysUserDetailVo;
import com.qsy.edifice.domain.vo.SysUserListVo;

import java.util.List;

public interface SysUserService {

    /**
     * 查询所有用户（管理员场景，需 menu:user-management 权限）
     *
     * @param getUserListDto 查询条件
     * @return 封装 vo 返回
     */
    Page<SysUserListVo> getAllUsers(GetUserListDto getUserListDto);

    /**
     * 查询候选人列表（业务场景：审批人/项目经理/项目成员选择等）
     *
     * <p>只返回精简字段（不含手机号/邮箱/身份证等敏感信息），权限要求仅需登录。
     * 默认只返回在职且启用的用户。
     *
     * @param getUserListDto 查询条件（关键字 / 部门 / 分页）
     * @return 精简候选 vo 分页
     */
    Page<SysUserCandidateVo> getCandidates(GetUserListDto getUserListDto);

    /**
     * 根据用户 id 查询用户详情
     *
     * @param id 用户 id
     * @return 返回用户详情 vo
     */
    SysUserDetailVo getUserById(Integer id);

    /**
     * 新增用户
     *
     * @param sysUserCreateDto 新增用户 dto
     * @return 返回新增结果
     */
    boolean createUser(SysUserCreateDto sysUserCreateDto);

    /**
     * 更新用户
     *
     * @param SysUserUpdateDto 更新用户请求
     * @return 更新结果
     */
    boolean updateUser(SysUserUpdateDto sysUserCreateDto);

    /**
     * 根据id删除用户
     * @param id 用户id
     * @return 返回删除结果
     */
    boolean deleteUser(Integer id);

    /**
     * 批量删除用户
     * @param ids 用户 id 数组
     * @return 返回删除结果
     */
    boolean deleteUserBath(List<Long> ids);

    /**
     * 获取当前登录用户的个人资料
     * @param userId 当前登录用户 id
     * @return 用户详情 vo
     */
    SysUserDetailVo getProfile(Long userId);

    /**
     * 更新当前登录用户的个人资料（仅允许自助修改的字段）
     * @param userId 当前登录用户 id
     * @param dto 资料更新 dto
     * @return 更新后的用户详情 vo
     */
    SysUserDetailVo updateProfile(Long userId, UpdateProfileDto dto);
}
