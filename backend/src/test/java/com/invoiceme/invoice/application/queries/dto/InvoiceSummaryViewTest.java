package com.invoiceme.invoice.application.queries.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InvoiceSummaryView read model DTO.
 */
@DisplayName("InvoiceSummaryView Tests")
class InvoiceSummaryViewTest {

    @Test
    @DisplayName("Should create InvoiceSummaryView with all fields")
    void shouldCreateInvoiceSummaryView() {
        // Given
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String invoiceNumber = "INV-2025-0001";
        String customerName = "Test Customer";
        String status = "SENT";
        LocalDate issueDate = LocalDate.now();
        LocalDate dueDate = issueDate.plusDays(30);
        BigDecimal total = BigDecimal.valueOf(550.00);
        BigDecimal balance = BigDecimal.valueOf(300.00);

        // When
        InvoiceSummaryView view = new InvoiceSummaryView(
            id,
            invoiceNumber,
            customerId,
            customerName,
            status,
            issueDate,
            dueDate,
            total,
            balance
        );

        // Then
        assertThat(view.id()).isEqualTo(id);
        assertThat(view.invoiceNumber()).isEqualTo(invoiceNumber);
        assertThat(view.customerId()).isEqualTo(customerId);
        assertThat(view.customerName()).isEqualTo(customerName);
        assertThat(view.status()).isEqualTo(status);
        assertThat(view.issueDate()).isEqualTo(issueDate);
        assertThat(view.dueDate()).isEqualTo(dueDate);
        assertThat(view.total()).isEqualByComparingTo(total);
        assertThat(view.balance()).isEqualByComparingTo(balance);
    }

    @Test
    @DisplayName("Should support null values where appropriate")
    void shouldSupportNullValues() {
        // Given
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        // When
        InvoiceSummaryView view = new InvoiceSummaryView(
            id,
            null,
            customerId,
            null,
            null,
            null,
            null,
            null,
            null
        );

        // Then
        assertThat(view.id()).isEqualTo(id);
        assertThat(view.invoiceNumber()).isNull();
        assertThat(view.customerName()).isNull();
    }
}

