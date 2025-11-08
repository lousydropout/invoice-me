package com.invoiceme.invoice.application.queries.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read model DTO for full invoice details.
 * Complete projection with all invoice information including line items and payments.
 */
public record InvoiceDetailView(
    UUID id,
    String invoiceNumber,
    String customerName,
    String customerEmail,
    LocalDate issueDate,
    LocalDate dueDate,
    String status,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal total,
    BigDecimal balance,
    String notes,
    List<LineItemView> lineItems,
    List<PaymentView> payments
) {
    /**
     * Read model DTO for a line item within an invoice detail view.
     */
    public record LineItemView(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
    ) {}

    /**
     * Read model DTO for a payment within an invoice detail view.
     */
    public record PaymentView(
        BigDecimal amount,
        LocalDate paymentDate,
        String method,
        String reference
    ) {}
}

