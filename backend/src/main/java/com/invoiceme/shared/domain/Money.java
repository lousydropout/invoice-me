package com.invoiceme.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value object representing monetary amounts.
 * 
 * Encapsulates amount and currency with safe arithmetic operations.
 * Immutable and equality by value.
 */
public final class Money {
    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("USD");
    private static final int DEFAULT_SCALE = 2;
    
    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        if (amount.scale() > DEFAULT_SCALE) {
            this.amount = amount.setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);
        } else {
            this.amount = amount;
        }
        this.currency = currency;
    }

    /**
     * Creates a Money instance with the specified amount and default currency (USD).
     * 
     * @param amount the monetary amount
     * @return a Money instance
     */
    public static Money of(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    /**
     * Creates a Money instance with the specified amount and currency.
     * 
     * @param amount the monetary amount
     * @param currency the currency
     * @return a Money instance
     */
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /**
     * Creates a Money instance from a double amount (for convenience).
     * 
     * @param amount the monetary amount
     * @return a Money instance
     */
    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount), DEFAULT_CURRENCY);
    }

    /**
     * Returns zero money with default currency.
     * 
     * @return zero Money
     */
    public static Money zero() {
        return new Money(BigDecimal.ZERO, DEFAULT_CURRENCY);
    }

    /**
     * Adds another Money amount to this Money.
     * 
     * @param other the Money to add
     * @return a new Money instance with the sum
     * @throws IllegalArgumentException if currencies don't match
     */
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add money with different currencies");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtracts another Money amount from this Money.
     * 
     * @param other the Money to subtract
     * @return a new Money instance with the difference
     * @throws IllegalArgumentException if currencies don't match
     */
    public Money subtract(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot subtract money with different currencies");
        }
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /**
     * Multiplies this Money by a multiplier.
     * 
     * @param multiplier the multiplier
     * @return a new Money instance with the product
     */
    public Money multiply(BigDecimal multiplier) {
        return new Money(this.amount.multiply(multiplier), this.currency);
    }

    /**
     * Multiplies this Money by a double multiplier.
     * 
     * @param multiplier the multiplier
     * @return a new Money instance with the product
     */
    public Money multiply(double multiplier) {
        return multiply(BigDecimal.valueOf(multiplier));
    }

    /**
     * Checks if this Money is greater than another Money.
     * 
     * @param other the Money to compare
     * @return true if this Money is greater
     * @throws IllegalArgumentException if currencies don't match
     */
    public boolean isGreaterThan(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot compare money with different currencies");
        }
        return this.amount.compareTo(other.amount) > 0;
    }

    /**
     * Checks if this Money is less than another Money.
     * 
     * @param other the Money to compare
     * @return true if this Money is less
     * @throws IllegalArgumentException if currencies don't match
     */
    public boolean isLessThan(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot compare money with different currencies");
        }
        return this.amount.compareTo(other.amount) < 0;
    }

    /**
     * Checks if this Money is zero.
     *
     * @return true if the amount is zero
     */
    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Checks if this Money is effectively zero (less than one cent).
     * Used for determining if an invoice is fully paid, accounting for
     * overpayments and tiny rounding differences.
     *
     * @return true if the amount is less than 0.01
     */
    public boolean isEffectivelyZero() {
        return this.amount.compareTo(new BigDecimal("0.01")) < 0;
    }

    /**
     * Checks if this Money is negative.
     * 
     * @return true if the amount is negative
     */
    public boolean isNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount) && Objects.equals(currency, money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return currency.getSymbol() + amount;
    }
}

