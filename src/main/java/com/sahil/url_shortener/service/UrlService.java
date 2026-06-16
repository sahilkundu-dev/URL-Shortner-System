package com.sahil.url_shortener.service;

public interface UrlService {
    String shortenUrl(String longUrl, Integer ttlHours);
    String getLongUrl(String shortCode, String ipAddress, String userAgent);
}
