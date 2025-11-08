package com.invoiceme.payment.domain;

import com.invoiceme.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Payment entity (Task 7.1).
 * 
 * Tests domain invariants and business rules.
 */
@DisplayName("T7.1 - Payment Domain Tests")
class PaymentTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final Currency USD = Currency.getInstance("USD");
    private static final Money VALID_AMOUNT = Money.of(BigDecimal.valueOf(100.00), USD);
    private static final LocalDate PAYMENT_DATE = LocalDate.now();
    private static final PaymentMethod PAYMENT_METHOD = PaymentMethod.BANK_TRANSFER;
    private static final String REFERENCE = "REF-123";

    @Test
    @DisplayName("T7.1.16 - Create payment with valid data")
    void createPayment_withValidData() {
        // When
        Payment payment = new Payment(
            PAYMENT_ID,
            VALID_AMOUNT,
            PAYMENT_DATE,
            PAYMENT_METHOD,
            REFERENCE
        );

        // Then
        assertThat(payment.getId()).isEqualTo(PAYMENT_ID);
        assertThat(payment.getAmount()).isEqualTo(VALID_AMOUNT);
        assertThat(payment.getPaymentDate()).isEqualTo(PAYMENT_DATE);
        assertThat(payment.getMethod()).isEqualTo(PAYMENT_METHOD);
        assertThat(payment.getReference()).isEqualTo(REFERENCE);
    }

    @Test
    @DisplayName("T7.1.17 - Create payment with null reference (optional)")
    void createPayment_withNullReference() {
        // When
        Payment payment = new Payment(
            PAYMENT_ID,
            VALID_AMOUNT,
            PAYMENT_DATE,
            PAYMENT_METHOD,
            null
        );

        // Then
        assertThat(payment.getId()).isEqualTo(PAYMENT_ID);
        assertThat(payment.getReference()).isNull();
    }

    @Test
    @DisplayName("T7.1.18 - Prevent payment creation with null ID")
    void createPayment_withNullId_throwsException() {
        // When/Then
        assertThatThrownBy(() -> new Payment(
            null,
            VALID_AMOUNT,
            PAYMENT_DATE,
            PAYMENT_METHOD,
            REFERENCE
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Payment ID cannot be null");
    }

    @Test
    @DisplayName("T7.1.19 - Prevent payment creation with null amount")
    void createPayment_withNullAmount_throwsException() {
        // When/Then
        assertThatThrownBy(() -> new Payment(
            PAYMENT_ID,
            null,
            PAYMENT_DATE,
            PAYMENT_METHOD,
            REFERENCE
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Payment amount cannot be null");
    }

    @Test
    @DisplayName("T7.1.20 - Prevent payment creation with zero amount")
    void createPayment_withZeroAmount_throwsException() {
        // Given
        Money zeroAmount = Money.of(BigDecimal.ZERO, USD);

        // When/Then
        assertThatThrownBy(() -> new Payment(
            PAYMENT_ID,
            zeroAmount,
            PAYMENT_DATE,
            PAYMENT_METHOD,
            REFERENCE
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Payment amount must be positive");
    }

    @Test
    @DisplayName("T7.1.21 - Prevent payment creation with negative amount")
    void createPayment_withNegativeAmount_throwsException() {
        // Given
        Money negativeAmount = Money.of(BigDecimal.valueOf(-100.00), USD);

        // When/Then
        assertThatThrownBy(() -> new Payment(
            PAYMENT_ID,
            negativeAmount,
            PAYMENT_DATE,
            PAYMENT_METHOD,
            REFERENCE
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Payment amount must be positive");
    }

    @Test
    @DisplayName("T7.1.22 - Prevent payment creation with null payment date")
    void createPayment_withNullPaymentDate_throwsException() {
        // When/Then
        assertThatThrownBy(() -> new Payment(
            PAYMENT_ID,
            VALID_AMOUNT,
            null,
            PAYMENT_METHOD,
            REFERENCE
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Payment date cannot be null");
    }

    @Test
    @DisplayName("T7.1.23 - Prevent payment creation with null payment method")
    void createPayment_withNullPaymentMethod_throwsException() {
        // When/Then
        assertThatThrownBy(() -> new Payment(
            PAYMENT_ID,
            VALID_AMOUNT,
            PAYMENT_DATE,
            null,
            REFERENCE
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Payment method cannot be null");
    }

    @Test
    @DisplayName("T7.1.24 - Payment equality based on ID")
    void paymentEquality_basedOnId() {
        // Given
        Payment payment1 = new Payment(
            PAYMENT_ID,
            VALID_AMOUNT,
            PAYMENT_DATE,
            PAYMENT_METHOD,
            REFERENCE
        );

        Payment payment2 = new Payment(
            PAYMENT_ID,
            Money.of(BigDecimal.valueOf(200.00), USD), // Different amount
            PAYMENT_DATE.plusDays(1), // Different date
            PaymentMethod.CASH, // Different method
            "REF-456" // Different reference
        );

        Payment payment3 = new Payment(
            UUID.randomUUID(), // Different ID
            VALID_AMOUNT,
            PAYMENT_DATE,
            PAYMENT_METHOD,
            REFERENCE
        );

        // Then
        assertThat(payment1).isEqualTo(payment2); // Same ID
        assertThat(payment1).isNotEqualTo(payment3); // Different ID
        assertThat(payment1.hashCode()).isEqualTo(payment2.hashCode());
    }

    @Test
    @DisplayName("T7.1.25 - Payment toString includes all fields")
    void paymentToString_includesAllFields() {
        // Given
        Payment payment = new Payment(
            PAYMENT_ID,
            VALID_AMOUNT,
            PAYMENT_DATE,
            PAYMENT_METHOD,
            REFERENCE
        );

        // When
        String toString = payment.toString();

        // Then
        assertThat(toString).contains(PAYMENT_ID.toString());
        assertThat(toString).contains(VALID_AMOUNT.toString());
        assertThat(toString).contains(PAYMENT_DATE.toString());
        assertThat(toString).contains(PAYMENT_METHOD.toString());
        assertThat(toString).contains(REFERENCE);
    }
}

