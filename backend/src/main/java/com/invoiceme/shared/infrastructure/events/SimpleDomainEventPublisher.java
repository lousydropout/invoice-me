package com.invoiceme.shared.infrastructure.events;

import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Simple in-memory domain event publisher implementation.
 * 
 * Uses Spring's ApplicationEventPublisher for synchronous, in-memory event handling.
 * Events are published after transaction commit (via @TransactionalEventListener).
 * 
 * This is the MVP implementation - no outbox pattern, no distributed event store.
 */
@Component
public class SimpleDomainEventPublisher implements DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(SimpleDomainEventPublisher.class);
    private final ApplicationEventPublisher springPublisher;

    public SimpleDomainEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(List<? extends DomainEvent> events) {
        events.forEach(event -> {
            log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
            springPublisher.publishEvent(event);
        });
    }
}

