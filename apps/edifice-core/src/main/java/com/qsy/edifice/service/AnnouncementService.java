package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.CreateAnnouncementDto;
import com.qsy.edifice.domain.dto.GetAnnouncementListDto;
import com.qsy.edifice.domain.dto.UpdateAnnouncementDto;
import com.qsy.edifice.domain.vo.AnnouncementVo;

import java.util.List;

/**
 * 公告服务接口
 */
public interface AnnouncementService {

    /**
     * 分页 + 条件查询公告列表（管理端）
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<AnnouncementVo> getAnnouncementList(GetAnnouncementListDto dto);

    /**
     * 获取首页展示用的近期已发布公告（按优先级倒序 + 发布时间倒序，已过期不展示）
     * @param limit 最多返回条数，默认 5
     * @return 公告列表
     */
    List<AnnouncementVo> getRecentPublished(Integer limit);

    /**
     * 根据 id 查询公告详情
     * @param id 公告id
     * @return 公告详情
     */
    AnnouncementVo getAnnouncementById(Long id);

    /**
     * 创建公告（草稿或立即发布）
     * @param dto 创建参数
     * @param userId 当前用户id
     * @return 公告id
     */
    Long createAnnouncement(CreateAnnouncementDto dto, Long userId);

    /**
     * 更新公告（仅草稿或已下线可编辑内容）
     * @param dto 更新参数
     */
    void updateAnnouncement(UpdateAnnouncementDto dto);

    /**
     * 发布公告（0-草稿 或 2-已下线 → 1-已发布，并记录 publishTime / publishUserId）
     * @param announcementId 公告id
     * @param userId 当前用户id
     */
    void publishAnnouncement(Long announcementId, Long userId);

    /**
     * 下线公告（1-已发布 → 2-已下线）
     * @param announcementId 公告id
     */
    void unpublishAnnouncement(Long announcementId);

    /**
     * 删除公告（逻辑删除）
     * @param announcementId 公告id
     */
    void deleteAnnouncement(Long announcementId);
}
