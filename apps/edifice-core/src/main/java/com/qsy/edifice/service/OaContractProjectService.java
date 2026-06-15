package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.OaContractProjectCreateDto;

import java.util.List;
import java.util.Map;

public interface OaContractProjectService {

    List<Map<String, Object>> listEnabledProjectTypes();

    Map<String, Object> getProjectStatus(Integer oaContractId);

    Map<String, Object> createProject(OaContractProjectCreateDto dto);
}
