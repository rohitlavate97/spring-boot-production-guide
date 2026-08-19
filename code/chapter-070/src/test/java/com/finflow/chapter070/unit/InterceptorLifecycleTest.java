package com.finflow.chapter070.unit;

import com.finflow.chapter070.correct.MerchantContextHolder;
import com.finflow.chapter070.correct.MerchantSecurityInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterceptorLifecycleTest {

    private final MerchantSecurityInterceptor interceptor = new MerchantSecurityInterceptor();

    @AfterEach
    void tearDown() {
        MerchantContextHolder.clear();
        MDC.clear();
    }

    @Test
    void preHandle_shouldSetContextAndMDC() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        
        when(request.getHeader("X-Merchant-ID")).thenReturn("m-123");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        assertNotNull(MerchantContextHolder.get());
        assertEquals("m-123", MerchantContextHolder.get().merchantId());
        assertEquals("m-123", MDC.get("merchantId"));
    }

    @Test
    void afterCompletion_shouldCleanUpContextAndMDCEvenWithException() {
        MerchantContextHolder.set(new com.finflow.chapter070.domain.MerchantContext("m", "T", "k", "c"));
        MDC.put("merchantId", "m");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Exception ex = new RuntimeException("Test Exception");

        interceptor.afterCompletion(request, response, new Object(), ex);

        assertNull(MerchantContextHolder.get());
        assertNull(MDC.get("merchantId"));
    }
}
