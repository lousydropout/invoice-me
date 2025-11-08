package com.invoiceme.invoice.domain;

import com.invoiceme.shared.domain.Money;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a payment applied to an invoice.
 * 
 * Owned by the Invoice aggregate root.
 */
public class Payment {
    private final UUID id;
    private final Money amount;
    private final LocalDate paymentDate;
    private final String method;
    private final String reference;

    public Payment(UUID id, Money amount, LocalDate paymentDate, String method, String reference) {
        if (id == null) {
            throw new IllegalArgumentException("Payment ID cannot be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Payment amount cannot be null");
        }
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (paymentDate == null) {
            throw new IllegalArgumentException("Payment date cannot be null");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Payment method cannot be null or blank");
        }
        
        this.id = id;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.method = method;
        this.reference = reference;
    }

    public UUID getId() {
        return id;
    }

    public Money getAmount() {
        return amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public String getMethod() {
        return method;
    }

    public String getReference() {
        return reference;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return Objects.equals(id, payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Payment{id=%s, amount=%s, paymentDate=%s, method='%s', reference='%s'}",
            id, amount, paymentDate, method, reference);
    }
}

