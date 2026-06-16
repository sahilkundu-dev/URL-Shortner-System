package com.sahil.url_shortener.service;

import com.sahil.url_shortener.entity.ClickEntity;
import com.sahil.url_shortener.entity.UrlEntity;
import com.sahil.url_shortener.exception.UrlExpiredException;
import com.sahil.url_shortener.exception.UrlNotFoundException;
import com.sahil.url_shortener.repository.ClickRepository;
import com.sahil.url_shortener.repository.UrlRepository;
import com.sahil.url_shortener.util.Base62Util;
import com.sahil.url_shortener.util.UrlValidatorUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UrlServiceImpl implements UrlService {

    private final UrlRepository urlRepository;
    private final ClickRepository clickRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.base-url}")
    private String baseUrl;

    private static final long DEFAULT_CACHE_TTL_HOURS = 24;

    @Override
    public String shortenUrl(String longUrl, Integer ttlHours) {

        // Step 1: Validate the URL
        UrlValidatorUtil.validate(longUrl);

        // Step 2: Check if already shortened
        var existing = urlRepository.findByLongUrl(longUrl);
        if (existing.isPresent()) {
            return baseUrl + "/" + existing.get().getShortCode();
        }

        // Step 3: Compute expiry timestamp if TTL provided
        LocalDateTime expiresAt = (ttlHours != null && ttlHours > 0)
                ? LocalDateTime.now().plusHours(ttlHours)
                : null;

        // Step 4: Save to MySQL with temp shortCode
        UrlEntity entity = UrlEntity.builder()
                .longUrl(longUrl)
                .shortCode("temp")
                .expiresAt(expiresAt)
                .build();
        entity = urlRepository.save(entity);

        // Step 5: Encode ID to Base62
        String shortCode = Base62Util.encode(entity.getId());

        // Step 6: Update with real shortCode
        entity.setShortCode(shortCode);
        urlRepository.save(entity);

        // Step 7: Cache in Redis
        // If URL has custom TTL, align Redis TTL to match
        // If no TTL, use default 24h cache TTL
        long cacheTtlHours = (ttlHours != null && ttlHours > 0)
                ? ttlHours
                : DEFAULT_CACHE_TTL_HOURS;

        redisTemplate.opsForValue().set(
                shortCode, longUrl, cacheTtlHours, TimeUnit.HOURS
        );

        return baseUrl + "/" + shortCode;
    }

    @Override
    public String getLongUrl(String shortCode, String ipAddress, String userAgent) {

        // Step 1: Check Redis cache first (fast path)
        String cachedUrl = redisTemplate.opsForValue().get(shortCode);

        // Step 2: Always fetch entity for click recording + expiry check
        UrlEntity entity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Step 3: Check expiry — even if Redis has it cached
        // Redis TTL is aligned but checking DB is the authoritative source
        if (entity.getExpiresAt() != null &&
                LocalDateTime.now().isAfter(entity.getExpiresAt())) {

            // Evict from Redis immediately so next request doesn't serve stale
            redisTemplate.delete(shortCode);
            throw new UrlExpiredException(shortCode);
        }

        // Step 4: Cache miss — repopulate Redis
        if (cachedUrl == null) {
            redisTemplate.opsForValue().set(
                    shortCode, entity.getLongUrl(), DEFAULT_CACHE_TTL_HOURS, TimeUnit.HOURS
            );
        }

        // Step 5: Record click
        ClickEntity click = ClickEntity.builder()
                .urlEntity(entity)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        clickRepository.save(click);

        return entity.getLongUrl();
    }
}