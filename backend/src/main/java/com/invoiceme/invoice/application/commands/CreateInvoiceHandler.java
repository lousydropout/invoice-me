package com.invoiceme.invoice.application.commands;

import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.application.errors.ApplicationError;
import com.invoiceme.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handler for creating a new invoice in DRAFT status.
 */
@Service
public class CreateInvoiceHandler {
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final DomainEventPublisher eventPublisher;

    public CreateInvoiceHandler(
        InvoiceRepository invoiceRepository,
        CustomerRepository customerRepository,
        DomainEventPublisher eventPublisher
    ) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UUID handle(CreateInvoiceCommand command) {
        // Validate customer exists
        if (customerRepository.findById(command.customerId()).isEmpty()) {
            throw ApplicationError.notFound("Customer");
        }

        // Generate invoice number
        InvoiceNumber invoiceNumber = InvoiceNumber.generate();

        // Convert line item DTOs to domain LineItems
        List<LineItem> lineItems = command.lineItems().stream()
            .map(dto -> {
                Currency currency = Currency.getInstance(dto.unitPriceCurrency());
                Money unitPrice = Money.of(dto.unitPriceAmount(), currency);
                return LineItem.of(dto.description(), dto.quantity(), unitPrice);
            })
            .collect(Collectors.toList());

        // Create invoice aggregate
        Invoice invoice = Invoice.create(
            command.id(),
            command.customerId(),
            invoiceNumber,
            command.issueDate(),
            command.dueDate(),
            lineItems,
            command.notes(),
            command.taxRate()
        );

        // Save and publish events
        invoiceRepository.save(invoice);
        eventPublisher.publish(invoice.pullDomainEvents());

        return invoice.getId();
    }
}

