package com.sahil.url_shortener.controller;

import com.sahil.url_shortener.dto.ShortenRequest;
import com.sahil.url_shortener.dto.ShortenResponse;
import com.sahil.url_shortener.service.RateLimiterService;
import com.sahil.url_shortener.service.UrlService;
import com.sahil.url_shortener.util.UrlValidatorUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@Tag(name = "URL Shortener", description = "Core URL shortening and redirect operations")
public class UrlController {

    private final UrlService urlService;
    private final RateLimiterService rateLimiterService;

    @Operation(
            summary = "Shorten a URL",
            description = """
            Validates the input URL and returns a 6-character Base62 short code.
            
            - If the same long URL is submitted again, returns the existing short code (no duplicates)
            - Optional `ttlHours` sets an expiry — after which the short URL returns 410 Gone
            - Rate limited to 10 requests per minute per IP address
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL shortened successfully",
                    content = @Content(schema = @Schema(implementation = ShortenResponse.class),
                            examples = @ExampleObject(value = """
                    {
                      "shortUrl": "http://localhost:8080/000001",
                      "longUrl": "https://www.example.com/some/very/long/url"
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Invalid URL — null, blank, bad scheme, or malformed",
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "error": "URL scheme not allowed. Only http and https are supported",
                      "status": 400,
                      "timestamp": "2026-06-19T13:00:00.000000"
                    }
                    """))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded — 10 requests per minute per IP",
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "error": "Too many requests from IP: 192.168.1.1. Limit: 10 requests per minute.",
                      "status": 429,
                      "timestamp": "2026-06-19T13:00:00.000000"
                    }
                    """)))
    })
    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shorten(
            @RequestBody ShortenRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        rateLimiterService.checkRateLimit(clientIp);

        String shortUrl = urlService.shortenUrl(
                request.getLongUrl(),
                request.getTtlHours(),
                request.getCustomAlias()
        );
        return ResponseEntity.ok(
                new ShortenResponse(shortUrl, request.getLongUrl())
        );
    }

    @Operation(
            summary = "Redirect to original URL",
            description = """
            Looks up the short code, checks expiry, records a click, and returns HTTP 302 redirect.
            
            - Returns 302 with `Location` header pointing to the original URL
            - Returns 410 Gone if the URL has expired
            - Returns 404 if the short code does not exist
            - Every successful redirect records: timestamp, IP address, user-agent
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to original URL"),
            @ApiResponse(responseCode = "404", description = "Short code not found",
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "error": "No URL found for short code: zzzzz",
                      "status": 404,
                      "timestamp": "2026-06-19T13:00:00.000000"
                    }
                    """))),
            @ApiResponse(responseCode = "410", description = "Short URL has expired",
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "error": "Short URL has expired: 000001",
                      "status": 410,
                      "timestamp": "2026-06-19T13:00:00.000000"
                    }
                    """)))
    })
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @Parameter(description = "The 6-character Base62 short code", example = "000001")
            @PathVariable String shortCode,
            HttpServletRequest request) {

        // Validate format before touching Redis or MySQL
        // Accepts EITHER: 6-char Base62 code OR Custom alias format
        UrlValidatorUtil.validateShortCodeOrAlias(shortCode);

        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        String longUrl = urlService.getLongUrl(shortCode, ipAddress, userAgent);
        return ResponseEntity.status(302)
                .location(URI.create(longUrl))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}