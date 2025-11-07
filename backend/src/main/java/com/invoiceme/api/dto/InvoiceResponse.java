package com.invoiceme.api.dto;

import com.invoiceme.api.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String customerName,
        BigDecimal amount,
        InvoiceStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}

