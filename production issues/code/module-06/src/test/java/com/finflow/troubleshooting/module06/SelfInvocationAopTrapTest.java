package com.finflow.troubleshooting.module06;

import com.finflow.troubleshooting.module06.aspect.TransactionAuditAspect;
import com.finflow.troubleshooting.module06.service.AccountBalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module06Application.class)
public class SelfInvocationAopTrapTest {

    @Autowired
    private AccountBalanceService accountBalanceService;

    @Autowired
    private TransactionAuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        auditAspect.resetCount();
    }

    @Test
    void testSelfInvocationBypassesAspectInterception() {
        // Calling method with internal self-invocation
        accountBalanceService.processDebitBuggy("ACC-100", new BigDecimal("50.00"));

        // PROOF: The aspect was NEVER invoked because 'this' was called instead of the proxy!
        assertThat(auditAspect.getInterceptionCount()).isEqualTo(0);
    }
}
