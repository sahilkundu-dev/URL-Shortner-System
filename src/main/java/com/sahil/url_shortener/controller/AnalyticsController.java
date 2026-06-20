package com.sahil.url_shortener.controller;

import com.sahil.url_shortener.dto.ClickResponse;
import com.sahil.url_shortener.entity.UrlEntity;
import com.sahil.url_shortener.exception.UrlNotFoundException;
import com.sahil.url_shortener.repository.ClickRepository;
import com.sahil.url_shortener.repository.UrlRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Click tracking and analytics for shortened URLs")
public class AnalyticsController {

    private final UrlRepository urlRepository;
    private final ClickRepository clickRepository;

    @Operation(
            summary = "Get full analytics for a short URL",
            description = """
            Returns total click count and the 10 most recent clicks with full metadata.
            
            Each click entry contains:
            - `clickedAt` — exact timestamp of the redirect
            - `ipAddress` — client IP (IPv6 loopback `0:0:0:0:0:0:0:1` = localhost)
            - `userAgent` — browser or client identifier
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully",
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "shortCode": "000001",
                      "longUrl": "https://www.example.com/some/very/long/url",
                      "totalClicks": 3,
                      "recentClicks": [
                        {
                          "clickedAt": "2026-06-19T13:23:34.284",
                          "ipAddress": "0:0:0:0:0:0:0:1",
                          "userAgent": "curl/8.7.1"
                        }
                      ]
                    }
                    """))),
            @ApiResponse(responseCode = "404", description = "Short code not found",
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "error": "No URL found for short code: zzzzz",
                      "status": 404,
                      "timestamp": "2026-06-19T13:00:00.000000"
                    }
                    """)))
    })
    @GetMapping("/{shortCode}")
    public ResponseEntity<ClickResponse> getAnalytics(
            @Parameter(description = "The 6-character Base62 short code", example = "000001")
            @PathVariable String shortCode) {

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

        return ResponseEntity.ok(
                new ClickResponse(shortCode, urlEntity.getLongUrl(),
                        totalClicks, recentClicks)
        );
    }

    @Operation(
            summary = "Get click count for a short URL",
            description = "Lightweight endpoint — returns just the total click count as a plain integer. Useful for dashboards that only need a number."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Click count returned",
                    content = @Content(examples = @ExampleObject(value = "3"))),
            @ApiResponse(responseCode = "404", description = "Short code not found")
    })
    @GetMapping("/{shortCode}/count")
    public ResponseEntity<Long> getClickCount(
            @Parameter(description = "The 6-character Base62 short code", example = "000001")
            @PathVariable String shortCode) {

        UrlEntity urlEntity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        return ResponseEntity.ok(clickRepository.countByUrlEntity(urlEntity));
    }
}