package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.CreateCollectionRecordDto;
import com.qsy.edifice.domain.dto.GetCollectionListDto;
import com.qsy.edifice.domain.dto.UpdateCollectionRecordDto;
import com.qsy.edifice.domain.vo.CollectionDetailVo;
import com.qsy.edifice.domain.vo.CollectionStatisticsVo;
import com.qsy.edifice.domain.vo.CollectionSummaryVo;

/**
 * 回款管理服务接口
 */
public interface CollectionService {

    /**
     * 项目级别的回款汇总列表（分页 + 按关键字/状态筛选）
     * @param dto 查询参数
     * @return 分页结果
     */
    Page<CollectionSummaryVo> getCollectionList(GetCollectionListDto dto);

    /**
     * 顶部统计卡片
     * @return 应收、已收、回款率、待回款
     */
    CollectionStatisticsVo getStatistics();

    /**
     * 项目回款详情（含按阶段聚合与原始记录）
     * @param projectId 项目id
     * @return 回款详情
     */
    CollectionDetailVo getCollectionDetail(Long projectId);

    /**
     * 新建回款记录
     * @param dto 参数
     * @param userId 当前用户id
     * @return 记录id
     */
    Long createCollectionRecord(CreateCollectionRecordDto dto, Long userId);

    /**
     * 更新回款记录
     * @param dto 参数
     */
    void updateCollectionRecord(UpdateCollectionRecordDto dto);

    /**
     * 删除回款记录（逻辑删除）
     * @param collectionRecordId 记录id
     */
    void deleteCollectionRecord(Long collectionRecordId);
}
