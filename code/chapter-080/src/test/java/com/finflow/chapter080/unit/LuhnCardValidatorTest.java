package com.finflow.chapter080.unit;

import com.finflow.chapter080.correct.validation.LuhnCardValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LuhnCardValidatorTest {

    private LuhnCardValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LuhnCardValidator();
    }

    @Test
    void nullOrBlankShouldReturnTrueToLetNotBlankHandleIt() {
        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("", null));
        assertTrue(validator.isValid("   ", null));
    }

    @Test
    void validCardNumberWithoutSpacesShouldPass() {
        // Standard test card number that passes Luhn
        assertTrue(validator.isValid("4242424242424242", null));
    }

    @Test
    void validCardNumberWithSpacesShouldPass() {
        assertTrue(validator.isValid("4242 4242 4242 4242", null));
    }

    @Test
    void validCardNumberWithDashesShouldPass() {
        assertTrue(validator.isValid("4242-4242-4242-4242", null));
    }

    @Test
    void invalidCardNumberShouldFail() {
        assertFalse(validator.isValid("4242424242424243", null));
    }

    @Test
    void nonNumericCharactersShouldFail() {
        assertFalse(validator.isValid("4242a4242b4242c", null));
    }
}
