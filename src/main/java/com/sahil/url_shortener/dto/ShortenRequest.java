package com.sahil.url_shortener.dto;

import lombok.Data;

@Data
public class ShortenRequest {
    private String longUrl;

    // Optional - if null, URL never expires
    // Value in hours - e.g. 24 means expires in 24 hours
    private Integer ttlHours;
}
