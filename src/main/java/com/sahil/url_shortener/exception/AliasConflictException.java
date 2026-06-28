package com.sahil.url_shortener.exception;

public class AliasConflictException extends RuntimeException {

    public AliasConflictException(String alias) {
        super("Custom alias '" + alias + "' is already taken. Please choose a different alias.");
    }
}