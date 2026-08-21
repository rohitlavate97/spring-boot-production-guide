package com.finflow.troubleshooting.module11.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AsyncSecurityContextService {

    // Demonstrates extracting authenticated username inside a custom thread worker
    public CompletableFuture<String> getAuthenticatedUserAsync() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentPrincipal = (auth != null) ? auth.getName() : "ANONYMOUS";

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<String> future = new CompletableFuture<>();

        executor.submit(() -> {
            // Context would normally be null in new thread unless explicitly propagated
            future.complete(currentPrincipal);
        });

        executor.shutdown();
        return future;
    }
}
