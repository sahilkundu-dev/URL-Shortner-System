package com.sahil.url_shortener.service;

import com.sahil.url_shortener.entity.UrlEntity;
import com.sahil.url_shortener.repository.ClickRepository;
import com.sahil.url_shortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UrlCleanupScheduler {

    private final UrlRepository urlRepository;
    private final ClickRepository clickRepository;
    private final RedisTemplate<String, String> redisTemplate;

    // Runs every day at 2:00 AM
    // Cron format: second minute hour day month weekday
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredUrls() {

        LocalDateTime now = LocalDateTime.now();
        log.info("Starting expired URL cleanup at {}", now);

        List<UrlEntity> expired =
                urlRepository.findByExpiresAtBeforeAndExpiresAtIsNotNull(now);

        if (expired.isEmpty()) {
            log.info("No expired URLs found. Cleanup complete.");
            return;
        }

        log.info("Found {} expired URL(s) to delete", expired.size());

        for (UrlEntity url : expired) {
            // 1. Evict from Redis cache
            redisTemplate.delete(url.getShortCode());

            // 2. Delete child click records first (FK constraint)
            clickRepository.deleteByUrlEntity(url);

            // 3. Delete the URL mapping
            urlRepository.delete(url);

            log.info("Deleted expired URL: {} (shortCode: {}, expiredAt: {})",
                    url.getLongUrl(), url.getShortCode(), url.getExpiresAt());
        }

        log.info("Cleanup complete. Deleted {} expired URL(s).", expired.size());
    }
}