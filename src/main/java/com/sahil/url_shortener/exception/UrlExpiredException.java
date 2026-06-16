package com.sahil.url_shortener.exception;

public class UrlExpiredException extends RuntimeException{

    public UrlExpiredException(String shortCode) {
        super("Short URL has expired: " + shortCode);
    }
}
