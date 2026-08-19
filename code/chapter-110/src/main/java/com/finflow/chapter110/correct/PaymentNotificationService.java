package com.finflow.chapter110.correct;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PaymentNotificationService {

    @Async
    public void executeAsyncNotification(String paymentId) {
        // Safe to call from another bean, the proxy will route this 
        // to a task executor thread pool correctly.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
