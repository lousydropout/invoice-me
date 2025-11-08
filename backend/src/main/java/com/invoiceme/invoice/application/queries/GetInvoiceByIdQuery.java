package com.invoiceme.invoice.application.queries;

import java.util.UUID;

/**
 * Query object for retrieving a single invoice by ID.
 */
public record GetInvoiceByIdQuery(UUID invoiceId) {
    public GetInvoiceByIdQuery {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
    }
}

