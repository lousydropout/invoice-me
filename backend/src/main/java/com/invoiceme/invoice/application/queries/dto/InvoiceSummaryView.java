package com.invoiceme.invoice.application.queries.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read model DTO for invoice summary (list view).
 * Simplified projection optimized for listing invoices.
 */
public record InvoiceSummaryView(
    UUID id,
    String invoiceNumber,
    UUID customerId,
    String customerName,
    String status,
    LocalDate issueDate,
    LocalDate dueDate,
    BigDecimal total,
    BigDecimal balance
) {
}

