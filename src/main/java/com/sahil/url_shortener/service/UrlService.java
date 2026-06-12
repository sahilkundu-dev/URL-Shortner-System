package com.sahil.url_shortener.service;

public interface UrlService {
    String shortenUrl(String longUrl);
    String getLongUrl(String shortCode);
}
