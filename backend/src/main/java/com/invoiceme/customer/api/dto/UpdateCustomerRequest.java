package com.invoiceme.customer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating a customer.
 */
public record UpdateCustomerRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    String phone,

    @NotNull(message = "Address is required")
    @Valid
    AddressDto address,

    @NotBlank(message = "Payment terms are required")
    String paymentTerms
) {
    public record AddressDto(
        String street,
        String city,
        @JsonProperty("postalCode")
        String postalCode,
        String country
    ) {}

    /**
     * Converts this DTO to an UpdateCustomerCommand.
     * 
     * @param customerId the customer ID
     * @return UpdateCustomerCommand
     */
    public com.invoiceme.customer.application.commands.UpdateCustomerCommand toCommand(java.util.UUID customerId) {
        return new com.invoiceme.customer.application.commands.UpdateCustomerCommand(
            customerId,
            name,
            email,
            address != null ? address.street() : null,
            address != null ? address.city() : null,
            address != null ? address.postalCode() : null,
            address != null ? address.country() : null,
            phone,
            paymentTerms
        );
    }
}

