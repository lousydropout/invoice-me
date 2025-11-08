package com.invoiceme.invoice.application.commands;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to record a payment against an invoice.
 */
public record RecordPaymentCommand(
    UUID invoiceId,
    UUID paymentId,
    BigDecimal amount,
    String currency, // ISO 4217 currency code (e.g., "USD")
    LocalDate paymentDate,
    String method, // e.g., "Bank Transfer", "Credit Card", "Cash"
    String reference // optional transaction reference
) {}

