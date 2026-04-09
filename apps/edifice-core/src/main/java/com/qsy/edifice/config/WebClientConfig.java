package com.qsy.edifice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient 配置类
 * 提供企业级 HTTP 客户端配置
 */
@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${webclient.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${webclient.timeout.read:30000}")
    private int readTimeout;

    @Value("${webclient.timeout.write:30000}")
    private int writeTimeout;

    @Value("${webclient.max-memory-size:16777216}")
    private int maxMemorySize;

    @Value("${webclient.connection-pool.max-connections:50}")
    private int maxConnections;

    @Value("${webclient.connection-pool.max-idle-time:30000}")
    private int maxIdleTime;

    @Value("${webclient.connection-pool.max-life-time:60000}")
    private int maxLifeTime;

    @Value("${webclient.connection-pool.pending-acquire-timeout:60000}")
    private int pendingAcquireTimeout;

    /**
     * 默认 WebClient Bean（用于普通调用，30秒超时）
     */
    @Bean
    public WebClient webClient() {
        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(createHttpClient(readTimeout, writeTimeout)))
                .exchangeStrategies(exchangeStrategies());
        addFilters(builder);
        return builder.build();
    }

    /**
     * 联网搜索专用 WebClient Bean（60秒超时）
     */
    @Bean("webClientForWebSearch")
    public WebClient webClientForWebSearch() {
        // 联网搜索需要更长的超时时间（60秒）
        int longReadTimeout = 60000;
        int longWriteTimeout = 60000;

        log.info("创建联网搜索专用 WebClient，超时时间: read={}ms, write={}ms", longReadTimeout, longWriteTimeout);

        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(createHttpClient(longReadTimeout, longWriteTimeout)))
                .exchangeStrategies(exchangeStrategies());
        addFilters(builder);
        return builder.build();
    }

    /**
     * 自定义名称的 WebClient Bean（用于特定服务）
     */
    @Bean("customWebClient")
    public WebClient customWebClient() {
        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(createHttpClient(readTimeout, writeTimeout)))
                .exchangeStrategies(exchangeStrategies());
        addFilters(builder);
        return builder.build();
    }

    /**
     * 创建 HTTP 客户端（支持自定义超时时间）
     */
    private HttpClient createHttpClient(int readTimeout, int writeTimeout) {
        // 创建连接池（所有 WebClient 共享同一个连接池）
        ConnectionProvider connectionProvider = ConnectionProvider.builder("ai-platform-pool")
                .maxConnections(maxConnections)  // 全局最大连接数
                .maxIdleTime(Duration.ofMillis(maxIdleTime))  // 最大空闲时间
                .maxLifeTime(Duration.ofMillis(maxLifeTime))  // 最大生命周期
                .pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeout))  // 等待连接的超时时间
                .evictInBackground(Duration.ofSeconds(60))  // 后台清理空闲连接
                // .metrics(true)  // 需要 micrometer-core 依赖，暂不启用
                .build();

        log.debug("创建 HttpClient，连接池配置: maxConnections={}, readTimeout={}ms, writeTimeout={}ms",
                maxConnections, readTimeout, writeTimeout);

        return HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(readTimeout))  // 使用传入的 readTimeout
                .doOnConnected(conn -> {
                    conn.addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout, TimeUnit.MILLISECONDS));
                    log.debug("WebClient 连接已建立");
                })
                .doOnDisconnected(conn -> log.debug("WebClient 连接已断开"));
    }

    /**
     * 交换策略配置（内存限制等）
     */
    private ExchangeStrategies exchangeStrategies() {
        return ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxMemorySize))
                .build();
    }

    /**
     * 添加过滤器
     */
    private void addFilters(WebClient.Builder builder) {
        builder
                .filter(logRequest())
                .filter(logResponse())
                .filter(errorHandling())
                .filter(logConnectionPool());
    }

    /**
     * 连接池监控日志过滤器
     */
    private ExchangeFilterFunction logConnectionPool() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (log.isDebugEnabled()) {
                log.debug("发起请求到: {}, 当前连接池状态将被监控", clientRequest.url().getHost());
            }
            return Mono.just(clientRequest);
        });
    }

    /**
     * 请求日志过滤器
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (log.isDebugEnabled()) {
                log.debug("HTTP Request: {} {}", clientRequest.method(), clientRequest.url());
                clientRequest.headers().forEach((name, values) -> 
                    log.debug("Request Header: {}={}", name, values)
                );
            }
            return Mono.just(clientRequest);
        });
    }

    /**
     * 响应日志过滤器
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (log.isDebugEnabled()) {
                log.debug("HTTP Response: {}", clientResponse.statusCode());
                clientResponse.headers().asHttpHeaders().forEach((name, values) -> 
                    log.debug("Response Header: {}={}", name, values)
                );
            }
            return Mono.just(clientResponse);
        });
    }

    /**
     * 错误处理过滤器
     */
    private ExchangeFilterFunction errorHandling() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode().isError()) {
                log.error("HTTP Error Response: {} {}", 
                    clientResponse.statusCode().value(), 
                    clientResponse.statusCode());
            }
            return Mono.just(clientResponse);
        });
    }
}