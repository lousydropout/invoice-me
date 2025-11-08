package com.invoiceme.invoice.domain.valueobjects;

/**
 * Value object representing invoice status.
 * 
 * Lifecycle: DRAFT → SENT → PAID
 */
public enum InvoiceStatus {
    DRAFT,
    SENT,
    PAID
}

