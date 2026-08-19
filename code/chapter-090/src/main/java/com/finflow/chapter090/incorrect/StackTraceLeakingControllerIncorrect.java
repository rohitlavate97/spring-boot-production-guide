package com.finflow.chapter090.incorrect;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/incorrect/users")
public class StackTraceLeakingControllerIncorrect {

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id) {
        try {
            // Simulated database error
            throw new SQLException("Table 'finflow.users' doesn't exist");
        } catch (Exception e) {
            // ANTI-PATTERN: Leaking internal schema details and full stack trace in HTTP 500 response
            String stackTrace = Arrays.stream(e.getStackTrace())
                    .map(StackTraceElement::toString)
                    .collect(Collectors.joining("\n"));
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage() + "\nStack Trace:\n" + stackTrace);
        }
    }
}
