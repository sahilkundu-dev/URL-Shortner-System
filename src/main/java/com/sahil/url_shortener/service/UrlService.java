package com.sahil.url_shortener.service;

public interface UrlService {
    String shortenUrl(String longUrl, Integer ttlHours, String customAlias);
    String getLongUrl(String shortCode, String ipAddress, String userAgent);
}
