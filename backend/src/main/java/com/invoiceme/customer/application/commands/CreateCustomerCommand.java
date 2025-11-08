package com.invoiceme.customer.application.commands;

import java.util.UUID;

/**
 * Command to create a new customer.
 */
public record CreateCustomerCommand(
    UUID id,
    String name,
    String email,
    String street,
    String city,
    String postalCode,
    String country,
    String phone,
    String defaultPaymentTerms
) {}

