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

    /**
     * 新 OA 系统所在的数据库名。当前部署里与 edifice 共用同一个 MySQL 实例，
     * 因此直接跨库读取 OA 主数据最稳。
     */
    private String officeDatabase = "office_db";

    /**
     * 从 OA 同步过来的新用户在 edifice 侧的默认密码。
     * 密码不是同步主数据的一部分；正式登录建议走 SSO。
     */
    private String defaultPassword = "12345678";

    private int timeoutMs = 5000;

    private int batchSize = 50;

    private int maxRetryCount = 10;
}
