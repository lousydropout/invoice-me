package com.invoiceme.customer.application.queries.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read model DTO for customer information.
 * Used for customer listing and queries.
 */
public record CustomerView(
    UUID id,
    String name,
    String email,
    String phone,
    String country,
    BigDecimal outstandingBalance
) {
}

