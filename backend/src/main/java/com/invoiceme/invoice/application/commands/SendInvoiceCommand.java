package com.invoiceme.invoice.application.commands;

import java.util.UUID;

/**
 * Command to send an invoice (transitions from DRAFT to SENT status).
 */
public record SendInvoiceCommand(
    UUID invoiceId
) {}

