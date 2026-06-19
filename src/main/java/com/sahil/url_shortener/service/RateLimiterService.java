package com.sahil.url_shortener.service;

import com.sahil.url_shortener.exception.RateLimitException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    // One bucket per IP address — stored in memory
    // ConcurrentHashMap is thread-safe for concurrent requests
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Configuration constants — easy to change without touching logic
    private static final int CAPACITY = 10;           // max requests per window
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1); // window size

    /**
     * Returns the bucket for this IP, creating one if it doesn't exist.
     * computeIfAbsent is atomic — safe for concurrent requests from same IP.
     */
    private Bucket getBucketForIp(String ipAddress) {
        return buckets.computeIfAbsent(ipAddress, ip -> createNewBucket());
    }

    private Bucket createNewBucket() {
        // Greedy refill: refill all tokens at once after the period
        // Alternative: smooth refill (1 token every 6 seconds for 10/min)
        Bandwidth limit = Bandwidth.builder()
                .capacity(CAPACITY)
                .refillGreedy(CAPACITY, REFILL_PERIOD)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Attempt to consume 1 token from this IP's bucket.
     * Throws RateLimitException if bucket is empty.
     */
    public void checkRateLimit(String ipAddress) {
        Bucket bucket = getBucketForIp(ipAddress);

        if (!bucket.tryConsume(1)) {
            throw new RateLimitException(ipAddress);
        }
    }
}