package com.sahil.url_shortener.exception;

public class RateLimitException extends RuntimeException{

    public RateLimitException(String ipAddress) {
        super("Too many requests from IP: " + ipAddress +
                ". Limit: 10 requests per minute.");
    }
}
