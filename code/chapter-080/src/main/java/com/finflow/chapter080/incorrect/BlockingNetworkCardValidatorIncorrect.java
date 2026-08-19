package com.finflow.chapter080.incorrect;

import com.finflow.chapter080.correct.validation.ValidCardNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class BlockingNetworkCardValidatorIncorrect implements ConstraintValidator<ValidCardNumber, String> {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;

        try {
            // INCORRECT: Doing blocking remote network I/O inside a validation constraint!
            // This will block the Tomcat worker thread. If the third-party service is slow,
            // the entire application's thread pool will be exhausted.
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.thirdparty-bin-lookup.com/validate?card=" + value))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            // Fails validation if network is down
            return false;
        }
    }
}
