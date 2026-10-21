package com.qsy.edifice.service;

import com.qsy.edifice.config.FileStreamingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FileStreamingConfigTests {
    @Test void honorsConfiguredDownloadTimeoutInsteadOfHardcodedTenMinutes() {
        var properties = new WebMvcProperties();
        properties.getAsync().setRequestTimeout(Duration.ofHours(24));
        var config = new FileStreamingConfig(properties);
        var async = new AsyncSupportConfigurer();
        config.configureAsyncSupport(async);
        assertEquals(86_400_000L, ReflectionTestUtils.getField(async, "timeout"));
    }
}
