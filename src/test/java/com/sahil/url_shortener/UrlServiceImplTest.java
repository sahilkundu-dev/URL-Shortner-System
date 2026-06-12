package com.sahil.url_shortener;

import com.sahil.url_shortener.entity.UrlEntity;
import com.sahil.url_shortener.exception.UrlNotFoundException;
import com.sahil.url_shortener.repository.UrlRepository;
import com.sahil.url_shortener.service.UrlServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceImplTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UrlServiceImpl urlService;

    private static final String BASE_URL = "http://localhost:8080";
    private static final String LONG_URL = "https://www.example.com/some/very/long/url";
    private static final String SHORT_CODE = "000001";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "baseUrl", BASE_URL);
    }

    // ─── TEST 1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("shortenUrl: new URL is saved to DB and cached in Redis")
    void shortenUrl_newUrl_savesAndCaches() {

        // ARRANGE
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(urlRepository.findByLongUrl(LONG_URL)).thenReturn(Optional.empty());

        UrlEntity savedEntity = UrlEntity.builder()
                .id(1L)
                .longUrl(LONG_URL)
                .shortCode(SHORT_CODE)
                .build();
        when(urlRepository.save(any(UrlEntity.class))).thenReturn(savedEntity);

        // ACT
        String result = urlService.shortenUrl(LONG_URL);

        // ASSERT
        assertThat(result).isEqualTo(BASE_URL + "/" + SHORT_CODE);
        verify(urlRepository, times(2)).save(any(UrlEntity.class));
        verify(valueOperations).set(eq(SHORT_CODE), eq(LONG_URL), eq(24L), eq(TimeUnit.HOURS));
    }

    // ─── TEST 2 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("shortenUrl: duplicate URL returns existing short URL without new DB insert")
    void shortenUrl_duplicateUrl_returnsExisting() {

        // ARRANGE
        UrlEntity existingEntity = UrlEntity.builder()
                .id(1L)
                .longUrl(LONG_URL)
                .shortCode(SHORT_CODE)
                .build();
        when(urlRepository.findByLongUrl(LONG_URL)).thenReturn(Optional.of(existingEntity));

        // ACT
        String result = urlService.shortenUrl(LONG_URL);

        // ASSERT
        assertThat(result).isEqualTo(BASE_URL + "/" + SHORT_CODE);
        verify(urlRepository, never()).save(any(UrlEntity.class));
    }

    // ─── TEST 3 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLongUrl: cache hit returns URL from Redis without touching DB")
    void getLongUrl_cacheHit_returnsFromRedis() {

        // ARRANGE
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(SHORT_CODE)).thenReturn(LONG_URL);

        // ACT
        String result = urlService.getLongUrl(SHORT_CODE);

        // ASSERT
        assertThat(result).isEqualTo(LONG_URL);
        verify(urlRepository, never()).findByShortCode(any());
    }

    // ─── TEST 4 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLongUrl: cache miss fetches from DB and repopulates Redis")
    void getLongUrl_cacheMiss_fetchesFromDbAndCaches() {

        // ARRANGE
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(SHORT_CODE)).thenReturn(null);

        UrlEntity entity = UrlEntity.builder()
                .id(1L)
                .longUrl(LONG_URL)
                .shortCode(SHORT_CODE)
                .build();
        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.of(entity));

        // ACT
        String result = urlService.getLongUrl(SHORT_CODE);

        // ASSERT
        assertThat(result).isEqualTo(LONG_URL);
        verify(urlRepository).findByShortCode(SHORT_CODE);
        verify(valueOperations).set(eq(SHORT_CODE), eq(LONG_URL), eq(24L), eq(TimeUnit.HOURS));
    }

    // ─── TEST 5 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLongUrl: not found in Redis or DB throws UrlNotFoundException")
    void getLongUrl_notFound_throwsException() {

        // ARRANGE
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(SHORT_CODE)).thenReturn(null);
        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThatThrownBy(() -> urlService.getLongUrl(SHORT_CODE))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessageContaining(SHORT_CODE);
    }

    // ─── TEST 6 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("shortenUrl: Redis TTL is set to exactly 24 hours")
    void shortenUrl_newUrl_setsCacheTtlTo24Hours() {

        // ARRANGE
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(urlRepository.findByLongUrl(LONG_URL)).thenReturn(Optional.empty());

        UrlEntity savedEntity = UrlEntity.builder()
                .id(1L)
                .longUrl(LONG_URL)
                .shortCode(SHORT_CODE)
                .build();
        when(urlRepository.save(any(UrlEntity.class))).thenReturn(savedEntity);

        // ACT
        urlService.shortenUrl(LONG_URL);

        // ASSERT
        verify(valueOperations).set(any(), any(), eq(24L), eq(TimeUnit.HOURS));
    }
}