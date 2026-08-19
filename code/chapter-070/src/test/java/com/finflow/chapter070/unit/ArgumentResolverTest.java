package com.finflow.chapter070.unit;

import com.finflow.chapter070.correct.CurrentMerchant;
import com.finflow.chapter070.correct.MerchantContextArgumentResolver;
import com.finflow.chapter070.correct.MerchantContextHolder;
import com.finflow.chapter070.domain.MerchantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArgumentResolverTest {

    private MerchantContextArgumentResolver resolver;
    private NativeWebRequest webRequest;
    private MethodParameter methodParameter;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        resolver = new MerchantContextArgumentResolver();
        webRequest = mock(NativeWebRequest.class);
        
        // mock parameter
        methodParameter = new MethodParameter(
                TestController.class.getMethod("testMethod", MerchantContext.class), 0);
    }

    @AfterEach
    void tearDown() {
        MerchantContextHolder.clear();
    }

    @Test
    void supportsParameter_shouldReturnTrueForCurrentMerchant() {
        assertTrue(resolver.supportsParameter(methodParameter));
    }

    @Test
    void resolveArgument_shouldReturnContextFromHolderIfPresent() throws Exception {
        MerchantContext context = new MerchantContext("m1", "T", "key", "US");
        MerchantContextHolder.set(context);

        Object result = resolver.resolveArgument(methodParameter, null, webRequest, null);

        assertEquals(context, result);
    }

    @Test
    void resolveArgument_shouldCreateContextFromHeaderIfHolderEmpty() throws Exception {
        when(webRequest.getHeader("X-Merchant-ID")).thenReturn("m2");

        Object result = resolver.resolveArgument(methodParameter, null, webRequest, null);

        assertTrue(result instanceof MerchantContext);
        assertEquals("m2", ((MerchantContext) result).merchantId());
    }

    @Test
    void resolveArgument_shouldThrowExceptionIfHeaderMissing() {
        when(webRequest.getHeader("X-Merchant-ID")).thenReturn(null);

        assertThrows(IllegalStateException.class, () ->
                resolver.resolveArgument(methodParameter, null, webRequest, null)
        );
    }

    @SuppressWarnings("unused")
    private static class TestController {
        public void testMethod(@CurrentMerchant MerchantContext context) {}
    }
}
