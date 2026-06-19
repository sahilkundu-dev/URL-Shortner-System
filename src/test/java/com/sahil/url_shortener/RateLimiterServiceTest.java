package com.sahil.url_shortener;

import com.sahil.url_shortener.exception.RateLimitException;
import com.sahil.url_shortener.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;
    private static final String TEST_IP = "192.168.1.1";
    private static final String OTHER_IP = "10.0.0.1";

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
    }

    // ─── TEST 14 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkRateLimit: first 10 requests from same IP succeed")
    void checkRateLimit_first10Requests_succeed() {

        // 10 requests should all pass without exception
        for (int i = 0; i < 10; i++) {
            final int requestNum = i;
            assertThatCode(() -> rateLimiterService.checkRateLimit(TEST_IP))
                    .as("Request %d should succeed", requestNum + 1)
                    .doesNotThrowAnyException();
        }
    }

    // ─── TEST 15 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkRateLimit: 11th request from same IP throws RateLimitException")
    void checkRateLimit_11thRequest_throwsRateLimitException() {

        // Exhaust the bucket
        for (int i = 0; i < 10; i++) {
            rateLimiterService.checkRateLimit(TEST_IP);
        }

        // 11th request should be rejected
        assertThatThrownBy(() -> rateLimiterService.checkRateLimit(TEST_IP))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Too many requests");
    }

    // ─── TEST 16 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkRateLimit: different IPs have independent buckets")
    void checkRateLimit_differentIps_independentBuckets() {

        // Exhaust IP 1's bucket completely
        for (int i = 0; i < 10; i++) {
            rateLimiterService.checkRateLimit(TEST_IP);
        }

        // IP 2 should still have a full bucket — completely unaffected
        assertThatCode(() -> rateLimiterService.checkRateLimit(OTHER_IP))
                .doesNotThrowAnyException();
    }
}