package com.invoiceme.invoice.application.commands;

import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.application.errors.ApplicationError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for sending an invoice (transitions from DRAFT to SENT status).
 */
@Service
public class SendInvoiceHandler {
    private final InvoiceRepository invoiceRepository;
    private final DomainEventPublisher eventPublisher;

    public SendInvoiceHandler(
        InvoiceRepository invoiceRepository,
        DomainEventPublisher eventPublisher
    ) {
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(SendInvoiceCommand command) {
        // Load invoice
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
            .orElseThrow(() -> ApplicationError.notFound("Invoice"));

        // Send invoice (domain method enforces DRAFT status requirement)
        invoice.sendInvoice();

        // Save and publish events
        invoiceRepository.save(invoice);
        eventPublisher.publish(invoice.pullDomainEvents());
    }
}

