package com.sahil.url_shortener.service;

import com.sahil.url_shortener.entity.UrlEntity;
import com.sahil.url_shortener.exception.UrlNotFoundException;
import com.sahil.url_shortener.repository.UrlRepository;
import com.sahil.url_shortener.util.Base62Util;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UrlServiceImpl implements UrlService {

    private final UrlRepository urlRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.base-url}")
    private String baseUrl;

    private static final long CACHE_TTL_HOURS = 24;

    @Override
    public String shortenUrl(String longUrl) {

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
    public String getLongUrl(String shortCode) {

        // Step 1: Check Redis cache first (fast path)
        String cachedUrl = redisTemplate.opsForValue().get(shortCode);
        if (cachedUrl != null) {
            return cachedUrl;
        }

        // Step 2: Cache miss - go to MySQL (slow path)
        UrlEntity entity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Step 3: Repopulate cache for next time
        redisTemplate.opsForValue().set(shortCode, entity.getLongUrl(), CACHE_TTL_HOURS, TimeUnit.HOURS);

        return entity.getLongUrl();
    }
}