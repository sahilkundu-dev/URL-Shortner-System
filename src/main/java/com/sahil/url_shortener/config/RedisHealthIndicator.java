package com.sahil.url_shortener.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public Health health() {
        try {
            // PING command — Redis responds with "PONG" if healthy
            String response = redisTemplate
                    .getConnectionFactory()
                    .getConnection()
                    .ping();

            if ("PONG".equalsIgnoreCase(response)) {
                return Health.up()
                        .withDetail("server", "Redis")
                        .withDetail("response", response)
                        .withDetail("status", "Connected and responding")
                        .build();
            }

            return Health.down()
                    .withDetail("server", "Redis")
                    .withDetail("response", response)
                    .withDetail("status", "Unexpected response to PING")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("server", "Redis")
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "Connection failed")
                    .build();
        }
    }
}