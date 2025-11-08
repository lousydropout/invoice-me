package com.invoiceme.invoice.application.queries;

/**
 * Query object for listing overdue invoices.
 * Overdue = due_date < CURRENT_DATE AND status != 'PAID'
 */
public record ListOverdueInvoicesQuery() {
}

