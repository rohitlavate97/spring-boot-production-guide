package com.finflow.chapter340.listener;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Production Event Tracker for Resilience4j registries.
 * Captures state transitions, error rates, and rejected calls for alerting.
 */
@Component
public class CircuitBreakerEventTracker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerEventTracker.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    private final List<String> eventLogs = new CopyOnWriteArrayList<>();

    public CircuitBreakerEventTracker(CircuitBreakerRegistry circuitBreakerRegistry,
                                      RetryRegistry retryRegistry,
                                      RateLimiterRegistry rateLimiterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @PostConstruct
    public void registerEventListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::bindCircuitBreakerEvents);
        circuitBreakerRegistry.getEventPublisher().onEntryAdded(event -> bindCircuitBreakerEvents(event.getAddedEntry()));

        retryRegistry.getAllRetries().forEach(retry -> {
            retry.getEventPublisher()
                    .onRetry(e -> recordEvent("[RETRY-ATTEMPT] Instance: " + retry.getName() + " | Attempt: " + e.getNumberOfRetryAttempts()))
                    .onError(e -> recordEvent("[RETRY-EXHAUSTED] Instance: " + retry.getName() + " | Last error: " + e.getLastThrowable().getMessage()));
        });

        rateLimiterRegistry.getAllRateLimiters().forEach(rl -> {
            rl.getEventPublisher()
                    .onFailure(e -> recordEvent("[RATE-LIMIT-REJECTED] Instance: " + rl.getName() + " | Limit exceeded"));
        });
    }

    private void bindCircuitBreakerEvents(CircuitBreaker cb) {
        cb.getEventPublisher()
                .onStateTransition(event -> {
                    String logMsg = String.format("[CIRCUIT-BREAKER-TRANSITION] %s transitioned from %s to %s",
                            event.getCircuitBreakerName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                    log.warn(logMsg);
                    recordEvent(logMsg);
                })
                .onError(event -> {
                    String logMsg = String.format("[CIRCUIT-BREAKER-ERROR] %s recorded error: %s (Duration: %dms)",
                            event.getCircuitBreakerName(),
                            event.getThrowable().getClass().getSimpleName(),
                            event.getElapsedDuration().toMillis());
                    log.error(logMsg);
                    recordEvent(logMsg);
                })
                .onSlowCallRateExceeded(event -> {
                    String logMsg = String.format("[CIRCUIT-BREAKER-SLOW-CALL] %s slow call rate exceeded: %.2f%%",
                            event.getCircuitBreakerName(),
                            event.getSlowCallRate());
                    log.warn(logMsg);
                    recordEvent(logMsg);
                })
                .onCallNotPermitted(event -> {
                    String logMsg = String.format("[CIRCUIT-BREAKER-FAST-FAIL] %s call not permitted (Circuit is OPEN)",
                            event.getCircuitBreakerName());
                    log.warn(logMsg);
                    recordEvent(logMsg);
                });
    }

    private void recordEvent(String event) {
        eventLogs.add(event);
        if (eventLogs.size() > 500) {
            eventLogs.remove(0);
        }
    }

    public List<String> getEventLogs() {
        return List.copyOf(eventLogs);
    }

    public void clearEventLogs() {
        eventLogs.clear();
    }
}
