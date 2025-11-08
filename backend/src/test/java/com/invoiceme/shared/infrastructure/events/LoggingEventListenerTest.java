package com.invoiceme.shared.infrastructure.events;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.invoiceme.customer.domain.events.CustomerCreated;
import com.invoiceme.customer.domain.events.CustomerDeleted;
import com.invoiceme.customer.domain.events.CustomerUpdated;
import com.invoiceme.invoice.domain.events.InvoiceCreated;
import com.invoiceme.invoice.domain.events.InvoicePaid;
import com.invoiceme.invoice.domain.events.InvoiceSent;
import com.invoiceme.invoice.domain.events.InvoiceUpdated;
import com.invoiceme.invoice.domain.events.PaymentRecorded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LoggingEventListener.
 * 
 * Verifies that domain events are logged with structured information:
 * - Event name
 * - Invoice ID (when available)
 * - Customer ID (when available)
 * - Timestamp
 */
@DisplayName("LoggingEventListener Unit Tests")
class LoggingEventListenerTest {

    private LoggingEventListener listener;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        listener = new LoggingEventListener();
        
        // Set up log appender to capture log events
        logger = (Logger) LoggerFactory.getLogger(LoggingEventListener.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @Test
    @DisplayName("T6.1.1 - Log InvoiceCreated event with invoiceId and customerId")
    void logInvoiceCreated() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        InvoiceCreated event = new InvoiceCreated(invoiceId, customerId, "INV-001", timestamp);

        // When
        listener.onInvoiceCreated(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getMessage()).contains("Domain event: InvoiceCreated");
        assertThat(logEvent.getMessage()).contains(invoiceId.toString());
        assertThat(logEvent.getMessage()).contains(customerId.toString());
        assertThat(logEvent.getMessage()).contains(timestamp.toString());
    }

    @Test
    @DisplayName("T6.1.2 - Log InvoiceUpdated event with invoiceId")
    void logInvoiceUpdated() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        InvoiceUpdated event = new InvoiceUpdated(invoiceId, timestamp);

        // When
        listener.onInvoiceUpdated(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getMessage()).contains("Domain event: InvoiceUpdated");
        assertThat(logEvent.getMessage()).contains(invoiceId.toString());
        assertThat(logEvent.getMessage()).contains("customerId: N/A");
    }

    @Test
    @DisplayName("T6.1.3 - Log InvoiceSent event with invoiceId and customerId")
    void logInvoiceSent() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        InvoiceSent event = new InvoiceSent(invoiceId, customerId, "INV-001", timestamp);

        // When
        listener.onInvoiceSent(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getMessage()).contains("Domain event: InvoiceSent");
        assertThat(logEvent.getMessage()).contains(invoiceId.toString());
        assertThat(logEvent.getMessage()).contains(customerId.toString());
    }

    @Test
    @DisplayName("T6.1.4 - Log PaymentRecorded event with invoiceId")
    void logPaymentRecorded() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        PaymentRecorded event = new PaymentRecorded(invoiceId, paymentId, BigDecimal.valueOf(100.00), "BANK_TRANSFER", timestamp);

        // When
        listener.onPaymentRecorded(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getMessage()).contains("Domain event: PaymentRecorded");
        assertThat(logEvent.getMessage()).contains(invoiceId.toString());
        assertThat(logEvent.getMessage()).contains("customerId: N/A");
    }

    @Test
    @DisplayName("T6.1.5 - Log InvoicePaid event with invoiceId and customerId")
    void logInvoicePaid() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        InvoicePaid event = new InvoicePaid(invoiceId, customerId, "INV-001", timestamp);

        // When
        listener.onInvoicePaid(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getMessage()).contains("Domain event: InvoicePaid");
        assertThat(logEvent.getMessage()).contains(invoiceId.toString());
        assertThat(logEvent.getMessage()).contains(customerId.toString());
    }

    @Test
    @DisplayName("T6.1.6 - Log CustomerCreated event with customerId")
    void logCustomerCreated() {
        // Given
        UUID customerId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        CustomerCreated event = new CustomerCreated(customerId, "Test Customer", "test@example.com", timestamp);

        // When
        listener.onCustomerCreated(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getMessage()).contains("Domain event: CustomerCreated");
        assertThat(logEvent.getMessage()).contains(customerId.toString());
        assertThat(logEvent.getMessage()).contains("invoiceId: N/A");
    }

    @Test
    @DisplayName("T6.1.7 - Log CustomerUpdated event with customerId")
    void logCustomerUpdated() {
        // Given
        UUID customerId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        CustomerUpdated event = new CustomerUpdated(customerId, timestamp);

        // When
        listener.onCustomerUpdated(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getMessage()).contains("Domain event: CustomerUpdated");
        assertThat(logEvent.getMessage()).contains(customerId.toString());
    }

    @Test
    @DisplayName("T6.1.8 - Log CustomerDeleted event with customerId")
    void logCustomerDeleted() {
        // Given
        UUID customerId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        CustomerDeleted event = new CustomerDeleted(customerId, timestamp);

        // When
        listener.onCustomerDeleted(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getMessage()).contains("Domain event: CustomerDeleted");
        assertThat(logEvent.getMessage()).contains(customerId.toString());
    }

    @Test
    @DisplayName("T6.1.9 - Log events include timestamp")
    void logEventsIncludeTimestamp() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2025-01-15T10:30:00Z");
        InvoiceCreated event = new InvoiceCreated(invoiceId, customerId, "INV-001", timestamp);

        // When
        listener.onInvoiceCreated(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getMessage()).contains("timestamp: 2025-01-15T10:30:00Z");
    }
}

