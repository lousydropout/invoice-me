package com.invoiceme.customer.application.queries.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CustomerOutstandingView read model DTO.
 */
@DisplayName("CustomerOutstandingView Tests")
class CustomerOutstandingViewTest {

    @Test
    @DisplayName("Should create CustomerOutstandingView with all fields")
    void shouldCreateCustomerOutstandingView() {
        // Given
        UUID customerId = UUID.randomUUID();
        String customerName = "Test Customer";
        String customerEmail = "test@example.com";
        BigDecimal outstandingBalance = BigDecimal.valueOf(2500.75);
        Integer invoiceCount = 5;

        // When
        CustomerOutstandingView view = new CustomerOutstandingView(
            customerId,
            customerName,
            customerEmail,
            outstandingBalance,
            invoiceCount
        );

        // Then
        assertThat(view.customerId()).isEqualTo(customerId);
        assertThat(view.customerName()).isEqualTo(customerName);
        assertThat(view.customerEmail()).isEqualTo(customerEmail);
        assertThat(view.outstandingBalance()).isEqualByComparingTo(outstandingBalance);
        assertThat(view.invoiceCount()).isEqualTo(invoiceCount);
    }

    @Test
    @DisplayName("Should support zero invoice count")
    void shouldSupportZeroInvoiceCount() {
        // Given
        UUID customerId = UUID.randomUUID();

        // When
        CustomerOutstandingView view = new CustomerOutstandingView(
            customerId,
            "Customer",
            "email@example.com",
            BigDecimal.ZERO,
            0
        );

        // Then
        assertThat(view.invoiceCount()).isEqualTo(0);
        assertThat(view.outstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

