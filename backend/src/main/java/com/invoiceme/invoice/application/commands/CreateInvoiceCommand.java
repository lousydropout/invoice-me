package com.invoiceme.invoice.application.commands;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Command to create a new invoice in DRAFT status.
 */
public record CreateInvoiceCommand(
    UUID id,
    UUID customerId,
    List<LineItemDto> lineItems,
    LocalDate issueDate,
    LocalDate dueDate,
    BigDecimal taxRate, // e.g., 0.10 for 10%
    String notes
) {
    /**
     * DTO for a line item in the command.
     */
    public record LineItemDto(
        String description,
        BigDecimal quantity,
        BigDecimal unitPriceAmount,
        String unitPriceCurrency // ISO 4217 currency code (e.g., "USD")
    ) {}
}

