package com.invoiceme.invoice.domain.valueobjects;

import java.time.Year;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Value object representing an invoice number.
 * 
 * Format: INV-YYYY-#### (e.g., INV-2024-0001)
 * Immutable and equality by value.
 */
public final class InvoiceNumber {
    private static final AtomicInteger sequence = new AtomicInteger(1);
    private final String value;

    private InvoiceNumber(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invoice number cannot be null or blank");
        }
        this.value = value;
    }

    /**
     * Generates a new invoice number with format INV-YYYY-####.
     * 
     * @return a new InvoiceNumber instance
     */
    public static InvoiceNumber generate() {
        int year = Year.now().getValue();
        int seq = sequence.getAndIncrement();
        String formatted = String.format("INV-%d-%04d", year, seq);
        return new InvoiceNumber(formatted);
    }

    /**
     * Creates an InvoiceNumber from an existing string value.
     * 
     * @param value the invoice number string
     * @return an InvoiceNumber instance
     */
    public static InvoiceNumber of(String value) {
        return new InvoiceNumber(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceNumber that = (InvoiceNumber) o;
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

