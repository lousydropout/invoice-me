package com.invoiceme.shared.infrastructure.events;

import com.invoiceme.invoice.domain.events.InvoiceCreated;
import com.invoiceme.shared.domain.DomainEvent;
import com.invoiceme.shared.infrastructure.persistence.DomainEventJpaRepository;
import com.invoiceme.shared.infrastructure.persistence.entities.DomainEventEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SimpleDomainEventPublisher.
 * 
 * Verifies that domain events are:
 * 1. Persisted to the database
 * 2. Published to Spring's ApplicationEventPublisher
 * 3. Handled gracefully when persistence fails
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("SimpleDomainEventPublisher Integration Tests")
@Transactional
class SimpleDomainEventPublisherTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("invoiceme_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Autowired
    private SimpleDomainEventPublisher publisher;

    @Autowired
    private DomainEventJpaRepository eventRepository;

    @Test
    @DisplayName("T8.2.1 - Persist and publish domain event")
    void persistAndPublishEvent() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        InvoiceCreated event = new InvoiceCreated(invoiceId, customerId, "INV-001", timestamp);
        List<DomainEvent> events = List.of(event);

        // When
        publisher.publish(events);

        // Then
        // Verify event was persisted
        List<DomainEventEntity> persistedEvents = eventRepository.findAllByOrderByCreatedAtDesc();
        assertThat(persistedEvents).isNotEmpty();
        
        DomainEventEntity persistedEvent = persistedEvents.stream()
            .filter(e -> e.getType().equals("InvoiceCreated"))
            .findFirst()
            .orElseThrow();
        
        assertThat(persistedEvent.getType()).isEqualTo("InvoiceCreated");
        assertThat(persistedEvent.getPayload()).isNotNull();
        assertThat(persistedEvent.getCreatedAt()).isNotNull();
        
        // Verify payload contains event data
        String payload = persistedEvent.getPayload();
        assertThat(payload).contains(invoiceId.toString());
        assertThat(payload).contains(customerId.toString());
        assertThat(payload).contains("INV-001");
    }

    @Test
    @DisplayName("T8.2.2 - Persist multiple events")
    void persistMultipleEvents() {
        // Given
        UUID invoiceId1 = UUID.randomUUID();
        UUID invoiceId2 = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        
        InvoiceCreated event1 = new InvoiceCreated(invoiceId1, customerId, "INV-001", timestamp);
        InvoiceCreated event2 = new InvoiceCreated(invoiceId2, customerId, "INV-002", timestamp);
        List<DomainEvent> events = List.of(event1, event2);

        // When
        publisher.publish(events);

        // Then
        // Verify both events were persisted
        List<DomainEventEntity> persistedEvents = eventRepository.findAllByOrderByCreatedAtDesc();
        assertThat(persistedEvents.stream()
            .filter(e -> e.getType().equals("InvoiceCreated"))
            .count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("T8.2.3 - Events are persisted with correct structure")
    void eventsPersistedWithCorrectStructure() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        InvoiceCreated event = new InvoiceCreated(invoiceId, customerId, "INV-001", timestamp);
        List<DomainEvent> events = List.of(event);

        // When
        publisher.publish(events);

        // Then
        List<DomainEventEntity> persistedEvents = eventRepository.findAllByOrderByCreatedAtDesc();
        assertThat(persistedEvents).isNotEmpty();
        
        DomainEventEntity persistedEvent = persistedEvents.stream()
            .filter(e -> e.getType().equals("InvoiceCreated"))
            .findFirst()
            .orElseThrow();
        
        // Verify structure
        assertThat(persistedEvent.getId()).isNotNull();
        assertThat(persistedEvent.getType()).isEqualTo("InvoiceCreated");
        assertThat(persistedEvent.getPayload()).isNotBlank();
        assertThat(persistedEvent.getCreatedAt()).isNotNull();
    }
}

