package com.invoiceme.shared.application.bus;

import com.invoiceme.shared.domain.DomainEvent;

import java.util.List;

/**
 * Interface for publishing domain events.
 * 
 * Application layer uses this interface to publish domain events after saving aggregates.
 * Infrastructure layer provides the implementation.
 */
public interface DomainEventPublisher {
    /**
     * Publishes a list of domain events.
     * 
     * @param events the domain events to publish
     */
    void publish(List<? extends DomainEvent> events);
}

