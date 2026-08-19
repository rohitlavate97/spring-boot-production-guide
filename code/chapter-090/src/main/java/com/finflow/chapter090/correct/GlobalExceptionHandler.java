package com.finflow.chapter090.correct;

import com.finflow.chapter090.exception.FinFlowException;
import com.finflow.chapter090.exception.GatewayTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.finflow.chapter090.correct")
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FinFlowException.class)
    public ResponseEntity<ProblemDetail> handleFinFlowException(FinFlowException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
        problemDetail.setTitle(ex.getErrorCode().name());
        problemDetail.setType(URI.create("https://api.finflow.com/errors/" + ex.getErrorCode().name().toLowerCase()));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("traceId", UUID.randomUUID().toString());
        problemDetail.setProperty("errorCode", ex.getErrorCode().name());

        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof GatewayTimeoutException gte) {
            headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(gte.getRetryAfterSeconds()));
        }

        if (ex.getHttpStatus().is5xxServerError()) {
            log.error("Infrastructure Error: {}", ex.getMessage(), ex);
        } else {
            log.warn("Domain Error: {}", ex.getMessage());
        }

        return new ResponseEntity<>(problemDetail, headers, ex.getHttpStatus());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameters");
        problemDetail.setTitle("INVALID_REQUEST");
        problemDetail.setType(URI.create("https://api.finflow.com/errors/invalid_request"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("traceId", UUID.randomUUID().toString());
        
        List<String> invalidParams = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .collect(Collectors.toList());
        problemDetail.setProperty("invalidParams", invalidParams);

        log.warn("Validation Error: {}", ex.getMessage());

        return new ResponseEntity<>(problemDetail, headers, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllExceptions(Exception ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled Exception (traceId: {}): {}", traceId, ex.getMessage(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setTitle("INTERNAL_SERVER_ERROR");
        problemDetail.setType(URI.create("https://api.finflow.com/errors/internal_server_error"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("traceId", traceId);

        return new ResponseEntity<>(problemDetail, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
