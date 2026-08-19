package com.finflow.chapter080.incorrect;

import com.finflow.chapter080.correct.validation.ValidCurrency;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class StatefulValidatorIncorrect implements ConstraintValidator<ValidCurrency, String> {

    // INCORRECT: Validator instances are singletons by default in Spring.
    // Storing state in instance variables makes this validator thread-unsafe!
    private String lastValidatedValue;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        lastValidatedValue = value; // Race condition here!
        
        // Simulating validation logic
        if (value == null) return true;
        
        boolean isValid = value.length() == 3;
        
        // This log might print a different value than what was passed into this method invocation
        // if another thread modified lastValidatedValue concurrently.
        System.out.println("Validated " + lastValidatedValue + ", result: " + isValid);
        
        return isValid;
    }
}
