package com.invoiceme.invoice.application.queries.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InvoiceDetailView read model DTO.
 */
@DisplayName("InvoiceDetailView Tests")
class InvoiceDetailViewTest {

    @Test
    @DisplayName("Should create InvoiceDetailView with all fields")
    void shouldCreateInvoiceDetailView() {
        // Given
        UUID id = UUID.randomUUID();
        String invoiceNumber = "INV-2025-0001";
        String customerName = "Test Customer";
        String customerEmail = "test@example.com";
        LocalDate issueDate = LocalDate.now();
        LocalDate dueDate = issueDate.plusDays(30);
        String status = "SENT";
        BigDecimal subtotal = BigDecimal.valueOf(500.00);
        BigDecimal tax = BigDecimal.valueOf(50.00);
        BigDecimal total = BigDecimal.valueOf(550.00);
        BigDecimal balance = BigDecimal.valueOf(300.00);
        String notes = "Test notes";

        List<InvoiceDetailView.LineItemView> lineItems = List.of(
            new InvoiceDetailView.LineItemView(
                "Service A",
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(200)
            )
        );

        List<InvoiceDetailView.PaymentView> payments = List.of(
            new InvoiceDetailView.PaymentView(
                BigDecimal.valueOf(250),
                LocalDate.now(),
                "BANK_TRANSFER",
                "REF-123"
            )
        );

        // When
        InvoiceDetailView view = new InvoiceDetailView(
            id,
            invoiceNumber,
            customerName,
            customerEmail,
            issueDate,
            dueDate,
            status,
            subtotal,
            tax,
            total,
            balance,
            notes,
            lineItems,
            payments
        );

        // Then
        assertThat(view.id()).isEqualTo(id);
        assertThat(view.invoiceNumber()).isEqualTo(invoiceNumber);
        assertThat(view.customerName()).isEqualTo(customerName);
        assertThat(view.customerEmail()).isEqualTo(customerEmail);
        assertThat(view.status()).isEqualTo(status);
        assertThat(view.subtotal()).isEqualByComparingTo(subtotal);
        assertThat(view.tax()).isEqualByComparingTo(tax);
        assertThat(view.total()).isEqualByComparingTo(total);
        assertThat(view.balance()).isEqualByComparingTo(balance);
        assertThat(view.notes()).isEqualTo(notes);
        assertThat(view.lineItems()).hasSize(1);
        assertThat(view.payments()).hasSize(1);
    }

    @Test
    @DisplayName("Should create nested LineItemView correctly")
    void shouldCreateLineItemView() {
        // Given
        InvoiceDetailView.LineItemView lineItem = new InvoiceDetailView.LineItemView(
            "Service A",
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(500)
        );

        // Then
        assertThat(lineItem.description()).isEqualTo("Service A");
        assertThat(lineItem.quantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(lineItem.unitPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(lineItem.subtotal()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("Should create nested PaymentView correctly")
    void shouldCreatePaymentView() {
        // Given
        LocalDate paymentDate = LocalDate.now();
        InvoiceDetailView.PaymentView payment = new InvoiceDetailView.PaymentView(
            BigDecimal.valueOf(250),
            paymentDate,
            "BANK_TRANSFER",
            "REF-123"
        );

        // Then
        assertThat(payment.amount()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(payment.paymentDate()).isEqualTo(paymentDate);
        assertThat(payment.method()).isEqualTo("BANK_TRANSFER");
        assertThat(payment.reference()).isEqualTo("REF-123");
    }
}

