package com.invoiceme.shared.infrastructure.persistence;

import com.invoiceme.customer.application.commands.CreateCustomerCommand;
import com.invoiceme.customer.application.commands.CreateCustomerHandler;
import com.invoiceme.invoice.application.commands.CreateInvoiceCommand;
import com.invoiceme.invoice.application.commands.CreateInvoiceHandler;
import com.invoiceme.shared.infrastructure.persistence.entities.DomainEventEntity;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for domain event persistence.
 * 
 * Verifies that domain events are persisted to the database when
 * domain operations occur through the application layer.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Domain Event Persistence Integration Tests")
@Transactional
class DomainEventPersistenceIntegrationTest {

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
    private CreateCustomerHandler createCustomerHandler;

    @Autowired
    private CreateInvoiceHandler createInvoiceHandler;

    @Autowired
    private DomainEventJpaRepository eventRepository;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("T8.2.9 - CustomerCreated event is persisted")
    void customerCreatedEventPersisted() {
        // Given
        CreateCustomerCommand command = new CreateCustomerCommand(
            customerId,
            "Test Customer",
            "test@example.com",
            "123 Test St",
            "Test City",
            "12345",
            "USA",
            "555-1234",
            "NET_30"
        );

        // When
        createCustomerHandler.handle(command);

        // Then
        List<DomainEventEntity> events = eventRepository.findAllByOrderByCreatedAtDesc();
        assertThat(events).isNotEmpty();
        
        DomainEventEntity customerCreatedEvent = events.stream()
            .filter(e -> e.getType().equals("CustomerCreated"))
            .findFirst()
            .orElseThrow();
        
        assertThat(customerCreatedEvent.getType()).isEqualTo("CustomerCreated");
        assertThat(customerCreatedEvent.getPayload()).contains(customerId.toString());
        assertThat(customerCreatedEvent.getPayload()).contains("Test Customer");
        assertThat(customerCreatedEvent.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("T8.2.10 - InvoiceCreated event is persisted")
    void invoiceCreatedEventPersisted() {
        // Given
        // Create customer first
        CreateCustomerCommand customerCommand = new CreateCustomerCommand(
            customerId,
            "Test Customer",
            "test@example.com",
            "123 Test St",
            "Test City",
            "12345",
            "USA",
            "555-1234",
            "NET_30"
        );
        createCustomerHandler.handle(customerCommand);

        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand invoiceCommand = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(new CreateInvoiceCommand.LineItemDto(
                "Test Item",
                BigDecimal.ONE,
                BigDecimal.valueOf(100.00),
                "USD"
            )),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.08),
            "Test invoice"
        );

        // When
        createInvoiceHandler.handle(invoiceCommand);

        // Then
        List<DomainEventEntity> events = eventRepository.findAllByOrderByCreatedAtDesc();
        assertThat(events).isNotEmpty();
        
        DomainEventEntity invoiceCreatedEvent = events.stream()
            .filter(e -> e.getType().equals("InvoiceCreated"))
            .findFirst()
            .orElseThrow();
        
        assertThat(invoiceCreatedEvent.getType()).isEqualTo("InvoiceCreated");
        assertThat(invoiceCreatedEvent.getPayload()).contains(invoiceId.toString());
        assertThat(invoiceCreatedEvent.getPayload()).contains(customerId.toString());
        assertThat(invoiceCreatedEvent.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("T8.2.11 - Multiple events are persisted in correct order")
    void multipleEventsPersistedInOrder() {
        // Given
        CreateCustomerCommand customerCommand = new CreateCustomerCommand(
            customerId,
            "Test Customer",
            "test@example.com",
            "123 Test St",
            "Test City",
            "12345",
            "USA",
            "555-1234",
            "NET_30"
        );

        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand invoiceCommand = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(new CreateInvoiceCommand.LineItemDto(
                "Test Item",
                BigDecimal.ONE,
                BigDecimal.valueOf(100.00),
                "USD"
            )),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.08),
            "Test invoice"
        );

        // When
        createCustomerHandler.handle(customerCommand);
        createInvoiceHandler.handle(invoiceCommand);

        // Then
        List<DomainEventEntity> events = eventRepository.findAllByOrderByCreatedAtDesc();
        assertThat(events.size()).isGreaterThanOrEqualTo(2);
        
        // Verify events are ordered by creation date (newest first)
        for (int i = 0; i < events.size() - 1; i++) {
            assertThat(events.get(i).getCreatedAt())
                .isAfterOrEqualTo(events.get(i + 1).getCreatedAt());
        }
        
        // Verify both event types exist
        assertThat(events.stream().anyMatch(e -> e.getType().equals("CustomerCreated"))).isTrue();
        assertThat(events.stream().anyMatch(e -> e.getType().equals("InvoiceCreated"))).isTrue();
    }
}

