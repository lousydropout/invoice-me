package com.invoiceme.invoice.application.queries;

import com.invoiceme.invoice.application.queries.dto.InvoiceDetailView;
import com.invoiceme.invoice.infrastructure.persistence.entities.InvoiceEntity;
import com.invoiceme.invoice.infrastructure.persistence.entities.LineItemEntity;
import com.invoiceme.invoice.infrastructure.persistence.entities.PaymentEntity;
import com.invoiceme.invoice.infrastructure.persistence.InvoiceJpaRepository;
import com.invoiceme.customer.infrastructure.persistence.entities.CustomerEntity;
import com.invoiceme.customer.infrastructure.persistence.CustomerJpaRepository;
import com.invoiceme.shared.application.errors.ApplicationError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Query handler for retrieving a single invoice by ID with full details.
 */
@Service
public class GetInvoiceByIdHandler {
    private final InvoiceJpaRepository invoiceRepository;
    private final CustomerJpaRepository customerRepository;

    public GetInvoiceByIdHandler(
        InvoiceJpaRepository invoiceRepository,
        CustomerJpaRepository customerRepository
    ) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public InvoiceDetailView handle(GetInvoiceByIdQuery query) {
        UUID invoiceId = query.invoiceId();

        // Fetch invoice with line items
        InvoiceEntity invoice = invoiceRepository.findByIdWithLineItems(invoiceId)
            .orElseThrow(() -> ApplicationError.notFound("Invoice"));

        // Fetch payments separately
        InvoiceEntity invoiceWithPayments = invoiceRepository.findByIdWithPayments(invoiceId)
            .orElseThrow(() -> ApplicationError.notFound("Invoice"));

        // Fetch customer
        CustomerEntity customer = customerRepository.findById(invoice.getCustomerId())
            .orElseThrow(() -> ApplicationError.notFound("Customer"));

        // Calculate totals
        BigDecimal subtotal = invoice.getLineItems().stream()
            .map(LineItemEntity::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tax = subtotal.multiply(invoice.getTaxRate());
        BigDecimal total = subtotal.add(tax);

        BigDecimal totalPaid = invoiceWithPayments.getPayments().stream()
            .map(PaymentEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balance = total.subtract(totalPaid);

        // Map line items
        List<InvoiceDetailView.LineItemView> lineItemViews = invoice.getLineItems().stream()
            .map(item -> new InvoiceDetailView.LineItemView(
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
            ))
            .toList();

        // Map payments
        List<InvoiceDetailView.PaymentView> paymentViews = invoiceWithPayments.getPayments().stream()
            .map(payment -> new InvoiceDetailView.PaymentView(
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getMethod().name(),
                payment.getReference()
            ))
            .toList();

        return new InvoiceDetailView(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            customer.getName(),
            customer.getEmail(),
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getStatus().name(),
            subtotal,
            tax,
            total,
            balance,
            invoice.getNotes(),
            lineItemViews,
            paymentViews
        );
    }
}

