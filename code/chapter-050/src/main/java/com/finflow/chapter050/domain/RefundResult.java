package com.finflow.chapter050.domain;

public record RefundResult(
    String refundId,
    String status,
    String failureReason  
) {}
