package com.finflow.chapter110.unit;

import com.finflow.chapter110.correct.LedgerAuditService;
import com.finflow.chapter110.incorrect.FinalMethodServiceIncorrect;
import com.finflow.chapter110.incorrect.PaymentServiceSelfInvocationIncorrect;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AopProxyMechanicsTest {

    @Autowired
    private LedgerAuditService ledgerAuditService;

    @Autowired
    private FinalMethodServiceIncorrect finalMethodService;

    @Autowired
    private PaymentServiceSelfInvocationIncorrect selfInvocationService;

    @Test
    void testIsAopProxy() {
        // Assert that the context injected proxies instead of raw instances
        assertThat(AopUtils.isAopProxy(ledgerAuditService)).isTrue();
        assertThat(AopUtils.isCglibProxy(ledgerAuditService)).isTrue();
        
        assertThat(AopUtils.isAopProxy(finalMethodService)).isTrue();
        assertThat(AopUtils.isCglibProxy(finalMethodService)).isTrue();
        
        assertThat(AopUtils.isAopProxy(selfInvocationService)).isTrue();
        assertThat(AopUtils.isCglibProxy(selfInvocationService)).isTrue();
    }
    
    @Test
    void testTargetClassIdentity() {
        Class<?> targetClass = AopUtils.getTargetClass(ledgerAuditService);
        assertThat(targetClass).isEqualTo(LedgerAuditService.class);
    }
}
