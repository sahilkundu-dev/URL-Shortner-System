package com.sahil.url_shortener.service;

import com.sahil.url_shortener.entity.ClickEntity;
import com.sahil.url_shortener.entity.UrlEntity;
import com.sahil.url_shortener.exception.UrlExpiredException;
import com.sahil.url_shortener.exception.UrlNotFoundException;
import com.sahil.url_shortener.repository.ClickRepository;
import com.sahil.url_shortener.repository.UrlRepository;
import com.sahil.url_shortener.util.Base62Util;
import com.sahil.url_shortener.util.UrlValidatorUtil;
import com.sahil.url_shortener.exception.AliasConflictException;
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
    public String shortenUrl(String longUrl, Integer ttlHours, String customAlias) {

        // Step 1: Validate the URL
        UrlValidatorUtil.validate(longUrl);

        // Step 2: Handle custom alias path
        if (customAlias != null && !customAlias.isBlank()) {

            // Validate and normalize the alias
            String normalizedAlias = UrlValidatorUtil.validateAndNormalizeAlias(customAlias);

            // Check if alias is already taken
            if (urlRepository.findByShortCode(normalizedAlias).isPresent()) {
                throw new AliasConflictException(normalizedAlias);
            }

            // Compute expiry
            LocalDateTime expiresAt = (ttlHours != null && ttlHours > 0)
                    ? LocalDateTime.now().plusHours(ttlHours)
                    : null;

            // Save with the custom alias as shortCode
            UrlEntity entity = UrlEntity.builder()
                    .longUrl(longUrl)
                    .shortCode(normalizedAlias)
                    .expiresAt(expiresAt)
                    .build();
            urlRepository.save(entity);

            // Cache in Redis
            long cacheTtl = (ttlHours != null && ttlHours > 0)
                    ? ttlHours
                    : DEFAULT_CACHE_TTL_HOURS;
            redisTemplate.opsForValue().set(
                    normalizedAlias, longUrl, cacheTtl, TimeUnit.HOURS
            );

            return baseUrl + "/" + normalizedAlias;
        }

        // Step 3: Base62 path — existing behaviour unchanged
        var existing = urlRepository.findByLongUrl(longUrl);
        if (existing.isPresent()) {
            return baseUrl + "/" + existing.get().getShortCode();
        }

        LocalDateTime expiresAt = (ttlHours != null && ttlHours > 0)
                ? LocalDateTime.now().plusHours(ttlHours)
                : null;

        UrlEntity entity = UrlEntity.builder()
                .longUrl(longUrl)
                .shortCode("temp")
                .expiresAt(expiresAt)
                .build();
        entity = urlRepository.save(entity);

        String shortCode = Base62Util.encode(entity.getId());
        entity.setShortCode(shortCode);
        urlRepository.save(entity);

        long cacheTtl = (ttlHours != null && ttlHours > 0)
                ? ttlHours
                : DEFAULT_CACHE_TTL_HOURS;
        redisTemplate.opsForValue().set(
                shortCode, longUrl, cacheTtl, TimeUnit.HOURS
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