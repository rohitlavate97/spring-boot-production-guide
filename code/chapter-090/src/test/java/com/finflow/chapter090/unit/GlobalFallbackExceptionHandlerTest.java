package com.finflow.chapter090.unit;

import com.finflow.chapter090.correct.PaymentProcessingController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PaymentProcessingController.class)
class GlobalFallbackExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testUnexpectedExceptionReturnsOpaque500ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/payments/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.type").value("https://api.finflow.com/errors/internal_server_error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.traceId").exists())
                // Ensure the internal message "Database connection dropped unexpectedly" is NOT leaked
                .andExpect(jsonPath("$.detail", not("Database connection dropped unexpectedly")));
    }
}
