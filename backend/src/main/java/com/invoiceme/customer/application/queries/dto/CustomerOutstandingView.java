package com.invoiceme.customer.application.queries.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read model DTO for customer outstanding balance report.
 * Specialized view for outstanding balance queries.
 */
public record CustomerOutstandingView(
    UUID customerId,
    String customerName,
    String customerEmail,
    BigDecimal outstandingBalance,
    Integer invoiceCount
) {
}

