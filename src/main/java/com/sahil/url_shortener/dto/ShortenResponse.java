package com.sahil.url_shortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Response body after successfully shortening a URL")
public class ShortenResponse {

    @Schema(description = "The generated short URL", example = "http://localhost:8080/000001")
    private String shortUrl;

    @Schema(description = "The original long URL that was shortened", example = "https://www.example.com/some/very/long/url")
    private String longUrl;
}