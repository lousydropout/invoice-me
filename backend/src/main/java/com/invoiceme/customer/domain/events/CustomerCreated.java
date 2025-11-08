package com.invoiceme.customer.domain.events;

import com.invoiceme.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a customer is created.
 */
public record CustomerCreated(
    UUID customerId,
    String name,
    String email,
    Instant occurredAt
) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return occurredAt;
    }
}

