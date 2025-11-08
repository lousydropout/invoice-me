package com.invoiceme.customer.api.dto;

import java.util.UUID;

/**
 * Response DTO for customer data.
 */
public record CustomerResponse(
    UUID id,
    String name,
    String email,
    String phone,
    String street,
    String city,
    String postalCode,
    String country,
    String paymentTerms
) {
    /**
     * Creates a CustomerResponse from a Customer domain aggregate.
     * 
     * @param customer the customer domain aggregate
     * @return CustomerResponse
     */
    public static CustomerResponse from(com.invoiceme.customer.domain.Customer customer) {
        return new CustomerResponse(
            customer.getId(),
            customer.getName(),
            customer.getEmail().getValue(),
            customer.getPhone(),
            customer.getAddress().getStreet(),
            customer.getAddress().getCity(),
            customer.getAddress().getPostalCode(),
            customer.getAddress().getCountry(),
            customer.getDefaultPaymentTerms().getValue()
        );
    }
}

