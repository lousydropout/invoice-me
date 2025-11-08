package com.invoiceme.shared.infrastructure.events;

import com.invoiceme.customer.domain.events.CustomerCreated;
import com.invoiceme.customer.domain.events.CustomerDeleted;
import com.invoiceme.customer.domain.events.CustomerUpdated;
import com.invoiceme.invoice.domain.events.InvoiceCreated;
import com.invoiceme.invoice.domain.events.InvoicePaid;
import com.invoiceme.invoice.domain.events.InvoiceSent;
import com.invoiceme.invoice.domain.events.InvoiceUpdated;
import com.invoiceme.invoice.domain.events.PaymentRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Logging event listener for domain events.
 * 
 * Provides structured logging for all domain events with:
 * - Event name
 * - Invoice ID (when available)
 * - Customer ID (when available)
 * - Timestamp
 * 
 * Logs are accessible in local and AWS CloudWatch logs.
 */
@Component
public class LoggingEventListener {
    private static final Logger log = LoggerFactory.getLogger(LoggingEventListener.class);

    // Invoice Events

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceCreated(InvoiceCreated event) {
        logEvent("InvoiceCreated", event.invoiceId(), event.customerId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceUpdated(InvoiceUpdated event) {
        logEvent("InvoiceUpdated", event.invoiceId(), null, event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceSent(InvoiceSent event) {
        logEvent("InvoiceSent", event.invoiceId(), event.customerId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRecorded(PaymentRecorded event) {
        logEvent("PaymentRecorded", event.invoiceId(), null, event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoicePaid(InvoicePaid event) {
        logEvent("InvoicePaid", event.invoiceId(), event.customerId(), event.occurredAt());
    }

    // Customer Events

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerCreated(CustomerCreated event) {
        logEvent("CustomerCreated", null, event.customerId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerUpdated(CustomerUpdated event) {
        logEvent("CustomerUpdated", null, event.customerId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerDeleted(CustomerDeleted event) {
        logEvent("CustomerDeleted", null, event.customerId(), event.occurredAt());
    }

    /**
     * Logs a domain event with structured logging.
     * 
     * Uses MDC (Mapped Diagnostic Context) for structured logging that works with
     * both local logging and AWS CloudWatch Logs Insights.
     * 
     * @param eventName the name of the event
     * @param invoiceId the invoice ID (if applicable)
     * @param customerId the customer ID (if applicable)
     * @param timestamp the event timestamp
     */
    private void logEvent(String eventName, UUID invoiceId, UUID customerId, java.time.Instant timestamp) {
        // Set MDC context for structured logging
        MDC.put("event.name", eventName);
        if (invoiceId != null) {
            MDC.put("event.invoiceId", invoiceId.toString());
        }
        if (customerId != null) {
            MDC.put("event.customerId", customerId.toString());
        }
        MDC.put("event.timestamp", timestamp.toString());

        try {
            // Log with structured format
            String message = String.format(
                "Domain event: %s | invoiceId: %s | customerId: %s | timestamp: %s",
                eventName,
                invoiceId != null ? invoiceId : "N/A",
                customerId != null ? customerId : "N/A",
                timestamp
            );
            log.info(message);
        } finally {
            // Clear MDC to avoid context leakage
            MDC.clear();
        }
    }
}

