package com.invoiceme.invoice.domain.events;

import com.invoiceme.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a payment is recorded against an invoice.
 */
public record PaymentRecorded(
    UUID invoiceId,
    UUID paymentId,
    BigDecimal amount,
    Instant occurredAt
) implements DomainEvent {
    public PaymentRecorded {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Occurred at cannot be null");
        }
    }

    public PaymentRecorded(UUID invoiceId, UUID paymentId, BigDecimal amount) {
        this(invoiceId, paymentId, amount, Instant.now());
    }
}

