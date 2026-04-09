package com.qsy.edifice.config;

import com.qsy.edifice.utils.FastJsonRedisSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 配置两个 Redis 实例：
 * 1. 主 Redis（业务数据）
 * 2. 中央 Redis（微信 access_token 中控）
 */
@Configuration
public class RedisConfig {

    // 主 Redis 配置（业务数据）
    @Value("${spring.data.redis.host}")
    private String primaryHost;

    @Value("${spring.data.redis.port}")
    private int primaryPort;

    @Value("${spring.data.redis.password}")
    private String primaryPassword;

    @Value("${spring.data.redis.database}")
    private int primaryDatabase;

    /**
     * 主 Redis 连接工厂（业务数据）
     */
    @Bean
    @Primary
    public RedisConnectionFactory primaryRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(primaryHost);
        config.setPort(primaryPort);
        config.setPassword(primaryPassword);
        config.setDatabase(primaryDatabase);
        return new LettuceConnectionFactory(config);
    }

    /**
     * 主 Redis Template（业务数据）
     */
    @Bean
    @Primary
    @SuppressWarnings(value = { "unchecked", "rawtypes" })
    public RedisTemplate<Object, Object> redisTemplate(
            @Qualifier("primaryRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        FastJsonRedisSerializer serializer = new FastJsonRedisSerializer(Object.class);

        // 使用StringRedisSerializer来序列化和反序列化redis的key值
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);

        // Hash的key也采用StringRedisSerializer的序列化方式
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}