package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.CreateOaApplicationDto;
import com.qsy.edifice.domain.dto.GetOaApplicationListDto;
import com.qsy.edifice.domain.dto.UpdateOaApplicationDto;
import com.qsy.edifice.domain.vo.OaApplicationTypeVo;
import com.qsy.edifice.domain.vo.OaApplicationVo;

import java.util.List;

public interface OaApplicationService {

    List<OaApplicationTypeVo> listTypes();

    Page<OaApplicationVo> list(GetOaApplicationListDto dto, Long currentUserId);

    OaApplicationVo getById(Long applicationId, Long currentUserId);

    Long create(CreateOaApplicationDto dto, Long currentUserId);

    void update(UpdateOaApplicationDto dto, Long currentUserId);

    void submit(Long applicationId, Long currentUserId);

    void withdraw(Long applicationId, Long currentUserId);
}
