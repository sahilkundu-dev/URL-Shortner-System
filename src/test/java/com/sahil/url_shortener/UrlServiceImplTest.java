package com.sahil.url_shortener;

import com.sahil.url_shortener.exception.UrlValidationException;
import com.sahil.url_shortener.entity.ClickEntity;
import com.sahil.url_shortener.entity.UrlEntity;
import com.sahil.url_shortener.exception.UrlNotFoundException;
import com.sahil.url_shortener.repository.ClickRepository;
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
import com.sahil.url_shortener.exception.UrlExpiredException;
import java.time.LocalDateTime;

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
    private ClickRepository clickRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UrlServiceImpl urlService;

    private static final String BASE_URL = "http://localhost:8080";
    private static final String LONG_URL = "https://www.example.com/some/very/long/url";
    private static final String SHORT_CODE = "000001";
    private static final String IP = "127.0.0.1";
    private static final String USER_AGENT = "test-agent";

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
        String result = urlService.shortenUrl(LONG_URL, null);

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
        String result = urlService.shortenUrl(LONG_URL, null);

        // ASSERT
        assertThat(result).isEqualTo(BASE_URL + "/" + SHORT_CODE);
        verify(urlRepository, never()).save(any(UrlEntity.class));
    }

    // ─── TEST 3 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLongUrl: cache hit returns URL from Redis without touching DB")
    void getLongUrl_cacheHit_returnsFromRedis() {

        // ARRANGE
        UrlEntity entity = UrlEntity.builder()
                .id(1L).longUrl(LONG_URL).shortCode(SHORT_CODE).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(SHORT_CODE)).thenReturn(LONG_URL);
        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.of(entity));
        when(clickRepository.save(any(ClickEntity.class))).thenReturn(null);

        // ACT
        String result = urlService.getLongUrl(SHORT_CODE, IP, USER_AGENT);

        // ASSERT
        assertThat(result).isEqualTo(LONG_URL);
        verify(valueOperations, never()).set(any(), any(), anyLong(), any());
    }

    // ─── TEST 4 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLongUrl: cache miss fetches from DB and repopulates Redis")
    void getLongUrl_cacheMiss_fetchesFromDbAndCaches() {

        // ARRANGE
        UrlEntity entity = UrlEntity.builder()
                .id(1L).longUrl(LONG_URL).shortCode(SHORT_CODE).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(SHORT_CODE)).thenReturn(null);
        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.of(entity));
        when(clickRepository.save(any(ClickEntity.class))).thenReturn(null);

        // ACT
        String result = urlService.getLongUrl(SHORT_CODE, IP, USER_AGENT);

        // ASSERT
        assertThat(result).isEqualTo(LONG_URL);
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
        assertThatThrownBy(() -> urlService.getLongUrl(SHORT_CODE, IP, USER_AGENT))
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
                .id(1L).longUrl(LONG_URL).shortCode(SHORT_CODE).build();
        when(urlRepository.save(any(UrlEntity.class))).thenReturn(savedEntity);

        // ACT
        urlService.shortenUrl(LONG_URL, null);

        // ASSERT
        verify(valueOperations).set(any(), any(), eq(24L), eq(TimeUnit.HOURS));
    }

    // ─── TEST 7 — NEW ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLongUrl: click is recorded on every redirect")
    void getLongUrl_recordsClickOnEveryRedirect() {

        // ARRANGE
        UrlEntity entity = UrlEntity.builder()
                .id(1L).longUrl(LONG_URL).shortCode(SHORT_CODE).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(SHORT_CODE)).thenReturn(LONG_URL);
        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.of(entity));

        // ACT
        urlService.getLongUrl(SHORT_CODE, IP, USER_AGENT);

        // ASSERT
        verify(clickRepository).save(any(ClickEntity.class));
    }

    // ─── TEST 8 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("shortenUrl: null URL throws UrlValidationException")
    void shortenUrl_nullUrl_throwsValidationException() {

        assertThatThrownBy(() -> urlService.shortenUrl(null, null))
                .isInstanceOf(UrlValidationException.class)
                .hasMessageContaining("empty");
    }

// ─── TEST 9 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("shortenUrl: blank URL throws UrlValidationException")
    void shortenUrl_blankUrl_throwsValidationException() {

        assertThatThrownBy(() -> urlService.shortenUrl("   ", null))
                .isInstanceOf(UrlValidationException.class)
                .hasMessageContaining("empty");
    }

// ─── TEST 10 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("shortenUrl: javascript scheme throws UrlValidationException")
    void shortenUrl_javascriptUrl_throwsValidationException() {

        assertThatThrownBy(() -> urlService.shortenUrl("javascript:alert(1)", null))
                .isInstanceOf(UrlValidationException.class)
                .hasMessageContaining("scheme not allowed");
    }

// ─── TEST 11 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("shortenUrl: malformed URL throws UrlValidationException")
    void shortenUrl_malformedUrl_throwsValidationException() {

        assertThatThrownBy(() -> urlService.shortenUrl("not-a-url", null))
                .isInstanceOf(UrlValidationException.class)
                .hasMessageContaining("Invalid URL format");
    }

    // ─── TEST 12 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("shortenUrl: with TTL sets Redis expiry to ttlHours not default")
    void shortenUrl_withTtl_setsRedisExpiryToTtlHours() {

        // ARRANGE
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(urlRepository.findByLongUrl(LONG_URL)).thenReturn(Optional.empty());

        UrlEntity savedEntity = UrlEntity.builder()
                .id(1L).longUrl(LONG_URL).shortCode(SHORT_CODE).build();
        when(urlRepository.save(any(UrlEntity.class))).thenReturn(savedEntity);

        // ACT — shorten with 6-hour TTL
        urlService.shortenUrl(LONG_URL, 6);

        // ASSERT — Redis TTL should be 6 hours, not 24
        verify(valueOperations).set(any(), any(), eq(6L), eq(TimeUnit.HOURS));
    }

// ─── TEST 13 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLongUrl: expired URL throws UrlExpiredException and evicts Redis")
    void getLongUrl_expiredUrl_throwsExceptionAndEvictsRedis() {

        // ARRANGE
        UrlEntity expiredEntity = UrlEntity.builder()
                .id(1L)
                .longUrl(LONG_URL)
                .shortCode(SHORT_CODE)
                .expiresAt(LocalDateTime.now().minusHours(1)) // expired 1 hour ago
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(SHORT_CODE)).thenReturn(null);
        when(urlRepository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(expiredEntity));

        // ACT + ASSERT
        assertThatThrownBy(() -> urlService.getLongUrl(SHORT_CODE, IP, USER_AGENT))
                .isInstanceOf(UrlExpiredException.class)
                .hasMessageContaining(SHORT_CODE);

        // Redis should be evicted
        verify(redisTemplate).delete(SHORT_CODE);
        // Click should NOT be recorded for expired URL
        verify(clickRepository, never()).save(any());
    }
}