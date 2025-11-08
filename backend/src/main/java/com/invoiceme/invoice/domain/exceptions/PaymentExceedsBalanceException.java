package com.invoiceme.invoice.domain.exceptions;

/**
 * Domain exception thrown when a payment amount exceeds the outstanding balance of an invoice.
 */
public class PaymentExceedsBalanceException extends RuntimeException {
    public PaymentExceedsBalanceException(String message) {
        super(message);
    }
}

