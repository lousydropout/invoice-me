package com.invoiceme.invoice.domain;

import com.invoiceme.shared.domain.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representing a line item on an invoice.
 * 
 * Immutable and equality by value.
 */
public final class LineItem {
    private final String description;
    private final BigDecimal quantity;
    private final Money unitPrice;
    private final Money subtotal;

    private LineItem(String description, BigDecimal quantity, Money unitPrice) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Line item description cannot be null or blank");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Line item quantity must be positive");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("Line item unit price cannot be null");
        }
        if (unitPrice.isNegative() || unitPrice.isZero()) {
            throw new IllegalArgumentException("Line item unit price must be positive");
        }
        
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(quantity);
    }

    /**
     * Creates a new LineItem.
     * 
     * @param description the item description
     * @param quantity the quantity
     * @param unitPrice the unit price
     * @return a new LineItem instance
     */
    public static LineItem of(String description, BigDecimal quantity, Money unitPrice) {
        return new LineItem(description, quantity, unitPrice);
    }

    /**
     * Creates a new LineItem with double quantity for convenience.
     * 
     * @param description the item description
     * @param quantity the quantity
     * @param unitPrice the unit price
     * @return a new LineItem instance
     */
    public static LineItem of(String description, double quantity, Money unitPrice) {
        return new LineItem(description, BigDecimal.valueOf(quantity), unitPrice);
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public Money getSubtotal() {
        return subtotal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LineItem lineItem = (LineItem) o;
        return Objects.equals(description, lineItem.description) &&
               Objects.equals(quantity, lineItem.quantity) &&
               Objects.equals(unitPrice, lineItem.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, quantity, unitPrice);
    }

    @Override
    public String toString() {
        return String.format("LineItem{description='%s', quantity=%s, unitPrice=%s, subtotal=%s}",
            description, quantity, unitPrice, subtotal);
    }
}

