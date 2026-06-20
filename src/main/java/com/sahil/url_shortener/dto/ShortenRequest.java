package com.sahil.url_shortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request body for shortening a URL")
public class ShortenRequest {

    @Schema(
            description = "The long URL to shorten. Must be a valid http or https URL.",
            example = "https://www.example.com/some/very/long/url/path/that/needs/shortening"
    )
    private String longUrl;

    @Schema(
            description = "Optional expiry in hours. If set, the short URL returns 410 Gone after this many hours. If omitted, the URL never expires.",
            example = "24",
            nullable = true
    )
    private Integer ttlHours;
}