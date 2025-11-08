package com.invoiceme.customer.application.queries.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CustomerView read model DTO.
 */
@DisplayName("CustomerView Tests")
class CustomerViewTest {

    @Test
    @DisplayName("Should create CustomerView with all fields")
    void shouldCreateCustomerView() {
        // Given
        UUID id = UUID.randomUUID();
        String name = "Test Customer";
        String email = "test@example.com";
        String phone = "555-1234";
        String country = "US";
        BigDecimal outstandingBalance = BigDecimal.valueOf(1250.50);

        // When
        CustomerView view = new CustomerView(
            id,
            name,
            email,
            phone,
            country,
            outstandingBalance
        );

        // Then
        assertThat(view.id()).isEqualTo(id);
        assertThat(view.name()).isEqualTo(name);
        assertThat(view.email()).isEqualTo(email);
        assertThat(view.phone()).isEqualTo(phone);
        assertThat(view.country()).isEqualTo(country);
        assertThat(view.outstandingBalance()).isEqualByComparingTo(outstandingBalance);
    }

    @Test
    @DisplayName("Should support zero outstanding balance")
    void shouldSupportZeroOutstandingBalance() {
        // Given
        UUID id = UUID.randomUUID();

        // When
        CustomerView view = new CustomerView(
            id,
            "Customer",
            "email@example.com",
            null,
            "US",
            BigDecimal.ZERO
        );

        // Then
        assertThat(view.outstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

