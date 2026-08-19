package com.finflow.chapter080.unit;

import com.finflow.chapter080.domain.PaymentIntentRequest;
import com.finflow.chapter080.domain.PaymentMethodType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossFieldValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void cardPaymentWithCardNumberShouldPass() {
        PaymentIntentRequest request = new PaymentIntentRequest(
                "pi_123", 1000, "USD", PaymentMethodType.CARD, "4242424242424242", null, Collections.emptyList()
        );

        // We only assert the class level validation by checking if "cardNumber is required" message is present
        Set<ConstraintViolation<PaymentIntentRequest>> violations = validator.validate(request);
        boolean hasCardNumberRequiredError = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Card number is required"));
        
        assertFalse(hasCardNumberRequiredError);
    }

    @Test
    void cardPaymentWithoutCardNumberShouldFail() {
        PaymentIntentRequest request = new PaymentIntentRequest(
                "pi_123", 1000, "USD", PaymentMethodType.CARD, null, null, Collections.emptyList()
        );

        Set<ConstraintViolation<PaymentIntentRequest>> violations = validator.validate(request);
        boolean hasCardNumberRequiredError = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Card number is required"));

        assertTrue(hasCardNumberRequiredError);
    }

    @Test
    void bankTransferWithBankAccountShouldPass() {
        PaymentIntentRequest request = new PaymentIntentRequest(
                "pi_123", 1000, "USD", PaymentMethodType.BANK_TRANSFER, null, "BANK123", Collections.emptyList()
        );

        Set<ConstraintViolation<PaymentIntentRequest>> violations = validator.validate(request);
        boolean hasBankRequiredError = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Bank account number is required"));
        
        assertFalse(hasBankRequiredError);
    }

    @Test
    void bankTransferWithoutBankAccountShouldFail() {
        PaymentIntentRequest request = new PaymentIntentRequest(
                "pi_123", 1000, "USD", PaymentMethodType.BANK_TRANSFER, null, null, Collections.emptyList()
        );

        Set<ConstraintViolation<PaymentIntentRequest>> violations = validator.validate(request);
        boolean hasBankRequiredError = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Bank account number is required"));
        
        assertTrue(hasBankRequiredError);
    }
}
