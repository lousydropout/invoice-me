package com.invoiceme.invoice.infrastructure.persistence.mappers;

import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.invoice.infrastructure.persistence.entities.InvoiceEntity;
import com.invoiceme.invoice.infrastructure.persistence.entities.LineItemEntity;
import com.invoiceme.invoice.infrastructure.persistence.entities.PaymentEntity;
import com.invoiceme.payment.domain.Payment;
import com.invoiceme.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper for converting between Invoice domain aggregate and InvoiceEntity.
 */
@Component
public class InvoiceMapper {

    /**
     * Converts an Invoice domain aggregate to an InvoiceEntity.
     * 
     * @param invoice the domain invoice
     * @return the JPA entity
     */
    public InvoiceEntity toEntity(Invoice invoice) {
        InvoiceEntity entity = new InvoiceEntity();
        entity.setId(invoice.getId());
        entity.setCustomerId(invoice.getCustomerId());
        entity.setInvoiceNumber(invoice.getInvoiceNumber().getValue());
        entity.setIssueDate(invoice.getIssueDate());
        entity.setDueDate(invoice.getDueDate());
        entity.setStatus(invoice.getStatus());
        entity.setTaxRate(invoice.getTaxRate());
        entity.setNotes(invoice.getNotes());

        // Map line items
        List<LineItemEntity> lineItemEntities = invoice.getLineItems().stream()
            .map(lineItem -> toLineItemEntity(lineItem, entity))
            .collect(Collectors.toList());
        entity.setLineItems(lineItemEntities);

        // Map payments
        List<PaymentEntity> paymentEntities = invoice.getPayments().stream()
            .map(payment -> toPaymentEntity(payment, entity))
            .collect(Collectors.toList());
        entity.setPayments(paymentEntities);

        return entity;
    }

    /**
     * Updates an existing InvoiceEntity with data from the Invoice domain aggregate.
     * 
     * @param entity the existing JPA entity
     * @param invoice the domain invoice
     */
    public void updateEntity(InvoiceEntity entity, Invoice invoice) {
        entity.setDueDate(invoice.getDueDate());
        entity.setStatus(invoice.getStatus());
        entity.setTaxRate(invoice.getTaxRate());
        entity.setNotes(invoice.getNotes());

        // Update line items (clear and recreate)
        entity.getLineItems().clear();
        List<LineItemEntity> lineItemEntities = invoice.getLineItems().stream()
            .map(lineItem -> toLineItemEntity(lineItem, entity))
            .collect(Collectors.toList());
        entity.getLineItems().addAll(lineItemEntities);

        // Update payments (add new ones, existing ones are already there)
        // Note: We only add new payments, existing payments are preserved
        List<PaymentEntity> existingPaymentIds = new ArrayList<>(entity.getPayments());
        List<UUID> existingIds = existingPaymentIds.stream()
            .map(PaymentEntity::getId)
            .collect(Collectors.toList());
        
        invoice.getPayments().stream()
            .filter(payment -> !existingIds.contains(payment.getId()))
            .map(payment -> toPaymentEntity(payment, entity))
            .forEach(entity.getPayments()::add);
    }

    /**
     * Converts an InvoiceEntity to an Invoice domain aggregate.
     * Uses reconstruct() to avoid emitting events when loading from database.
     * 
     * @param entity the JPA entity
     * @return the domain invoice
     */
    public Invoice toDomain(InvoiceEntity entity) {
        // Map line items
        List<LineItem> lineItems = entity.getLineItems().stream()
            .map(this::toLineItem)
            .collect(Collectors.toList());

        // Map payments
        List<Payment> payments = entity.getPayments().stream()
            .map(this::toPayment)
            .collect(Collectors.toList());

        return Invoice.reconstruct(
            entity.getId(),
            entity.getCustomerId(),
            InvoiceNumber.of(entity.getInvoiceNumber()),
            entity.getIssueDate(),
            entity.getDueDate(),
            entity.getStatus(),
            lineItems,
            payments,
            entity.getNotes(),
            entity.getTaxRate()
        );
    }

    private LineItemEntity toLineItemEntity(LineItem lineItem, InvoiceEntity invoice) {
        LineItemEntity entity = new LineItemEntity();
        entity.setInvoice(invoice);
        entity.setDescription(lineItem.getDescription());
        entity.setQuantity(lineItem.getQuantity());
        entity.setUnitPrice(lineItem.getUnitPrice().getAmount());
        entity.setSubtotal(lineItem.getSubtotal().getAmount());
        return entity;
    }

    private LineItem toLineItem(LineItemEntity entity) {
        return LineItem.of(
            entity.getDescription(),
            entity.getQuantity(),
            Money.of(entity.getUnitPrice())
        );
    }

    private PaymentEntity toPaymentEntity(Payment payment, InvoiceEntity invoice) {
        PaymentEntity entity = new PaymentEntity();
        entity.setId(payment.getId());
        entity.setInvoice(invoice);
        entity.setAmount(payment.getAmount().getAmount());
        entity.setPaymentDate(payment.getPaymentDate());
        entity.setMethod(payment.getMethod());
        entity.setReference(payment.getReference());
        return entity;
    }

    private Payment toPayment(PaymentEntity entity) {
        return new Payment(
            entity.getId(),
            Money.of(entity.getAmount()),
            entity.getPaymentDate(),
            entity.getMethod(),
            entity.getReference()
        );
    }
}

