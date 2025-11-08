package com.invoiceme.invoice.domain.events;

import com.invoiceme.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when an invoice is created.
 */
public record InvoiceCreated(
    UUID invoiceId,
    UUID customerId,
    String invoiceNumber,
    Instant occurredAt
) implements DomainEvent {
    public InvoiceCreated {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("Invoice number cannot be null or blank");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Occurred at cannot be null");
        }
    }

    public InvoiceCreated(UUID invoiceId, UUID customerId, String invoiceNumber) {
        this(invoiceId, customerId, invoiceNumber, Instant.now());
    }
}

