package com.invoiceme.invoice.application.commands;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Command to update an existing invoice (only allowed in DRAFT status).
 */
public record UpdateInvoiceCommand(
    UUID invoiceId,
    List<CreateInvoiceCommand.LineItemDto> lineItems,
    LocalDate dueDate,
    BigDecimal taxRate,
    String notes
) {}

