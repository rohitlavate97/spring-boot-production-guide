package com.finflow.chapter070.incorrect;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class StreamConsumingFilterIncorrect implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest req) {
            // Incorrect: directly reading the stream before the controller.
            // This consumes the stream, making it unavailable to Jackson for @RequestBody parsing.
            String body = new String(req.getInputStream().readAllBytes());
            System.out.println("Audit log: Request body is: " + body);
        }
        chain.doFilter(request, response);
    }
}
