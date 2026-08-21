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
public class ProxyInvocationFixedTest {

    @Autowired
    private AccountBalanceService accountBalanceService;

    @Autowired
    private TransactionAuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        auditAspect.resetCount();
    }

    @Test
    void testCollaboratorBeanTriggersAspectSuccessfully() {
        accountBalanceService.processDebitWithCollaborator("ACC-200", new BigDecimal("75.00"));

        // Collaborator bean goes through the CGLIB proxy -> aspect intercepted!
        assertThat(auditAspect.getInterceptionCount()).isEqualTo(1);
    }

    @Test
    void testSelfInjectedProxyTriggersAspectSuccessfully() {
        accountBalanceService.processDebitWithSelfProxy("ACC-300", new BigDecimal("120.00"));

        // Self-injected proxy goes through the CGLIB proxy -> aspect intercepted!
        assertThat(auditAspect.getInterceptionCount()).isEqualTo(1);
    }
}
