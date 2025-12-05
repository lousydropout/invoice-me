package com.invoiceme.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Money Value Object")
class MoneyTest {

    @Nested
    @DisplayName("isEffectivelyZero")
    class IsEffectivelyZeroTests {

        @Test
        @DisplayName("returns true for zero")
        void zero_returnsTrue() {
            Money money = Money.of(BigDecimal.ZERO);
            assertThat(money.isEffectivelyZero()).isTrue();
        }

        @Test
        @DisplayName("0.009 rounds to 0.01 which IS effectively zero (threshold <= 0.01)")
        void smallPositiveRoundsUp_returnsTrue() {
            // Money rounds 0.009 to 0.01 (HALF_UP), which IS effectively zero
            Money money = Money.of(new BigDecimal("0.009"));
            assertThat(money.isEffectivelyZero()).isTrue();
        }

        @Test
        @DisplayName("0.004 rounds to 0.00 which IS effectively zero")
        void smallPositiveRoundsDown_returnsTrue() {
            // Money rounds 0.004 to 0.00 (HALF_UP), so it IS effectively zero
            Money money = Money.of(new BigDecimal("0.004"));
            assertThat(money.isEffectivelyZero()).isTrue();
        }

        @Test
        @DisplayName("returns true for negative amount (overpayment)")
        void negative_returnsTrue() {
            Money money = Money.of(new BigDecimal("-0.50"));
            assertThat(money.isEffectivelyZero()).isTrue();
        }

        @Test
        @DisplayName("returns true for small negative amount")
        void smallNegative_returnsTrue() {
            Money money = Money.of(new BigDecimal("-0.01"));
            assertThat(money.isEffectivelyZero()).isTrue();
        }

        @Test
        @DisplayName("returns true for exactly one cent (threshold <= 0.01)")
        void oneCent_returnsTrue() {
            Money money = Money.of(new BigDecimal("0.01"));
            assertThat(money.isEffectivelyZero()).isTrue();
        }

        @Test
        @DisplayName("returns false for larger amount")
        void largerAmount_returnsFalse() {
            Money money = Money.of(new BigDecimal("100.00"));
            assertThat(money.isEffectivelyZero()).isFalse();
        }

        @Test
        @DisplayName("returns false for two cents")
        void twoCents_returnsFalse() {
            Money money = Money.of(new BigDecimal("0.02"));
            assertThat(money.isEffectivelyZero()).isFalse();
        }
    }
}
