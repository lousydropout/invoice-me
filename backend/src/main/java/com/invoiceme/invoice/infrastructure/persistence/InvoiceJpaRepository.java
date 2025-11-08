package com.invoiceme.invoice.infrastructure.persistence;

import com.invoiceme.invoice.infrastructure.persistence.entities.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for InvoiceEntity.
 */
@Repository
public interface InvoiceJpaRepository extends JpaRepository<InvoiceEntity, UUID> {
    
    /**
     * Finds an invoice by ID with line items eagerly loaded.
     */
    @Query("SELECT DISTINCT i FROM InvoiceEntity i LEFT JOIN FETCH i.lineItems WHERE i.id = :id")
    Optional<InvoiceEntity> findByIdWithLineItems(@Param("id") UUID id);
    
    /**
     * Finds an invoice by ID with payments eagerly loaded.
     */
    @Query("SELECT DISTINCT i FROM InvoiceEntity i LEFT JOIN FETCH i.payments WHERE i.id = :id")
    Optional<InvoiceEntity> findByIdWithPayments(@Param("id") UUID id);
}

