package com.finflow.troubleshooting.module10;

import com.finflow.troubleshooting.module10.entity.LedgerAccountEntity;
import com.finflow.troubleshooting.module10.repository.LedgerAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module10Application.class)
@AutoConfigureMockMvc
public class Module10IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LedgerAccountRepository accountRepository;

    private Long account1Id;
    private Long account2Id;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAllInBatch();
        LedgerAccountEntity a1 = accountRepository.save(new LedgerAccountEntity("ACC-201", "Emma", new BigDecimal("1000.00")));
        LedgerAccountEntity a2 = accountRepository.save(new LedgerAccountEntity("ACC-202", "Frank", new BigDecimal("1000.00")));
        this.account1Id = a1.getId();
        this.account2Id = a2.getId();
    }

    @Test
    void testGetAccountEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/accounts/" + account1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC-201"))
                .andExpect(jsonPath("$.balance").value(1000.00));
    }

    @Test
    void testDeterministicTransferEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/ledger/transfer/deterministic?fromId=" + account1Id + "&toId=" + account2Id + "&amount=150.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void testOptimisticTransferEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/ledger/transfer/optimistic?fromId=" + account1Id + "&toId=" + account2Id + "&amount=50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
