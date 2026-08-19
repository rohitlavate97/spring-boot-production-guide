package com.finflow.chapter070.unit;

import com.finflow.chapter070.correct.ContentCachingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ContentCachingFilterTest {

    @Test
    void doFilter_shouldWrapRequestAndCacheContent() throws IOException, ServletException {
        ContentCachingFilter filter = new ContentCachingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"test\":\"data\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertTrue(req instanceof ContentCachingRequestWrapper);
            // Simulate reading the stream
            req.getInputStream().readAllBytes();
        };

        filter.doFilter(request, response, chain);
    }
}
