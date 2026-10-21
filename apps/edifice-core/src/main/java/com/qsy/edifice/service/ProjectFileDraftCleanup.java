package com.qsy.edifice.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProjectFileDraftCleanup {
    @Resource private ProjectFilesService projectFilesService;

    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void expire() {
        try {
            int count = projectFilesService.expireDrafts();
            if (count > 0) log.info("项目文件草稿过期 count={}", count);
        } catch (Exception e) {
            log.error("项目文件草稿过期任务失败", e);
        }
    }
}
