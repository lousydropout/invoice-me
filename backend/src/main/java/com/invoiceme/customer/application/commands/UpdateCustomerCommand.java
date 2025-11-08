package com.invoiceme.customer.application.commands;

import java.util.UUID;

/**
 * Command to update an existing customer.
 */
public record UpdateCustomerCommand(
    UUID customerId,
    String name,
    String email,
    String street,
    String city,
    String postalCode,
    String country,
    String phone,
    String defaultPaymentTerms
) {}

