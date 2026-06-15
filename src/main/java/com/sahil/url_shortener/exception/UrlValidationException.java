package com.sahil.url_shortener.exception;

public class UrlValidationException extends RuntimeException{

    public UrlValidationException(String message) {
        super(message);
    }
}
