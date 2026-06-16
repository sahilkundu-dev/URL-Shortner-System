package com.sahil.url_shortener.controller;

import com.sahil.url_shortener.dto.ShortenRequest;
import com.sahil.url_shortener.dto.ShortenResponse;
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

    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest request) {
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
}