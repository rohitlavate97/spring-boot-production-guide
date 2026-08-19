package com.finflow.chapter080.unit;

import com.finflow.chapter080.domain.PaymentIntentRequest;
import com.finflow.chapter080.domain.PaymentMethodType;
import com.finflow.chapter080.domain.SplitAllocation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedCollectionValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validNestedCollectionShouldPass() {
        List<SplitAllocation> splits = List.of(
            new SplitAllocation("merch_1", 500),
            new SplitAllocation("merch_2", 500)
        );

        PaymentIntentRequest request = new PaymentIntentRequest(
                "pi_123", 1000, "USD", PaymentMethodType.CRYPTO, null, null, splits
        );

        Set<ConstraintViolation<PaymentIntentRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void invalidNestedCollectionShouldProducePropertyPathErrors() {
        List<SplitAllocation> splits = List.of(
            new SplitAllocation("merch_1", 500),
            new SplitAllocation("", 0) // Invalid: Blank merchantId, amount < 1
        );

        PaymentIntentRequest request = new PaymentIntentRequest(
                "pi_123", 1000, "USD", PaymentMethodType.CRYPTO, null, null, splits
        );

        Set<ConstraintViolation<PaymentIntentRequest>> violations = validator.validate(request);
        
        assertEquals(2, violations.size());
        
        boolean hasMerchantError = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("splitAllocations[1].merchantId") 
                        && v.getMessage().contains("Merchant ID is required"));
                        
        boolean hasAmountError = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("splitAllocations[1].amountCents") 
                        && v.getMessage().contains("Amount must be at least 1 cent"));

        assertTrue(hasMerchantError);
        assertTrue(hasAmountError);
    }
}
