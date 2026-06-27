package com.sahil.url_shortener.util;

import com.sahil.url_shortener.exception.UrlValidationException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class UrlValidatorUtil {

    private static final int MAX_URL_LENGTH = 2048;

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private static final Set<String> BLOCKED_SCHEMES = Set.of(
            "javascript", "data", "vbscript", "file", "ftp", "ftps"
    );

    public static void validate(String url) {

        // Rule 1: Null or blank
        if (url == null || url.isBlank()) {
            throw new UrlValidationException("URL must not be empty");
        }

        // Rule 2: Length limit — matches DB column constraint
        if (url.length() > MAX_URL_LENGTH) {
            throw new UrlValidationException(
                    "URL exceeds maximum length of " + MAX_URL_LENGTH + " characters"
            );
        }

        // Rule 3: Parse with Java's RFC 3986-compliant URI parser
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new UrlValidationException(
                    "Invalid URL format. Must be a valid http or https URL"
            );
        }

        // Rule 4: Must have a scheme (rejects "www.google.com", "google", etc.)
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new UrlValidationException(
                    "Invalid URL format. Must be a valid http or https URL"
            );
        }

        // Rule 5: Block dangerous schemes explicitly
        if (BLOCKED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new UrlValidationException(
                    "URL scheme not allowed. Only http and https are supported"
            );
        }

        // Rule 6: Only allow http and https
        if (!ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new UrlValidationException(
                    "URL scheme not allowed. Only http and https are supported"
            );
        }

        // Rule 7: Must have a host (rejects "http://", "https://")
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UrlValidationException(
                    "Invalid URL format. Must be a valid http or https URL"
            );
        }
    }

    private static final int SHORT_CODE_LENGTH = 6;
    private static final java.util.regex.Pattern SHORT_CODE_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9]{6}$");

    /**
     * Validates that a shortCode is exactly 6 alphanumeric characters.
     * Rejects path traversal, XSS, special characters, wrong length.
     *
     * Valid:   "000001", "aB3xYz", "AAAAAA"
     * Invalid: "abc", "abc!@#", "../../x", "toolongcode", "", null
     */
    public static void validateShortCode(String shortCode) {

        // Rule 1: Null or blank
        if (shortCode == null || shortCode.isBlank()) {
            throw new UrlValidationException("Short code must not be empty");
        }

        // Rule 2: Exact length — our Base62 codes are always 6 chars
        if (shortCode.length() != SHORT_CODE_LENGTH) {
            throw new UrlValidationException(
                    "Short code must be exactly " + SHORT_CODE_LENGTH +
                            " characters. Received: " + shortCode.length()
            );
        }

        // Rule 3: Only alphanumeric — blocks path traversal, XSS, special chars
        if (!SHORT_CODE_PATTERN.matcher(shortCode).matches()) {
            throw new UrlValidationException(
                    "Short code must contain only alphanumeric characters (a-z, A-Z, 0-9)"
            );
        }
    }
}