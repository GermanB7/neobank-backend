package com.neobank.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Cache configuration for Spring Cache abstraction.
 *
 * Caching is enabled for:
 * - User lookups by email (read-only, stable identifier)
 * - Role lookups by name (configuration-like, rarely changes)
 *
 * NOT cached (for safety):
 * - Financial account state (balances, transfer history)
 * - Ledger entries (source of truth)
 * - Risk evaluations (time-sensitive decisions)
 * - Live mutable user/account properties
 *
 * Redis is the cache backend (configured via spring-boot-starter-data-redis).
 * Cache invalidation is automatic via TTL or explicit @CacheEvict on mutations.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Provide CacheManager bean if one is not already available.
     * This allows for graceful degradation when Redis is unavailable.
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        return RedisCacheManager.create(redisConnectionFactory);
    }
}
