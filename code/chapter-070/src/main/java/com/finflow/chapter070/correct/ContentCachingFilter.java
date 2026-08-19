package com.finflow.chapter070.correct;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@Component
public class ContentCachingFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ContentCachingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest req) {
            ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(req);
            // Pass the wrapper down the chain
            chain.doFilter(wrapper, response);
            
            // After the chain completes (and the body was consumed by Jackson),
            // the wrapper will have cached the content.
            byte[] cachedContent = wrapper.getContentAsByteArray();
            if (cachedContent.length > 0) {
                log.debug("Audit: Cached request body length: {}", cachedContent.length);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
