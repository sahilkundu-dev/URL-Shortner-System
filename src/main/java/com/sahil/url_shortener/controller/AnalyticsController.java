package com.sahil.url_shortener.controller;

import com.sahil.url_shortener.dto.ClickResponse;
import com.sahil.url_shortener.entity.UrlEntity;
import com.sahil.url_shortener.exception.UrlNotFoundException;
import com.sahil.url_shortener.repository.ClickRepository;
import com.sahil.url_shortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final UrlRepository urlRepository;
    private final ClickRepository clickRepository;

    @GetMapping("/{shortCode}")
    public ResponseEntity<ClickResponse> getAnalytics(@PathVariable String shortCode) {

        UrlEntity urlEntity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        long totalClicks = clickRepository.countByUrlEntity(urlEntity);

        var recentClicks = clickRepository
                .findTop10ByUrlEntityOrderByClickedAtDesc(urlEntity)
                .stream()
                .map(click -> new ClickResponse.ClickDetail(
                        click.getClickedAt(),
                        click.getIpAddress(),
                        click.getUserAgent()
                ))
                .collect(Collectors.toList());

        ClickResponse response = new ClickResponse(
                shortCode,
                urlEntity.getLongUrl(),
                totalClicks,
                recentClicks
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{shortCode}/count")
    public ResponseEntity<Long> getClickCount(@PathVariable String shortCode) {

        UrlEntity urlEntity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        return ResponseEntity.ok(clickRepository.countByUrlEntity(urlEntity));
    }
}