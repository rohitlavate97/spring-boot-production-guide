package com.finflow.troubleshooting.module11;

import com.finflow.troubleshooting.module11.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module11Application.class)
@AutoConfigureMockMvc
public class RoleBasedAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    void testUserRoleForbiddenFromAccessingAdminEndpoint() throws Exception {
        String userToken = tokenProvider.generateToken("regular_user", List.of("ROLE_USER"));

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminRoleAllowedToAccessAdminEndpoint() throws Exception {
        String adminToken = tokenProvider.generateToken("super_admin", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.systemHealth").value("OPTIMAL"));
    }
}
