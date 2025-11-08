package com.invoiceme.invoice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for updating an invoice.
 */
public record UpdateInvoiceRequest(
    @NotEmpty(message = "At least one line item is required")
    @Valid
    List<CreateInvoiceRequest.LineItemDto> lineItems,

    @NotNull(message = "Due date is required")
    LocalDate dueDate,

    @NotNull(message = "Tax rate is required")
    @PositiveOrZero(message = "Tax rate must be positive or zero")
    BigDecimal taxRate,

    String notes
) {
    /**
     * Converts this DTO to an UpdateInvoiceCommand.
     *
     * @param invoiceId the invoice ID
     * @return UpdateInvoiceCommand
     */
    public com.invoiceme.invoice.application.commands.UpdateInvoiceCommand toCommand(java.util.UUID invoiceId) {
        List<com.invoiceme.invoice.application.commands.CreateInvoiceCommand.LineItemDto> commandLineItems =
            lineItems.stream()
                .map(li -> new com.invoiceme.invoice.application.commands.CreateInvoiceCommand.LineItemDto(
                    li.description(),
                    li.quantity(),
                    li.unitPriceAmount(),
                    li.unitPriceCurrency()
                ))
                .toList();

        return new com.invoiceme.invoice.application.commands.UpdateInvoiceCommand(
            invoiceId,
            commandLineItems,
            dueDate,
            taxRate,
            notes
        );
    }
}

