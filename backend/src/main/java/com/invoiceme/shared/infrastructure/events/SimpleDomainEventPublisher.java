package com.invoiceme.shared.infrastructure.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.domain.DomainEvent;
import com.invoiceme.shared.infrastructure.persistence.DomainEventJpaRepository;
import com.invoiceme.shared.infrastructure.persistence.entities.DomainEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Simple in-memory domain event publisher implementation.
 * 
 * Uses Spring's ApplicationEventPublisher for synchronous, in-memory event handling.
 * Events are published after transaction commit (via @TransactionalEventListener).
 * 
 * Additionally persists events to the database for debugging and audit purposes.
 */
@Component
public class SimpleDomainEventPublisher implements DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(SimpleDomainEventPublisher.class);
    private final ApplicationEventPublisher springPublisher;
    private final DomainEventJpaRepository eventRepository;
    private final ObjectMapper objectMapper;

    public SimpleDomainEventPublisher(
            ApplicationEventPublisher springPublisher,
            DomainEventJpaRepository eventRepository,
            ObjectMapper objectMapper) {
        this.springPublisher = springPublisher;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(List<? extends DomainEvent> events) {
        events.forEach(event -> {
            log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
            
            // Persist event to database
            try {
                String eventType = event.getClass().getSimpleName();
                String payload = objectMapper.writeValueAsString(event);
                DomainEventEntity entity = new DomainEventEntity(eventType, payload);
                eventRepository.save(entity);
                log.debug("Persisted domain event: {} with id: {}", eventType, entity.getId());
            } catch (Exception e) {
                // Log error but don't fail event publication
                log.error("Failed to persist domain event: {}", event.getClass().getSimpleName(), e);
            }
            
            // Publish event to Spring's event system (for listeners)
            springPublisher.publishEvent(event);
        });
    }
}

