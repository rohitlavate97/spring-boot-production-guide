package com.finflow.chapter080.domain;

import java.time.Instant;
import java.util.List;

public record ValidationErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldErrorDetail> fieldErrors
) {
}
