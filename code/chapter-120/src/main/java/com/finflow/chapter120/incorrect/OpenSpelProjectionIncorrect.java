package com.finflow.chapter120.incorrect;

import org.springframework.beans.factory.annotation.Value;

public interface OpenSpelProjectionIncorrect {

    // Incorrect: Using SpEL in projection makes it an "open projection".
    // Hibernate cannot optimize the SELECT clause, so it fetches the entire entity anyway.
    @Value("#{target.customerId.toString() + ':' + target.status}")
    String getCustomerAndStatus();
}
