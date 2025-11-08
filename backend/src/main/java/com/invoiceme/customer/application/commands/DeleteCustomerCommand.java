package com.invoiceme.customer.application.commands;

import java.util.UUID;

/**
 * Command to delete a customer.
 */
public record DeleteCustomerCommand(
    UUID customerId
) {}

