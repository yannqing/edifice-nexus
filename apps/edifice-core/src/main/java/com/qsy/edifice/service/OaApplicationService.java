package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.CreateOaApplicationDto;
import com.qsy.edifice.domain.dto.GetOaApplicationListDto;
import com.qsy.edifice.domain.dto.SubmitOaApplicationDto;
import com.qsy.edifice.domain.dto.UpdateOaApplicationDto;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.vo.OaApplicationTypeVo;
import com.qsy.edifice.domain.vo.OaApplicationVo;
import com.qsy.edifice.service.ApprovalFlowService;

import java.util.List;

public interface OaApplicationService {

    List<OaApplicationTypeVo> listTypes();

    Page<OaApplicationVo> list(GetOaApplicationListDto dto, Long currentUserId);

    OaApplicationVo getById(Long applicationId, Long currentUserId);

    Long create(CreateOaApplicationDto dto, Long currentUserId);

    void update(UpdateOaApplicationDto dto, Long currentUserId);

    void submit(Long applicationId, SubmitOaApplicationDto dto, Long currentUserId);

    void withdraw(Long applicationId, Long currentUserId);

    Page<OaApplicationVo> listMyPending(GetOaApplicationListDto dto, Long currentUserId);

    ApprovalFlowService.ApprovalResult approve(ApproveDto dto, Long currentUserId);
}
