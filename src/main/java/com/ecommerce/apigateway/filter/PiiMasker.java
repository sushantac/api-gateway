package com.ecommerce.apigateway.filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PiiMasker {

    private static final Pattern JSON_FIELD = Pattern.compile(
            "(\"(?:password|passwd|pwd|token|accessToken|refreshToken|secret|apiKey|authorization)\"\\s*:\\s*)(\"[^\"]*\")");
    private static final Pattern ESCAPED_JSON_FIELD = Pattern.compile(
            "(\\\\\"(?:password|passwd|pwd|token|accessToken|refreshToken|secret|apiKey|authorization)\\\\\"\\s*:\\s*)(\\\\\"[^\\\\\"]*\\\\\")");
    private static final Pattern QUERY_PARAM = Pattern.compile(
            "([?&](?:password|passwd|pwd|token|accessToken|refreshToken|secret|apiKey)=)[^&]*",
            Pattern.CASE_INSENSITIVE);

    private PiiMasker() {
    }

    static String maskJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        return mask(ESCAPED_JSON_FIELD, mask(JSON_FIELD, json));
    }

    private static String mask(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder masked = new StringBuilder(input.length());
        while (matcher.find()) {
            String replacement = pattern == ESCAPED_JSON_FIELD
                    ? matcher.group(1) + "\\\"***\\\""
                    : matcher.group(1) + "\"***\"";
            matcher.appendReplacement(masked, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    static String maskQuery(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        Matcher matcher = QUERY_PARAM.matcher(query);
        StringBuilder masked = new StringBuilder(query.length());
        while (matcher.find()) {
            matcher.appendReplacement(masked, Matcher.quoteReplacement(matcher.group(1) + "***"));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }
}