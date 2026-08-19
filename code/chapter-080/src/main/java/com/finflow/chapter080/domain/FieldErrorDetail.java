package com.finflow.chapter080.domain;

public record FieldErrorDetail(
    String field,
    Object rejectedValue,
    String message
) {
}
