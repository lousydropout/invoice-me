package com.invoiceme.invoice.api.dto;

import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.payment.domain.Payment;
import com.invoiceme.shared.domain.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for invoice data.
 */
public record InvoiceResponse(
    UUID id,
    UUID customerId,
    String invoiceNumber,
    LocalDate issueDate,
    LocalDate dueDate,
    String status, // DRAFT, SENT, PAID
    List<LineItemResponse> lineItems,
    List<PaymentResponse> payments,
    BigDecimal taxRate,
    String notes,
    MoneyResponse subtotal,
    MoneyResponse tax,
    MoneyResponse total,
    MoneyResponse balance
) {
    /**
     * Response DTO for a line item.
     */
    public record LineItemResponse(
        String description,
        BigDecimal quantity,
        MoneyResponse unitPrice,
        MoneyResponse subtotal
    ) {}

    /**
     * Response DTO for a payment.
     */
    public record PaymentResponse(
        UUID id,
        MoneyResponse amount,
        LocalDate paymentDate,
        String method,
        String reference
    ) {}

    /**
     * Response DTO for money amounts.
     */
    public record MoneyResponse(
        BigDecimal amount,
        String currency
    ) {}

    /**
     * Creates an InvoiceResponse from an Invoice domain aggregate.
     *
     * @param invoice the invoice domain aggregate
     * @return InvoiceResponse
     */
    public static InvoiceResponse from(Invoice invoice) {
        List<LineItemResponse> lineItemResponses = invoice.getLineItems().stream()
            .map(InvoiceResponse::toLineItemResponse)
            .toList();

        List<PaymentResponse> paymentResponses = invoice.getPayments().stream()
            .map(InvoiceResponse::toPaymentResponse)
            .toList();

        return new InvoiceResponse(
            invoice.getId(),
            invoice.getCustomerId(),
            invoice.getInvoiceNumber().getValue(),
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getStatus().name(),
            lineItemResponses,
            paymentResponses,
            invoice.getTaxRate(),
            invoice.getNotes(),
            toMoneyResponse(invoice.calculateSubtotal()),
            toMoneyResponse(invoice.calculateTax()),
            toMoneyResponse(invoice.calculateTotal()),
            toMoneyResponse(invoice.calculateBalance())
        );
    }

    private static LineItemResponse toLineItemResponse(LineItem lineItem) {
        return new LineItemResponse(
            lineItem.getDescription(),
            lineItem.getQuantity(),
            toMoneyResponse(lineItem.getUnitPrice()),
            toMoneyResponse(lineItem.getSubtotal())
        );
    }

    private static PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            toMoneyResponse(payment.getAmount()),
            payment.getPaymentDate(),
            payment.getMethod().name(),
            payment.getReference()
        );
    }

    private static MoneyResponse toMoneyResponse(Money money) {
        return new MoneyResponse(
            money.getAmount(),
            money.getCurrency().getCurrencyCode()
        );
    }
}

