package com.invoiceme.invoice.domain.events;

import com.invoiceme.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when an invoice is updated.
 */
public record InvoiceUpdated(
    UUID invoiceId,
    Instant occurredAt
) implements DomainEvent {
    public InvoiceUpdated {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Occurred at cannot be null");
        }
    }

    public InvoiceUpdated(UUID invoiceId) {
        this(invoiceId, Instant.now());
    }
}

