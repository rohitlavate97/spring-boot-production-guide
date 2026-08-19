package com.finflow.chapter080.incorrect;

import com.finflow.chapter080.correct.validation.ValidCurrency;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class RegexRecompilingCurrencyValidatorIncorrect implements ConstraintValidator<ValidCurrency, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        
        // INCORRECT: Compiling the regex on every single validation call causes high CPU utilization
        Pattern pattern = Pattern.compile("^[A-Z]{3}$");
        return pattern.matcher(value).matches();
    }
}
