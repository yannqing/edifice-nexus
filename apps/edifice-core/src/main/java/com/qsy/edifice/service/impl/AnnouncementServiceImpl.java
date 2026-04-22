package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.CreateAnnouncementDto;
import com.qsy.edifice.domain.dto.GetAnnouncementListDto;
import com.qsy.edifice.domain.dto.UpdateAnnouncementDto;
import com.qsy.edifice.domain.entity.Announcement;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.AnnouncementVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.AnnouncementMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.AnnouncementService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公告服务实现类
 *
 * 状态约定：0-草稿 / 1-已发布 / 2-已下线
 */
@Slf4j
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final int DEFAULT_RECENT_LIMIT = 5;
    private static final int MAX_RECENT_LIMIT = 20;

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_OFFLINE = 2;

    @Resource
    private AnnouncementMapper announcementMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    // ==================== 查询 ====================

    @Override
    public Page<AnnouncementVo> getAnnouncementList(GetAnnouncementListDto dto) {
        Integer current = dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        Integer pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;

        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeywords())) {
            wrapper.like(Announcement::getTitle, dto.getKeywords());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(Announcement::getStatus, dto.getStatus());
        }
        if (dto.getPriority() != null) {
            wrapper.eq(Announcement::getPriority, dto.getPriority());
        }
        // 按优先级高 → 发布时间新 → 创建时间新排序
        wrapper.orderByDesc(Announcement::getPriority)
                .orderByDesc(Announcement::getPublishTime)
                .orderByDesc(Announcement::getCreatedTime);

        Page<Announcement> page = announcementMapper.selectPage(new Page<>(current, pageSize), wrapper);
        List<AnnouncementVo> voList = toVoList(page.getRecords());

        Page<AnnouncementVo> voPage = new Page<>(current, pageSize, page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public List<AnnouncementVo> getRecentPublished(Integer limit) {
        int safeLimit = limit != null && limit > 0 ? Math.min(limit, MAX_RECENT_LIMIT) : DEFAULT_RECENT_LIMIT;

        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Announcement::getStatus, STATUS_PUBLISHED)
                // 未设置过期时间 或 尚未过期
                .and(w -> w.isNull(Announcement::getExpireTime)
                        .or()
                        .ge(Announcement::getExpireTime, LocalDateTime.now()))
                .orderByDesc(Announcement::getPriority)
                .orderByDesc(Announcement::getPublishTime)
                .last("LIMIT " + safeLimit);

        List<Announcement> records = announcementMapper.selectList(wrapper);
        return toVoList(records);
    }

    @Override
    public AnnouncementVo getAnnouncementById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        Announcement a = announcementMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(ErrorType.ANNOUNCEMENT_NOT_FOUND);
        }
        return toVoList(List.of(a)).get(0);
    }

    // ==================== 写操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAnnouncement(CreateAnnouncementDto dto, Long userId) {
        validateContent(dto.getTitle(), dto.getContent());

        int priority = dto.getPriority() != null ? dto.getPriority() : 0;
        int status = dto.getStatus() != null && dto.getStatus() == STATUS_PUBLISHED
                ? STATUS_PUBLISHED : STATUS_DRAFT;

        Announcement a = Announcement.builder()
                .title(dto.getTitle().trim())
                .content(dto.getContent())
                .priority(priority)
                .status(status)
                .expireTime(dto.getExpireTime())
                .build();

        if (status == STATUS_PUBLISHED) {
            a.setPublishTime(LocalDateTime.now());
            a.setPublishUserId(userId);
        }

        announcementMapper.insert(a);
        return a.getAnnouncementId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncement(UpdateAnnouncementDto dto) {
        if (dto == null || dto.getAnnouncementId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "公告ID不能为空");
        }
        Announcement existing = announcementMapper.selectById(dto.getAnnouncementId());
        if (existing == null) {
            throw new BusinessException(ErrorType.ANNOUNCEMENT_NOT_FOUND);
        }

        if (StringUtils.hasText(dto.getTitle())) existing.setTitle(dto.getTitle().trim());
        if (dto.getContent() != null) existing.setContent(dto.getContent());
        if (dto.getPriority() != null) existing.setPriority(dto.getPriority());
        if (dto.getExpireTime() != null) existing.setExpireTime(dto.getExpireTime());

        announcementMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishAnnouncement(Long announcementId, Long userId) {
        Announcement a = findOrThrow(announcementId);
        if (a.getStatus() != null && a.getStatus() == STATUS_PUBLISHED) {
            throw new BusinessException(ErrorType.ANNOUNCEMENT_STATUS_INVALID, "公告已处于发布状态");
        }
        a.setStatus(STATUS_PUBLISHED);
        a.setPublishTime(LocalDateTime.now());
        a.setPublishUserId(userId);
        announcementMapper.updateById(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublishAnnouncement(Long announcementId) {
        Announcement a = findOrThrow(announcementId);
        if (a.getStatus() == null || a.getStatus() != STATUS_PUBLISHED) {
            throw new BusinessException(ErrorType.ANNOUNCEMENT_STATUS_INVALID, "仅已发布的公告可以下线");
        }
        a.setStatus(STATUS_OFFLINE);
        announcementMapper.updateById(a);
    }

    @Override
    public void deleteAnnouncement(Long announcementId) {
        if (announcementId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        if (announcementMapper.selectById(announcementId) == null) {
            throw new BusinessException(ErrorType.ANNOUNCEMENT_NOT_FOUND);
        }
        announcementMapper.deleteById(announcementId);
    }

    // ==================== 辅助 ====================

    private Announcement findOrThrow(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        Announcement a = announcementMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(ErrorType.ANNOUNCEMENT_NOT_FOUND);
        }
        return a;
    }

    private void validateContent(String title, String content) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "公告标题不能为空");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "公告内容不能为空");
        }
        if (title.length() > 200) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "公告标题不能超过 200 字");
        }
    }

    /** 批量转 VO（发布人姓名批量查询，避免 N+1） */
    private List<AnnouncementVo> toVoList(List<Announcement> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();

        Set<Long> userIds = list.stream()
                .map(Announcement::getPublishUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));

        return list.stream().map(a -> {
            String name = null;
            if (a.getPublishUserId() != null) {
                SysUser u = userMap.get(a.getPublishUserId());
                if (u != null) name = u.getRealName() != null ? u.getRealName() : u.getUsername();
            }
            return AnnouncementVo.builder()
                    .announcementId(a.getAnnouncementId())
                    .title(a.getTitle())
                    .content(a.getContent())
                    .priority(a.getPriority())
                    .status(a.getStatus())
                    .publishTime(a.getPublishTime())
                    .expireTime(a.getExpireTime())
                    .publishUserId(a.getPublishUserId())
                    .publishUserName(name)
                    .createdTime(a.getCreatedTime())
                    .updatedTime(a.getUpdatedTime())
                    .build();
        }).collect(Collectors.toList());
    }
}
