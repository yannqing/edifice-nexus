package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateBidDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.dto.UpdateBidDto;
import com.qsy.edifice.domain.dto.UpdateBidStatusDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.ApprovalRecordVo;
import com.qsy.edifice.domain.vo.BidVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.BidFileMapper;
import com.qsy.edifice.mapper.BidMapper;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.ApprovalFlowService;
import com.qsy.edifice.service.BidService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 投标服务实现
 */
@Slf4j
@Service
public class BidServiceImpl implements BidService {

    // ========== bid_status 取值 ==========
    private static final int BID_PREPARING = 0;
    private static final int BID_SUBMITTED = 1;
    private static final int BID_WON = 2;
    private static final int BID_LOST = 3;
    private static final int BID_TERMINATED = 4;

    private static final Map<Integer, String> BID_STATUS_LABELS = Map.of(
            BID_PREPARING, "筹备",
            BID_SUBMITTED, "已投递",
            BID_WON, "中标",
            BID_LOST, "未中标",
            BID_TERMINATED, "终止"
    );

    // ========== approval_status 取值 ==========
    private static final int APPROVAL_DRAFT = 0;
    private static final int APPROVAL_IN_PROGRESS = 1;
    private static final int APPROVAL_APPROVED = 2;
    private static final int APPROVAL_REJECTED = 3;

    private static final Map<Integer, String> APPROVAL_STATUS_LABELS = Map.of(
            APPROVAL_DRAFT, "草稿",
            APPROVAL_IN_PROGRESS, "审核中",
            APPROVAL_APPROVED, "已通过",
            APPROVAL_REJECTED, "已驳回"
    );

    @Resource
    private BidMapper bidMapper;

    @Resource
    private BidFileMapper bidFileMapper;

    @Resource
    private FilesMapper filesMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ApprovalFlowService approvalFlowService;

    // ==================== 创建 / 更新 / 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CreateBidDto dto, Long operatorId) {
        if (dto == null || !StringUtils.hasText(dto.getBidName()) || dto.getOwnerUserId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目名称和负责人不能为空");
        }

        Bid bid = Bid.builder()
                .bidName(dto.getBidName().trim())
                .bidCode(dto.getBidCode())
                .ownerUserId(dto.getOwnerUserId())
                .tenderAmount(dto.getTenderAmount())
                .clientName(dto.getClientName())
                .bidDate(dto.getBidDate())
                .resultDate(dto.getResultDate())
                .description(dto.getDescription())
                .bidStatus(BID_PREPARING)
                .approvalStatus(APPROVAL_DRAFT)
                .build();
        bidMapper.insert(bid);

        saveFiles(bid.getBidId(), dto.getFiles());

        log.info("投标创建 bidId={} ownerUserId={} operator={}",
                bid.getBidId(), dto.getOwnerUserId(), operatorId);
        return bid.getBidId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UpdateBidDto dto, Long operatorId) {
        if (dto == null || dto.getBidId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "投标id不能为空");
        }
        Bid bid = bidMapper.selectById(dto.getBidId());
        if (bid == null) throw new BusinessException(ErrorType.BID_NOT_FOUND);
        // 审批中 / 已通过 禁止修改基础信息（避免内容与审批脱节）；草稿或驳回可改
        if (bid.getApprovalStatus() != null
                && (bid.getApprovalStatus() == APPROVAL_IN_PROGRESS
                    || bid.getApprovalStatus() == APPROVAL_APPROVED)) {
            throw new BusinessException(ErrorType.BID_STATUS_INVALID,
                    "投标审批中或已通过，无法修改基础信息");
        }

        if (StringUtils.hasText(dto.getBidName())) bid.setBidName(dto.getBidName().trim());
        if (dto.getBidCode() != null) bid.setBidCode(dto.getBidCode());
        if (dto.getOwnerUserId() != null) bid.setOwnerUserId(dto.getOwnerUserId());
        if (dto.getTenderAmount() != null) bid.setTenderAmount(dto.getTenderAmount());
        if (dto.getClientName() != null) bid.setClientName(dto.getClientName());
        if (dto.getBidDate() != null) bid.setBidDate(dto.getBidDate());
        if (dto.getResultDate() != null) bid.setResultDate(dto.getResultDate());
        if (dto.getDescription() != null) bid.setDescription(dto.getDescription());
        bidMapper.updateById(bid);

        if (dto.getFiles() != null) {
            // 全量替换
            LambdaQueryWrapper<BidFile> dw = new LambdaQueryWrapper<>();
            dw.eq(BidFile::getBidId, bid.getBidId());
            bidFileMapper.delete(dw);
            saveFiles(bid.getBidId(), dto.getFiles());
        }
        log.info("投标更新 bidId={} operator={}", bid.getBidId(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long bidId) {
        if (bidId == null) throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        Bid bid = bidMapper.selectById(bidId);
        if (bid == null) throw new BusinessException(ErrorType.BID_NOT_FOUND);
        if (bid.getApprovalStatus() != null && bid.getApprovalStatus() == APPROVAL_IN_PROGRESS) {
            throw new BusinessException(ErrorType.BID_STATUS_INVALID, "审批中的投标不可删除");
        }
        bidMapper.deleteById(bidId);
        LambdaQueryWrapper<BidFile> dw = new LambdaQueryWrapper<>();
        dw.eq(BidFile::getBidId, bidId);
        bidFileMapper.delete(dw);
    }

    // ==================== 状态切换 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBidStatus(UpdateBidStatusDto dto) {
        if (dto == null || dto.getBidId() == null || dto.getBidStatus() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        if (!BID_STATUS_LABELS.containsKey(dto.getBidStatus())) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "未知的投标状态");
        }
        Bid bid = bidMapper.selectById(dto.getBidId());
        if (bid == null) throw new BusinessException(ErrorType.BID_NOT_FOUND);

        // 简单护栏：终止 是一个终态；已投递 → 中标/未中标 也要求先走已投递；允许从 筹备 任意前进
        int current = bid.getBidStatus() == null ? BID_PREPARING : bid.getBidStatus();
        int next = dto.getBidStatus();
        if (current == BID_TERMINATED) {
            throw new BusinessException(ErrorType.BID_STATUS_INVALID, "已终止的投标不可变更状态");
        }
        if ((next == BID_WON || next == BID_LOST) && current != BID_SUBMITTED) {
            throw new BusinessException(ErrorType.BID_STATUS_INVALID,
                    "标的结果只能在已投递状态下更新");
        }

        bid.setBidStatus(next);
        if (dto.getBidDate() != null) bid.setBidDate(dto.getBidDate());
        if (dto.getResultDate() != null) bid.setResultDate(dto.getResultDate());
        bidMapper.updateById(bid);

        log.info("投标状态变更 bidId={} {}→{}", bid.getBidId(),
                BID_STATUS_LABELS.get(current), BID_STATUS_LABELS.get(next));
    }

    // ==================== 审批 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitApproval(Long bidId, Long firstApproverId, Long operatorId) {
        if (bidId == null || firstApproverId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "投标id / 一级审批人不能为空");
        }
        Bid bid = bidMapper.selectById(bidId);
        if (bid == null) throw new BusinessException(ErrorType.BID_NOT_FOUND);
        if (bid.getApprovalStatus() == APPROVAL_IN_PROGRESS) {
            throw new BusinessException(ErrorType.BID_STATUS_INVALID, "该投标已在审批中");
        }
        if (bid.getApprovalStatus() == APPROVAL_APPROVED) {
            throw new BusinessException(ErrorType.BID_STATUS_INVALID, "该投标已审批通过");
        }

        SubmitApprovalDto submit = new SubmitApprovalDto(
                ApprovalBizType.BID.getExt(),
                bidId,
                firstApproverId,
                bid.getBidName()
        );
        ApprovalRecords record = approvalFlowService.submit(submit, operatorId);

        bid.setApprovalStatus(APPROVAL_IN_PROGRESS);
        bid.setCurrentRecordId(record.getApprovalRecordId());
        bidMapper.updateById(bid);

        log.info("投标提交审批 bidId={} firstApprover={} recordId={}",
                bidId, firstApproverId, record.getApprovalRecordId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(ApproveDto dto, Long operatorId) {
        if (dto == null || dto.getRecordId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        ApprovalFlowService.ApprovalResult result = approvalFlowService.approve(dto, operatorId);
        if (result.bizType != ApprovalBizType.BID) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "此审批记录不属于投标");
        }

        Bid bid = bidMapper.selectById(result.bizId);
        if (bid == null) throw new BusinessException(ErrorType.BID_NOT_FOUND);

        if (result.rejected) {
            bid.setApprovalStatus(APPROVAL_REJECTED);
            bid.setCurrentRecordId(null);
        } else if (result.isFinal) {
            bid.setApprovalStatus(APPROVAL_APPROVED);
            bid.setCurrentRecordId(null);
        } else {
            bid.setApprovalStatus(APPROVAL_IN_PROGRESS);
            bid.setCurrentRecordId(result.nextRecordId);
        }
        bidMapper.updateById(bid);
    }

    // ==================== 查询 ====================

    @Override
    public List<BidVo> list(Integer bidStatus, Integer approvalStatus, String keyword) {
        LambdaQueryWrapper<Bid> w = new LambdaQueryWrapper<>();
        if (bidStatus != null) w.eq(Bid::getBidStatus, bidStatus);
        if (approvalStatus != null) w.eq(Bid::getApprovalStatus, approvalStatus);
        if (StringUtils.hasText(keyword)) {
            w.and(ww -> ww.like(Bid::getBidName, keyword)
                    .or().like(Bid::getBidCode, keyword)
                    .or().like(Bid::getClientName, keyword));
        }
        w.orderByDesc(Bid::getCreatedTime);
        return toVos(bidMapper.selectList(w), false);
    }

    @Override
    public BidVo getDetail(Long bidId) {
        Bid bid = bidMapper.selectById(bidId);
        if (bid == null) throw new BusinessException(ErrorType.BID_NOT_FOUND);
        return toVos(Collections.singletonList(bid), true).get(0);
    }

    @Override
    public List<BidVo> listMyPending(Long userId) {
        if (userId == null) return Collections.emptyList();
        List<ApprovalRecordVo> my = approvalFlowService.listPendingByApprover(userId, ApprovalBizType.BID);
        if (my.isEmpty()) return Collections.emptyList();
        Set<Long> ids = my.stream().map(ApprovalRecordVo::getBizId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyList();
        return toVos(bidMapper.selectBatchIds(ids), false);
    }

    // ==================== helpers ====================

    private void saveFiles(Long bidId, List<CreateBidDto.BidFileItem> files) {
        if (files == null || files.isEmpty()) return;
        for (CreateBidDto.BidFileItem item : files) {
            if (item.getFileId() == null) continue;
            BidFile bf = BidFile.builder()
                    .bidId(bidId)
                    .fileId(item.getFileId())
                    .fileCategory(item.getFileCategory())
                    .build();
            bidFileMapper.insert(bf);
        }
    }

    private List<BidVo> toVos(List<Bid> list, boolean withDetail) {
        if (list == null || list.isEmpty()) return Collections.emptyList();

        Set<Long> ownerIds = list.stream().map(Bid::getOwnerUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = ownerIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));

        // 一次拉出所有 bid 的附件
        Set<Long> bidIds = list.stream().map(Bid::getBidId).collect(Collectors.toSet());
        LambdaQueryWrapper<BidFile> fw = new LambdaQueryWrapper<>();
        fw.in(BidFile::getBidId, bidIds).orderByAsc(BidFile::getCreatedTime);
        List<BidFile> allBidFiles = bidFileMapper.selectList(fw);
        Set<Long> fileIds = allBidFiles.stream().map(BidFile::getFileId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Files> filesMap = fileIds.isEmpty() ? Collections.emptyMap()
                : filesMapper.selectBatchIds(fileIds).stream()
                .collect(Collectors.toMap(Files::getFileId, f -> f, (a, b) -> a));
        Map<Long, List<BidFile>> byBid = allBidFiles.stream()
                .collect(Collectors.groupingBy(BidFile::getBidId));

        return list.stream().map(b -> {
            SysUser owner = b.getOwnerUserId() == null ? null : userMap.get(b.getOwnerUserId());
            BidVo.BidVoBuilder vo = BidVo.builder()
                    .bidId(b.getBidId())
                    .bidName(b.getBidName())
                    .bidCode(b.getBidCode())
                    .ownerUserId(b.getOwnerUserId())
                    .ownerUserName(owner == null ? null :
                            (owner.getRealName() != null ? owner.getRealName() : owner.getUsername()))
                    .tenderAmount(b.getTenderAmount())
                    .bidStatus(b.getBidStatus())
                    .bidStatusLabel(BID_STATUS_LABELS.getOrDefault(b.getBidStatus(), "未知"))
                    .bidDate(b.getBidDate())
                    .resultDate(b.getResultDate())
                    .clientName(b.getClientName())
                    .description(b.getDescription())
                    .approvalStatus(b.getApprovalStatus())
                    .approvalStatusLabel(APPROVAL_STATUS_LABELS.getOrDefault(b.getApprovalStatus(), "未知"))
                    .currentRecordId(b.getCurrentRecordId())
                    .createdTime(b.getCreatedTime())
                    .updatedTime(b.getUpdatedTime());

            // 附件
            List<BidFile> bfs = byBid.getOrDefault(b.getBidId(), Collections.emptyList());
            List<BidVo.BidFileVo> fileVos = bfs.stream().map(bf -> {
                Files f = filesMap.get(bf.getFileId());
                return BidVo.BidFileVo.builder()
                        .bidFileId(bf.getBidFileId())
                        .fileId(bf.getFileId())
                        .fileName(f == null ? null :
                                (f.getDisplayName() != null ? f.getDisplayName() : f.getFileName()))
                        .fileUrl(f == null ? null : f.getFileUrl())
                        .fileExtension(f == null ? null : f.getFileExtension())
                        .fileSize(f == null || f.getFileSize() == null ? null : String.valueOf(f.getFileSize()))
                        .fileCategory(bf.getFileCategory())
                        .createdTime(bf.getCreatedTime())
                        .build();
            }).collect(Collectors.toList());
            vo.files(fileVos);

            // 审批链
            List<ApprovalRecordVo> chain = approvalFlowService.queryChain(ApprovalBizType.BID, b.getBidId());
            if (b.getCurrentRecordId() != null) {
                chain.stream()
                        .filter(v -> b.getCurrentRecordId().equals(v.getApprovalRecordId()))
                        .findFirst()
                        .ifPresent(v -> {
                            vo.currentApproverId(v.getApprover());
                            vo.currentApproverName(v.getApproverName());
                        });
            }
            if (withDetail) vo.approvalChain(chain);

            return vo.build();
        }).collect(Collectors.toList());
    }
}
