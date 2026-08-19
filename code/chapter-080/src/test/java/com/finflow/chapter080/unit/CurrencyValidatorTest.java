package com.finflow.chapter080.unit;

import com.finflow.chapter080.correct.validation.CurrencyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrencyValidatorTest {

    private CurrencyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CurrencyValidator();
    }

    @Test
    void nullOrBlankShouldReturnTrue() {
        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("", null));
    }

    @Test
    void validCurrenciesShouldPass() {
        assertTrue(validator.isValid("USD", null));
        assertTrue(validator.isValid("EUR", null));
        assertTrue(validator.isValid("GBP", null));
        assertTrue(validator.isValid("JPY", null));
    }

    @Test
    void invalidCurrenciesShouldFail() {
        assertFalse(validator.isValid("XYZ", null));
        assertFalse(validator.isValid("usd", null)); // Case sensitive
        assertFalse(validator.isValid("US", null));
        assertFalse(validator.isValid("USDD", null));
    }
}
