package com.invoiceme.invoice.infrastructure.persistence;

import com.invoiceme.invoice.infrastructure.persistence.entities.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for InvoiceEntity.
 */
@Repository
public interface InvoiceJpaRepository extends JpaRepository<InvoiceEntity, UUID> {
}

