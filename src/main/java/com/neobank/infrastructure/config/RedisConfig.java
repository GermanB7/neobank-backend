package com.neobank.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for rate limiting, caching, and session management.
 *
 * Redis is used for:
 * - Rate limiting counters (ephemeral, with TTL)
 * - Session management (via spring-session-data-redis)
 * - Selective caching of read-only lookups (UserEntity, RoleEntity)
 *
 * NOT used for:
 * - Mutable financial state (balances, in-flight transfers, ledger)
 * - Source-of-truth financial data
 */
@Configuration
public class RedisConfig {

    /**
     * Configure RedisTemplate with String key serialization and JSON value serialization.
     * Used for rate limiting counters and caching non-financial metadata.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String key serialization
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // JSON value serialization for complex objects
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
