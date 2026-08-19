package com.finflow.chapter050.domain;

public interface PaymentGateway {
    PaymentResult charge(PaymentRequest request);
    RefundResult refund(RefundRequest request);
    String gatewayName();
    boolean isAvailable();
}
