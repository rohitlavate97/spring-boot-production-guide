package com.finflow.chapter090.unit;

import com.finflow.chapter090.exception.ErrorCode;
import com.finflow.chapter090.exception.GatewayTimeoutException;
import com.finflow.chapter090.exception.IdempotencyConflictException;
import com.finflow.chapter090.exception.PaymentDeclinedException;
import com.finflow.chapter090.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class DomainExceptionHierarchyTest {

    @Test
    void testPaymentDeclinedExceptionProperties() {
        PaymentDeclinedException ex = new PaymentDeclinedException("NSF");
        assertEquals("NSF", ex.getDeclineReason());
        assertEquals(ErrorCode.PAYMENT_DECLINED, ex.getErrorCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getHttpStatus());
        assertFalse(ex.isRetryable());
    }

    @Test
    void testIdempotencyConflictExceptionProperties() {
        IdempotencyConflictException ex = new IdempotencyConflictException("key123");
        assertEquals("key123", ex.getIdempotencyKey());
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertFalse(ex.isRetryable());
    }

    @Test
    void testGatewayTimeoutExceptionProperties() {
        GatewayTimeoutException ex = new GatewayTimeoutException("Stripe", 30);
        assertEquals("Stripe", ex.getGatewayName());
        assertEquals(30, ex.getRetryAfterSeconds());
        assertEquals(ErrorCode.GATEWAY_TIMEOUT, ex.getErrorCode());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getHttpStatus());
        assertTrue(ex.isRetryable());
    }

    @Test
    void testResourceNotFoundExceptionProperties() {
        ResourceNotFoundException ex = new ResourceNotFoundException("PaymentIntent", "pi_123");
        assertEquals("PaymentIntent", ex.getResourceType());
        assertEquals("pi_123", ex.getResourceId());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        assertFalse(ex.isRetryable());
    }
}
