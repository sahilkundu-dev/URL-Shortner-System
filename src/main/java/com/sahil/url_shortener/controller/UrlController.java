package com.sahil.url_shortener.controller;

import com.sahil.url_shortener.dto.ShortenRequest;
import com.sahil.url_shortener.dto.ShortenResponse;
import com.sahil.url_shortener.service.RateLimiterService;
import com.sahil.url_shortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;
    private final RateLimiterService rateLimiterService;

    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest request, HttpServletRequest httpRequest) {

        // Extract client IP and check rate limit FIRST
        // before any validation or DB work
        String clientIp = getClientIp(httpRequest);
        rateLimiterService.checkRateLimit(clientIp);

        String shortUrl = urlService.shortenUrl(request.getLongUrl(), request.getTtlHours());
        return ResponseEntity.ok(new ShortenResponse(shortUrl, request.getLongUrl()));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {

        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        String longUrl = urlService.getLongUrl(shortCode, ipAddress, userAgent);
        return ResponseEntity.status(302).location(URI.create(longUrl)).build();
    }

    /**
     * Extract real client IP — handles reverse proxies (Nginx, AWS ALB, Cloudflare).
     * X-Forwarded-For header contains the original client IP when behind a proxy.
     * Falls back to getRemoteAddr() for direct connections (localhost dev).
     */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if(forwarded != null && !forwarded.isBlank()) {
            // X-Forworded-For can contain a chain: "client, proxyl, proxy2"
            // The first IP is always the original client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}