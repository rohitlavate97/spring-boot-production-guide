package com.finflow.chapter380.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback Composite Converter for PCI-DSS and PII compliance.
 * Automatically masks 16-digit credit card numbers (PANs) and API keys
 * in log streams before writing to stdout / log aggregators.
 */
public class PiiMaskingConverter extends CompositeConverter<ILoggingEvent> {

    // Regex matching standard 16-digit credit card patterns with optional hyphens or spaces
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\b(?<first4>\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(?<last4>\\d{4})\\b");

    // Regex matching API secret keys (e.g. sk_live_..., key-...)
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|secret|token|authorization)[:=]\\s*['\"]?([a-zA-Z0-9_\\-]{16,})['\"]?");

    @Override
    protected String transform(ILoggingEvent event, String in) {
        if (in == null || in.isEmpty()) {
            return in;
        }

        // 1. Mask Credit Card PANs -> 4111-****-****-1111
        Matcher cardMatcher = CARD_PATTERN.matcher(in);
        String masked = cardMatcher.replaceAll("${first4}-****-****-${last4}");

        // 2. Redact API Keys / Tokens
        Matcher keyMatcher = API_KEY_PATTERN.matcher(masked);
        masked = keyMatcher.replaceAll("$1=***REDACTED***");

        return masked;
    }
}
