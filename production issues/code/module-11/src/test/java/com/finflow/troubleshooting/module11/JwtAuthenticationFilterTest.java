package com.finflow.troubleshooting.module11;

import com.finflow.troubleshooting.module11.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Module11Application.class)
@AutoConfigureMockMvc
public class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    void testValidJwtTokenAllowsAccessToSecureEndpoint() throws Exception {
        String token = tokenProvider.generateToken("alice_trader", List.of("ROLE_USER"));

        mockMvc.perform(get("/api/v1/secure/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice_trader"))
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
    }

    @Test
    void testMissingAuthorizationHeaderReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/secure/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized Access"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void testExpiredJwtTokenReturns401ProblemDetail() throws Exception {
        // Generate an expired token (expired 5 seconds ago)
        String expiredToken = tokenProvider.generateTokenWithCustomExpiry("bob_expired", List.of("ROLE_USER"), -5000);

        mockMvc.perform(get("/api/v1/secure/profile")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }
}
