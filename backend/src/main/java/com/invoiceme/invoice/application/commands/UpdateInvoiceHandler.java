package com.invoiceme.invoice.application.commands;

import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.application.errors.ApplicationError;
import com.invoiceme.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler for updating an existing invoice (only allowed in DRAFT status).
 */
@Service
public class UpdateInvoiceHandler {
    private final InvoiceRepository invoiceRepository;
    private final DomainEventPublisher eventPublisher;

    public UpdateInvoiceHandler(
        InvoiceRepository invoiceRepository,
        DomainEventPublisher eventPublisher
    ) {
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(UpdateInvoiceCommand command) {
        // Load invoice
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
            .orElseThrow(() -> ApplicationError.notFound("Invoice"));

        // Convert line item DTOs to domain LineItems
        List<LineItem> lineItems = command.lineItems().stream()
            .map(dto -> {
                Currency currency = Currency.getInstance(dto.unitPriceCurrency());
                Money unitPrice = Money.of(dto.unitPriceAmount(), currency);
                return LineItem.of(dto.description(), dto.quantity(), unitPrice);
            })
            .collect(Collectors.toList());

        // Update invoice (domain methods enforce DRAFT status requirement)
        invoice.updateLineItems(lineItems);
        invoice.updateDueDate(command.dueDate());
        invoice.updateTaxRate(command.taxRate());
        invoice.updateNotes(command.notes());

        // Save and publish events
        invoiceRepository.save(invoice);
        eventPublisher.publish(invoice.pullDomainEvents());
    }
}

