package com.invoiceme.invoice.application.commands;

import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.payment.domain.Payment;
import com.invoiceme.payment.domain.PaymentMethod;
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
        PaymentMethod paymentMethod = parsePaymentMethod(command.method());
        Payment payment = new Payment(
            command.paymentId(),
            amount,
            command.paymentDate(),
            paymentMethod,
            command.reference()
        );

        // Record payment (domain method enforces business rules)
        invoice.recordPayment(payment);

        // Save and publish events
        invoiceRepository.save(invoice);
        eventPublisher.publish(invoice.pullDomainEvents());
    }

    private PaymentMethod parsePaymentMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Payment method cannot be null or blank");
        }
        try {
            // Try to parse as enum name (e.g., "BANK_TRANSFER")
            return PaymentMethod.valueOf(method.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            // If not a valid enum, try common mappings
            String normalized = method.toUpperCase().replace(" ", "_");
            return switch (normalized) {
                case "BANK_TRANSFER", "WIRE", "WIRE_TRANSFER" -> PaymentMethod.BANK_TRANSFER;
                case "CREDIT_CARD", "CARD", "CC" -> PaymentMethod.CREDIT_CARD;
                case "DEBIT_CARD", "DEBIT" -> PaymentMethod.DEBIT_CARD;
                case "CHECK", "CHEQUE" -> PaymentMethod.CHECK;
                case "CASH" -> PaymentMethod.CASH;
                default -> PaymentMethod.OTHER;
            };
        }
    }
}

