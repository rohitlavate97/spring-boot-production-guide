package com.finflow.troubleshooting.module05.model;

import com.finflow.troubleshooting.module05.validation.StrongPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "Customer ID must not be blank")
        String customerId,

        @StrongPassword(message = "Authorization PIN/Password is weak")
        String authPin,

        @NotEmpty(message = "Order must contain at least one item")
        @Size(max = 50, message = "Order cannot contain more than 50 items")
        @Valid // CRITICAL FOR CASCADING VALIDATION ON NESTED ITEMS
        List<OrderItemRequest> items
) {}
