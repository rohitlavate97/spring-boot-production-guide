package com.finflow.chapter080.correct.validation;

import com.finflow.chapter080.domain.PaymentIntentRequest;
import com.finflow.chapter080.domain.PaymentMethodType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PaymentMethodDetailsValidator implements ConstraintValidator<ValidPaymentMethodDetails, PaymentIntentRequest> {

    @Override
    public boolean isValid(PaymentIntentRequest request, ConstraintValidatorContext context) {
        if (request == null || request.paymentMethodType() == null) {
            return true; 
        }

        boolean isValid = true;
        
        if (request.paymentMethodType() == PaymentMethodType.CARD) {
            if (request.cardNumber() == null || request.cardNumber().isBlank()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Card number is required when payment method is CARD")
                       .addPropertyNode("cardNumber")
                       .addConstraintViolation();
                isValid = false;
            }
        } else if (request.paymentMethodType() == PaymentMethodType.BANK_TRANSFER) {
            if (request.bankAccountNumber() == null || request.bankAccountNumber().isBlank()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Bank account number is required when payment method is BANK_TRANSFER")
                       .addPropertyNode("bankAccountNumber")
                       .addConstraintViolation();
                isValid = false;
            }
        }

        return isValid;
    }
}
