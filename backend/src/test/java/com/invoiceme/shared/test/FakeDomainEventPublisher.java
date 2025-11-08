package com.invoiceme.shared.test;

import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.domain.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake domain event publisher for testing.
 * Captures published events for assertion.
 */
public class FakeDomainEventPublisher implements DomainEventPublisher {
    private final List<DomainEvent> publishedEvents = new ArrayList<>();

    @Override
    public void publish(List<? extends DomainEvent> events) {
        publishedEvents.addAll(events);
    }

    /**
     * Returns all published events.
     */
    public List<DomainEvent> getPublishedEvents() {
        return List.copyOf(publishedEvents);
    }

    /**
     * Clears all published events.
     */
    public void clear() {
        publishedEvents.clear();
    }

    /**
     * Returns the count of published events.
     */
    public int getEventCount() {
        return publishedEvents.size();
    }
}

