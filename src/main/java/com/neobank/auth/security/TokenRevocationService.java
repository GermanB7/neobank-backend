package com.neobank.auth.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenRevocationService {

    private final StringRedisTemplate redis;

    public TokenRevocationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String jti, Duration ttl) {
        redis.opsForValue().set(key(jti), "1", ttl);
    }

    public boolean isRevoked(String jti) {
        Boolean exists = redis.hasKey(key(jti));
        return exists != null && exists;
    }

    private String key(String jti) {
        return "revoked:jwt:" + jti;
    }
}