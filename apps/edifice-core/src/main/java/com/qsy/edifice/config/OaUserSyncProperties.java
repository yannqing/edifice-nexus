package com.qsy.edifice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "oa.sync")
public class OaUserSyncProperties {

    private boolean enabled = true;

    private String baseUrl = "http://127.0.0.1:8088";

    private String apiKey;

    private int timeoutMs = 5000;

    private int batchSize = 50;

    private int maxRetryCount = 10;
}
