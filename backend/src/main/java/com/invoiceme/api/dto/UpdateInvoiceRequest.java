package com.invoiceme.api.dto;

import com.invoiceme.api.domain.InvoiceStatus;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.Optional;

public record UpdateInvoiceRequest(
        Optional<String> customerName,
        Optional<@DecimalMin(value = "0.01", message = "Amount must be positive") BigDecimal> amount,
        Optional<InvoiceStatus> status
) {
}

