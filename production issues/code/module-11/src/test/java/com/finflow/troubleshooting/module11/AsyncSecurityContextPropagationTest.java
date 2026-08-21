package com.finflow.troubleshooting.module11;

import com.finflow.troubleshooting.module11.service.AsyncSecurityContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module11Application.class)
public class AsyncSecurityContextPropagationTest {

    @Autowired
    private AsyncSecurityContextService asyncService;

    @Test
    void testSecurityContextHolderExtractsPrincipalAccurately() throws ExecutionException, InterruptedException, TimeoutException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("async_agent", null, List.of())
        );

        CompletableFuture<String> future = asyncService.getAuthenticatedUserAsync();
        String result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("async_agent");
        SecurityContextHolder.clearContext();
    }
}
