package com.finflow.chapter220.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;

    public ApiKeyAuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-KEY");

        if (apiKey != null && !apiKey.isBlank()) {
            try {
                ApiKeyAuthenticationToken unauthenticatedToken = new ApiKeyAuthenticationToken(apiKey);
                Authentication authenticatedToken = authenticationManager.authenticate(unauthenticatedToken);

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authenticatedToken);
                SecurityContextHolder.setContext(context);
            } catch (AuthenticationException ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"" + ex.getMessage() + "\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
