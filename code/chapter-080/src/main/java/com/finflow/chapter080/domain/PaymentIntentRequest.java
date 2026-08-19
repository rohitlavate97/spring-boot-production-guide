package com.finflow.chapter080.domain;

import com.finflow.chapter080.correct.validation.ValidCurrency;
import com.finflow.chapter080.correct.validation.ValidPaymentMethodDetails;
import com.finflow.chapter080.correct.validation.ValidCardNumber;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@ValidPaymentMethodDetails
public record PaymentIntentRequest(
    @NotBlank(message = "Intent ID is required")
    String intentId,
    
    @Min(value = 1, message = "Amount must be at least 1 cent")
    long amountCents,
    
    @ValidCurrency
    @NotBlank(message = "Currency is required")
    String currency,
    
    @NotNull(message = "Payment method type is required")
    PaymentMethodType paymentMethodType,
    
    @ValidCardNumber(groups = CardPaymentGroup.class)
    String cardNumber,
    
    @NotBlank(groups = BankTransferGroup.class, message = "Bank account number is required for bank transfers")
    String bankAccountNumber,
    
    @Valid
    @NotNull(message = "Split allocations list must not be null")
    List<SplitAllocation> splitAllocations
) {
}
