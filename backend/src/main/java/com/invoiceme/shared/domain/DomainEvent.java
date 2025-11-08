package com.invoiceme.shared.domain;

import java.time.Instant;

/**
 * Marker interface for domain events.
 * 
 * All domain events must implement this interface and provide an occurrence timestamp.
 * Domain events represent something that happened in the past and are immutable.
 */
public interface DomainEvent {
    /**
     * Returns the timestamp when the event occurred.
     * 
     * @return the instant when the event occurred
     */
    Instant occurredAt();
}

