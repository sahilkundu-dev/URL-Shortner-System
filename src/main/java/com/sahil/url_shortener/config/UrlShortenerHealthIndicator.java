package com.sahil.url_shortener.config;

import com.sahil.url_shortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UrlShortenerHealthIndicator implements HealthIndicator {

    private final UrlRepository urlRepository;

    @Override
    public Health health() {
        try {
            // Verify the url_mappings table is reachable and queryable
            long count = urlRepository.count();

            return Health.up()
                    .withDetail("service", "URL Shortener")
                    .withDetail("database-table", "url_mappings")
                    .withDetail("total-urls-stored", count)
                    .withDetail("status", "Table reachable and queryable")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("service", "URL Shortener")
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "url_mappings table unreachable")
                    .build();
        }
    }
}