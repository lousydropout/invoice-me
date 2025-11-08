package com.invoiceme.invoice.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Invoice aggregate.
 * 
 * Defined in the domain layer to preserve DDD purity.
 * Infrastructure layer provides the implementation.
 */
public interface InvoiceRepository {
    /**
     * Finds an invoice by ID.
     * 
     * @param id the invoice ID
     * @return Optional containing the invoice if found
     */
    Optional<Invoice> findById(UUID id);

    /**
     * Saves an invoice aggregate.
     * 
     * @param invoice the invoice to save
     */
    void save(Invoice invoice);

    /**
     * Deletes an invoice by ID.
     * 
     * @param id the invoice ID
     */
    void delete(UUID id);
}

