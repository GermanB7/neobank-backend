package com.neobank.observability.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operational health summary for Sprint 18.
 * Uses lightweight direct checks so it remains compatible with Spring Boot 4 package moves.
 */
@RestController
@RequestMapping("/actuator/health-summary")
public class OperationalHealthController {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";
    private static final String STATUS_DEGRADED = "DEGRADED";

    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;

    public OperationalHealthController(
            DataSource dataSource,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider
    ) {
        this.dataSource = dataSource;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
    }

    @GetMapping
    public Map<String, Object> getHealthSummary() {
        Map<String, String> components = new LinkedHashMap<>();

        boolean dbUp = isDatabaseUp();
        boolean redisUp = isRedisUp();
        boolean diskUp = isDiskSpaceHealthy();

        components.put("db", dbUp ? STATUS_UP : STATUS_DOWN);
        components.put("redis", redisUp ? STATUS_UP : STATUS_DOWN);
        components.put("diskSpace", diskUp ? STATUS_UP : STATUS_DOWN);
        components.put("ping", STATUS_UP);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", deriveStatus(dbUp, redisUp, diskUp));
        summary.put("components", components);
        summary.put("timestamp", Instant.now().toString());
        return summary;
    }

    private boolean isDatabaseUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isRedisUp() {
        RedisConnectionFactory factory = redisConnectionFactoryProvider.getIfAvailable();
        if (factory == null) {
            return false;
        }

        try (RedisConnection connection = factory.getConnection()) {
            String ping = connection.ping();
            return ping != null && !ping.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isDiskSpaceHealthy() {
        try {
            return new File(".").getUsableSpace() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String deriveStatus(boolean dbUp, boolean redisUp, boolean diskUp) {
        if (dbUp && redisUp && diskUp) {
            return STATUS_UP;
        }
        if (dbUp) {
            return STATUS_DEGRADED;
        }
        return STATUS_DOWN;
    }
}
