package com.finflow.troubleshooting.module04;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module04Application.class)
@AutoConfigureMockMvc
public class ContentNegotiationAndStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testUnsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/v1/payments/process")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain-text-not-supported"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Unsupported Media Type (415)"));
    }

    @Test
    void testMethodNotAllowedReturns405() throws Exception {
        mockMvc.perform(put("/api/v1/payments/process"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Method Not Allowed (405)"));
    }

    @Test
    void testUnmappedEndpointReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/payments/non-existent-subpath/xyz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found (404)"));
    }
}
