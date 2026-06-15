package com.sahil.url_shortener.service;

import com.sahil.url_shortener.entity.ClickEntity;
import com.sahil.url_shortener.entity.UrlEntity;
import com.sahil.url_shortener.exception.UrlNotFoundException;
import com.sahil.url_shortener.repository.ClickRepository;
import com.sahil.url_shortener.repository.UrlRepository;
import com.sahil.url_shortener.util.Base62Util;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.sahil.url_shortener.util.UrlValidatorUtil;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UrlServiceImpl implements UrlService {

    private final UrlRepository urlRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ClickRepository clickRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    private static final long CACHE_TTL_HOURS = 24;

    @Override
    public String shortenUrl(String longUrl) {

        // Validate before doing anything else
        UrlValidatorUtil.validate(longUrl);

        // Step 1: Check if this longUrl was already shortened (avoid duplicates)
        var existing = urlRepository.findByLongUrl(longUrl);
        if (existing.isPresent()) {
            return baseUrl + "/" + existing.get().getShortCode();
        }

        // Step 2: Save to MySQL first to get the auto-generated ID
        UrlEntity entity = UrlEntity.builder()
                .longUrl(longUrl)
                .shortCode("temp")
                .build();
        entity = urlRepository.save(entity);

        // Step 3: Encode the ID to Base62 to generate the shortCode
        String shortCode = Base62Util.encode(entity.getId());

        // Step 4: Update the entity with the real shortCode
        entity.setShortCode(shortCode);
        urlRepository.save(entity);

        // Step 5: Cache shortCode -> longUrl in Redis with TTL
        redisTemplate.opsForValue().set(shortCode, longUrl, CACHE_TTL_HOURS, TimeUnit.HOURS);

        return baseUrl + "/" + shortCode;
    }

    @Override
    public String getLongUrl(String shortCode, String ipAddress, String userAgent) {

        // Step 1: Check Redis cache first (fast path)
        String cachedUrl = redisTemplate.opsForValue().get(shortCode);

        // Step 2: Find the entity for click recording (always needed now)
        UrlEntity entity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Step 3: If cache miss — repopulate Redis
        if (cachedUrl == null) {
            redisTemplate.opsForValue().set(
                    shortCode, entity.getLongUrl(), CACHE_TTL_HOURS, TimeUnit.HOURS
            );
        }

        // Step 4: Record the click
        ClickEntity click = ClickEntity.builder().urlEntity(entity).ipAddress(ipAddress).userAgent(userAgent).build();
        clickRepository.save(click);

        return entity.getLongUrl();
    }
}