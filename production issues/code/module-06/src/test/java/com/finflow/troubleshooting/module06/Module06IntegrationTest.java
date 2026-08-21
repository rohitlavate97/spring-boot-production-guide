package com.finflow.troubleshooting.module06;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module06Application.class)
@AutoConfigureMockMvc
public class Module06IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testBuggyEndpointDemonstratesAspectBypass() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/debit/buggy?accountId=ACC-BUG-1&amount=50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aspectIntercepted").value(false))
                .andExpect(jsonPath("$.pattern").value("BUGGY_SELF_INVOCATION"));
    }

    @Test
    void testFixedCollaboratorEndpointDemonstratesAspectInterception() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/debit/fixed-collaborator?accountId=ACC-FIX-1&amount=100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aspectIntercepted").value(true))
                .andExpect(jsonPath("$.pattern").value("FIXED_COLLABORATOR_BEAN"));
    }

    @Test
    void testFixedSelfProxyEndpointDemonstratesAspectInterception() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/debit/fixed-self-proxy?accountId=ACC-FIX-2&amount=150.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aspectIntercepted").value(true))
                .andExpect(jsonPath("$.pattern").value("FIXED_SELF_INJECTED_PROXY"));
    }
}
