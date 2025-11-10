package com.invoiceme.shared.infrastructure.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.domain.DomainEvent;
import com.invoiceme.shared.infrastructure.persistence.DomainEventJpaRepository;
import com.invoiceme.shared.infrastructure.persistence.entities.DomainEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Simple in-memory domain event publisher implementation.
 * 
 * Uses Spring's ApplicationEventPublisher for synchronous, in-memory event handling.
 * Events are published after transaction commit (via @TransactionalEventListener).
 * 
 * Additionally persists events to the database for debugging and audit purposes.
 * Event persistence is done in a separate transaction to avoid affecting the main
 * business transaction if persistence fails.
 */
@Component
public class SimpleDomainEventPublisher implements DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(SimpleDomainEventPublisher.class);
    private final ApplicationEventPublisher springPublisher;
    private final DomainEventJpaRepository eventRepository;
    private final ObjectMapper objectMapper;
    private ApplicationContext applicationContext;

    public SimpleDomainEventPublisher(
            ApplicationEventPublisher springPublisher,
            DomainEventJpaRepository eventRepository,
            ObjectMapper objectMapper) {
        this.springPublisher = springPublisher;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void publish(List<? extends DomainEvent> events) {
        events.forEach(event -> {
            log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
            
            // Persist event to database in a separate transaction
            // This ensures persistence failures don't affect the main business transaction
            // Use self-injection to ensure transaction proxy is used
            if (applicationContext != null) {
                applicationContext.getBean(SimpleDomainEventPublisher.class)
                    .persistEventSafely(event);
            } else {
                // Fallback if application context not available
                persistEventSafely(event);
            }
            
            // Publish event to Spring's event system (for listeners)
            springPublisher.publishEvent(event);
        });
    }

    /**
     * Persists a domain event to the database in a separate transaction.
     * If persistence fails (e.g., table doesn't exist), it logs the error
     * but doesn't throw an exception, allowing the main business transaction to succeed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistEventSafely(DomainEvent event) {
        try {
            String eventType = event.getClass().getSimpleName();
            String payload = objectMapper.writeValueAsString(event);
            DomainEventEntity entity = new DomainEventEntity(eventType, payload);
            eventRepository.save(entity);
            log.debug("Persisted domain event: {} with id: {}", eventType, entity.getId());
        } catch (Exception e) {
            // Log error but don't fail event publication or main transaction
            // This allows the application to continue functioning even if the
            // domain_events table is missing (e.g., during initial deployment)
            log.warn("Failed to persist domain event: {} - {}", 
                    event.getClass().getSimpleName(), 
                    e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Domain event persistence error details", e);
            }
        }
    }
}

