package com.finflow.chapter090.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends DomainException {
    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("Resource %s with id %s not found", resourceType, resourceId), ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
