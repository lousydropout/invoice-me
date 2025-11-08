package com.invoiceme.customer.domain.valueobjects;

import java.util.Objects;

/**
 * Value object representing payment terms.
 * 
 * Examples: "NET_15", "NET_30", "DUE_ON_RECEIPT"
 * Immutable and equality by value.
 */
public final class PaymentTerms {
    private final String value;

    private PaymentTerms(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payment terms cannot be null or blank");
        }
        this.value = value.trim().toUpperCase();
    }

    /**
     * Creates PaymentTerms from a string.
     * 
     * @param value the payment terms string (e.g., "NET_30", "DUE_ON_RECEIPT")
     * @return PaymentTerms instance
     */
    public static PaymentTerms of(String value) {
        return new PaymentTerms(value);
    }

    /**
     * Creates standard NET_15 payment terms.
     * 
     * @return PaymentTerms for NET_15
     */
    public static PaymentTerms net15() {
        return new PaymentTerms("NET_15");
    }

    /**
     * Creates standard NET_30 payment terms.
     * 
     * @return PaymentTerms for NET_30
     */
    public static PaymentTerms net30() {
        return new PaymentTerms("NET_30");
    }

    /**
     * Creates DUE_ON_RECEIPT payment terms.
     * 
     * @return PaymentTerms for DUE_ON_RECEIPT
     */
    public static PaymentTerms dueOnReceipt() {
        return new PaymentTerms("DUE_ON_RECEIPT");
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentTerms that = (PaymentTerms) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

