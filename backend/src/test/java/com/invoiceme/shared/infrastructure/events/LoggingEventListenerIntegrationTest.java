package com.invoiceme.shared.infrastructure.events;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.invoiceme.customer.application.commands.CreateCustomerCommand;
import com.invoiceme.customer.application.commands.CreateCustomerHandler;
import com.invoiceme.invoice.application.commands.CreateInvoiceCommand;
import com.invoiceme.invoice.application.commands.CreateInvoiceHandler;
import com.invoiceme.invoice.application.commands.RecordPaymentCommand;
import com.invoiceme.invoice.application.commands.RecordPaymentHandler;
import com.invoiceme.invoice.application.commands.SendInvoiceCommand;
import com.invoiceme.invoice.application.commands.SendInvoiceHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for LoggingEventListener.
 * 
 * Verifies that domain events are logged when domain operations occur
 * through the application layer (commands/handlers).
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("LoggingEventListener Integration Tests")
class LoggingEventListenerIntegrationTest {

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
    private SendInvoiceHandler sendInvoiceHandler;

    @Autowired
    private RecordPaymentHandler recordPaymentHandler;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // Set up log appender to capture log events
        logger = (Logger) LoggerFactory.getLogger(LoggingEventListener.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @Test
    @DisplayName("T6.1.10 - Log CustomerCreated event when customer is created")
    void logCustomerCreatedEvent() {
        // Given
        UUID customerId = UUID.randomUUID();
        CreateCustomerCommand command = new CreateCustomerCommand(
            customerId,
            "Test Customer",
            "test@example.com",
            "123 Main St",
            "City",
            "12345",
            "US",
            "555-1234",
            "NET_30"
        );

        // When
        createCustomerHandler.handle(command);
        // Handler is @Transactional, so transaction commits automatically and events are logged

        // Then
        List<ILoggingEvent> customerEvents = logAppender.list.stream()
            .filter(e -> e.getMessage().contains("CustomerCreated"))
            .collect(Collectors.toList());
        
        assertThat(customerEvents).hasSize(1);
        assertThat(customerEvents.get(0).getMessage()).contains(customerId.toString());
    }

    @Test
    @DisplayName("T6.1.11 - Log InvoiceCreated event when invoice is created")
    void logInvoiceCreatedEvent() {
        // Given
        UUID customerId = UUID.randomUUID();
        CreateCustomerCommand customerCommand = new CreateCustomerCommand(
            customerId,
            "Test Customer",
            "test-" + customerId + "@example.com", // Unique email to avoid conflicts
            "123 Main St",
            "City",
            "12345",
            "US",
            "555-1234",
            "NET_30"
        );
        createCustomerHandler.handle(customerCommand);

        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand command = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(new CreateInvoiceCommand.LineItemDto("Service", BigDecimal.valueOf(1), BigDecimal.valueOf(100), "USD")),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test invoice"
        );

        // When
        createInvoiceHandler.handle(command);
        // Handler is @Transactional, so transaction commits automatically and events are logged

        // Then
        List<ILoggingEvent> invoiceEvents = logAppender.list.stream()
            .filter(e -> e.getMessage().contains("InvoiceCreated"))
            .collect(Collectors.toList());
        
        assertThat(invoiceEvents).hasSize(1);
        assertThat(invoiceEvents.get(0).getMessage()).contains(invoiceId.toString());
        assertThat(invoiceEvents.get(0).getMessage()).contains(customerId.toString());
    }

    @Test
    @DisplayName("T6.1.12 - Log InvoiceSent event when invoice is sent")
    void logInvoiceSentEvent() {
        // Given
        UUID customerId = UUID.randomUUID();
        CreateCustomerCommand customerCommand = new CreateCustomerCommand(
            customerId,
            "Test Customer",
            "test-" + customerId + "@example.com", // Unique email to avoid conflicts
            "123 Main St",
            "City",
            "12345",
            "US",
            "555-1234",
            "NET_30"
        );
        createCustomerHandler.handle(customerCommand);

        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand createCommand = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(new CreateInvoiceCommand.LineItemDto("Service", BigDecimal.valueOf(1), BigDecimal.valueOf(100), "USD")),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test invoice"
        );
        createInvoiceHandler.handle(createCommand);

        // When
        SendInvoiceCommand sendCommand = new SendInvoiceCommand(invoiceId);
        sendInvoiceHandler.handle(sendCommand);
        // Handler is @Transactional, so transaction commits automatically and events are logged

        // Then
        List<ILoggingEvent> sentEvents = logAppender.list.stream()
            .filter(e -> e.getMessage().contains("InvoiceSent"))
            .collect(Collectors.toList());
        
        assertThat(sentEvents).hasSize(1);
        assertThat(sentEvents.get(0).getMessage()).contains(invoiceId.toString());
        assertThat(sentEvents.get(0).getMessage()).contains(customerId.toString());
    }

    @Test
    @DisplayName("T6.1.13 - Log PaymentRecorded and InvoicePaid events when payment is recorded")
    void logPaymentRecordedAndInvoicePaidEvents() {
        // Given
        UUID customerId = UUID.randomUUID();
        CreateCustomerCommand customerCommand = new CreateCustomerCommand(
            customerId,
            "Test Customer",
            "test-" + customerId + "@example.com", // Unique email to avoid conflicts
            "123 Main St",
            "City",
            "12345",
            "US",
            "555-1234",
            "NET_30"
        );
        createCustomerHandler.handle(customerCommand);

        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand createCommand = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(new CreateInvoiceCommand.LineItemDto("Service", BigDecimal.valueOf(1), BigDecimal.valueOf(100), "USD")),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test invoice"
        );
        createInvoiceHandler.handle(createCommand);

        SendInvoiceCommand sendCommand = new SendInvoiceCommand(invoiceId);
        sendInvoiceHandler.handle(sendCommand);

        // When - Record payment equal to balance (should trigger InvoicePaid)
        // Total = 110 (100 + 10% tax)
        RecordPaymentCommand paymentCommand = new RecordPaymentCommand(
            invoiceId,
            UUID.randomUUID(), // paymentId
            BigDecimal.valueOf(110),
            "USD",
            LocalDate.now(),
            "BANK_TRANSFER",
            "REF-123"
        );
        recordPaymentHandler.handle(paymentCommand);
        // Handler is @Transactional, so transaction commits automatically and events are logged

        // Then
        List<ILoggingEvent> paymentEvents = logAppender.list.stream()
            .filter(e -> e.getMessage().contains("PaymentRecorded"))
            .collect(Collectors.toList());
        
        List<ILoggingEvent> paidEvents = logAppender.list.stream()
            .filter(e -> e.getMessage().contains("InvoicePaid"))
            .collect(Collectors.toList());
        
        assertThat(paymentEvents).hasSize(1);
        assertThat(paymentEvents.get(0).getMessage()).contains(invoiceId.toString());
        
        assertThat(paidEvents).hasSize(1);
        assertThat(paidEvents.get(0).getMessage()).contains(invoiceId.toString());
        assertThat(paidEvents.get(0).getMessage()).contains(customerId.toString());
    }

    @Test
    @DisplayName("T6.1.14 - Log events include all required fields (event name, invoiceId, customerId, timestamp)")
    void logEventsIncludeAllRequiredFields() {
        // Given
        UUID customerId = UUID.randomUUID();
        CreateCustomerCommand customerCommand = new CreateCustomerCommand(
            customerId,
            "Test Customer",
            "test-" + customerId + "@example.com", // Unique email to avoid conflicts
            "123 Main St",
            "City",
            "12345",
            "US",
            "555-1234",
            "NET_30"
        );
        createCustomerHandler.handle(customerCommand);

        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand command = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(new CreateInvoiceCommand.LineItemDto("Service", BigDecimal.valueOf(1), BigDecimal.valueOf(100), "USD")),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test invoice"
        );

        // When
        createInvoiceHandler.handle(command);
        // Handler is @Transactional, so transaction commits automatically and events are logged

        // Then
        List<ILoggingEvent> invoiceEvents = logAppender.list.stream()
            .filter(e -> e.getMessage().contains("InvoiceCreated"))
            .collect(Collectors.toList());
        
        assertThat(invoiceEvents).hasSize(1);
        String message = invoiceEvents.get(0).getMessage();
        
        // Verify all required fields are present
        assertThat(message).contains("Domain event: InvoiceCreated");
        assertThat(message).contains("invoiceId: " + invoiceId);
        assertThat(message).contains("customerId: " + customerId);
        assertThat(message).contains("timestamp:");
    }
}

