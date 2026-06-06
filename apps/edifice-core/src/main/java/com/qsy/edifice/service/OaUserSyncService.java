package com.qsy.edifice.service;

import com.qsy.edifice.domain.entity.SysUser;

import java.util.Map;

public interface OaUserSyncService {

    void enqueueUpsert(SysUser user);

    void enqueueDelete(Long userId);

    int enqueueFullSync();

    int processPending();

    int syncFromOa();

    int syncOneFromOa(Integer oaAdminId);

    Map<String, Object> getStatus();
}
