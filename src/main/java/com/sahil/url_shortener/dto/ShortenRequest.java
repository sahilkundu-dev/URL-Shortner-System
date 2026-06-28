package com.sahil.url_shortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request body for shortening a URL")
public class ShortenRequest {

    @Schema(
            description = "The long URL to shorten. Must be a valid http or https URL.",
            example = "https://www.example.com/some/very/long/url/path"
    )
    private String longUrl;

    @Schema(
            description = "Optional expiry in hours. If set, the short URL returns 410 Gone after this many hours. If omitted, the URL never expires.",
            example = "24",
            nullable = true
    )
    private Integer ttlHours;

    @Schema(
            description = "Optional custom alias for the short URL. 3-30 characters, only a-z, 0-9, and hyphens allowed. If omitted, a Base62 code is generated automatically",
            example = "my-project",
            nullable = true
    )
    private String customAlias;
}