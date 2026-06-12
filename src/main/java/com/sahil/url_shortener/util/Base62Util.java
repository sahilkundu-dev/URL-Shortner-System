package com.sahil.url_shortener.util;

public class Base62Util {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;
    private static final int MIN_LENGTH = 6;

    public static String encode(long id) {
        StringBuilder sb = new StringBuilder();

        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % BASE)));
            id /= BASE;
        }

        while (sb.length() < MIN_LENGTH) {
            sb.append('0');
        }

        return sb.reverse().toString();
    }
}