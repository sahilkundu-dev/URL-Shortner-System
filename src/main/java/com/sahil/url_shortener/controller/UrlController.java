package com.sahil.url_shortener.controller;

import com.sahil.url_shortener.dto.ShortenRequest;
import com.sahil.url_shortener.dto.ShortenResponse;
import com.sahil.url_shortener.service.UrlService;
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
        String shortUrl = urlService.shortenUrl(request.getLongUrl());
        return ResponseEntity.ok(new ShortenResponse(shortUrl, request.getLongUrl()));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String longUrl = urlService.getLongUrl(shortCode);
        return ResponseEntity.status(302).location(URI.create(longUrl)).build();
    }
}