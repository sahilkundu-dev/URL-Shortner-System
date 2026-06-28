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

    private static final int ALIAS_MIN_LENGTH = 3;
    private static final int ALIAS_MAX_LENGTH = 30;
    private static final java.util.regex.Pattern ALIAS_PATTERN =
            java.util.regex.Pattern.compile("^[a-z0-9-]+$");

    // Reserved words that would conflict with existing routes
    private static final java.util.Set<String> RESERVED_ALIASES = java.util.Set.of(
            "api", "admin", "actuator", "swagger", "health",
            "metrics", "info", "static", "login", "logout",
            "register", "user", "users", "url", "urls",
            "shorten", "analytics", "v1", "v2", "v3"
    );

    /**
     * Validates a custom alias provided by the user.
     * Rules:
     * - 3 to 30 characters
     * - Only lowercase letters, digits, and hyphens
     * - Cannot start or end with a hyphen
     * - Cannot be a reserved system word
     *
     * Valid:   "my-link", "launch2026", "summer-sale"
     * Invalid: "ab", "-start", "end-", "api", "my alias", "MY_ALIAS"
     */
    public static String validateAndNormalizeAlias(String alias) {

        if (alias == null || alias.isBlank()) {
            throw new UrlValidationException("Custom alias must not be empty");
        }

        // Normalize to lowercase — treat "Launch" and "launch" as same
        String normalized = alias.toLowerCase().trim();

        // Rule 1: Length
        if (normalized.length() < ALIAS_MIN_LENGTH) {
            throw new UrlValidationException(
                    "Custom alias must be at least " + ALIAS_MIN_LENGTH +
                            " characters. Received: " + normalized.length()
            );
        }

        if (normalized.length() > ALIAS_MAX_LENGTH) {
            throw new UrlValidationException(
                    "Custom alias must not exceed " + ALIAS_MAX_LENGTH +
                            " characters. Received: " + normalized.length()
            );
        }

        // Rule 2: Only a-z, 0-9, hyphens
        if (!ALIAS_PATTERN.matcher(normalized).matches()) {
            throw new UrlValidationException(
                    "Custom alias may only contain lowercase letters (a-z), " +
                            "digits (0-9), and hyphens (-). No spaces or special characters."
            );
        }

        // Rule 3: Cannot start or end with hyphen
        if (normalized.startsWith("-") || normalized.endsWith("-")) {
            throw new UrlValidationException(
                    "Custom alias cannot start or end with a hyphen"
            );
        }

        // Rule 4: Reserved words
        if (RESERVED_ALIASES.contains(normalized)) {
            throw new UrlValidationException(
                    "'" + normalized + "' is a reserved word and cannot be used as a custom alias"
            );
        }

        return normalized;
    }

    /**
     * Validates that a path variable is safe to use as a DB lookup key.
     * Accepts either:
     * - A 6-char Base62 code: "000001", "aB3xYz"
     * - A custom alias: "my-github", "launch2026" (3-30 chars, a-z0-9)
     *
     * Rejects: null, blank, path traversal (../), XSS (<script>),
     * special chars beyond hyphen, anything clearly malicious
     */
    public static void validateShortCodeOrAlias(String code) {

        // Rule 1: Null or blank
        if(code == null || code.isBlank()) {
            throw new UrlValidationException("Short code must not be empty");
        }

        // Rule 2: Length - Base62 is 6 chars, aliases are 3-30 chars
        // Anything outside 3-30 is invalid for both formats
        if(code.length() < 3 || code.length() > 30) {
            throw new UrlValidationException(
                    "Invalid short code or alias length. Must be between 3 and 30 characters."
            );
        }

        // Rule 3: Only alphanumeric + hyphen allowed
        // This blocks path traversal (../), XSS (<>), SQL fragments, etc.
        if(!java.util.regex.Pattern.compile("^[a-zA-Z0-9-]+$").matcher(code).matches()) {
            throw new UrlValidationException(
                    "Short code contains invalid characters. Only letters, digits and hyphens are allowed."
            );
        }
    }
}