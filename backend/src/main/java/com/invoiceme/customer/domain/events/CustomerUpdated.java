package com.invoiceme.customer.domain.events;

import com.invoiceme.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a customer is updated.
 */
public record CustomerUpdated(
    UUID customerId,
    Instant occurredAt
) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return occurredAt;
    }
}

