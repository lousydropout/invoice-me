package com.invoiceme.invoice.application.commands;

import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.Payment;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.application.errors.ApplicationError;
import com.invoiceme.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;

/**
 * Handler for recording a payment against an invoice.
 */
@Service
public class RecordPaymentHandler {
    private final InvoiceRepository invoiceRepository;
    private final DomainEventPublisher eventPublisher;

    public RecordPaymentHandler(
        InvoiceRepository invoiceRepository,
        DomainEventPublisher eventPublisher
    ) {
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(RecordPaymentCommand command) {
        // Load invoice
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
            .orElseThrow(() -> ApplicationError.notFound("Invoice"));

        // Create payment value object
        Currency currency = Currency.getInstance(command.currency());
        Money amount = Money.of(command.amount(), currency);
        Payment payment = new Payment(
            command.paymentId(),
            amount,
            command.paymentDate(),
            command.method(),
            command.reference()
        );

        // Record payment (domain method enforces business rules)
        invoice.recordPayment(payment);

        // Save and publish events
        invoiceRepository.save(invoice);
        eventPublisher.publish(invoice.pullDomainEvents());
    }
}

