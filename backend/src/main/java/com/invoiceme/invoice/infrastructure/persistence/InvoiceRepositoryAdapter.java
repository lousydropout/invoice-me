package com.invoiceme.invoice.infrastructure.persistence;

import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.infrastructure.persistence.mappers.InvoiceMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository adapter that implements the domain InvoiceRepository interface.
 * 
 * Bridges the domain layer and Spring Data JPA infrastructure.
 */
@Repository
public class InvoiceRepositoryAdapter implements InvoiceRepository {
    private final InvoiceJpaRepository jpaRepository;
    private final InvoiceMapper mapper;

    public InvoiceRepositoryAdapter(InvoiceJpaRepository jpaRepository, InvoiceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return jpaRepository.findById(id)
            .map(mapper::toDomain);
    }

    @Override
    public void save(Invoice invoice) {
        var existingEntity = jpaRepository.findById(invoice.getId());
        
        if (existingEntity.isPresent()) {
            // Update existing entity
            var entity = existingEntity.get();
            mapper.updateEntity(entity, invoice);
            jpaRepository.save(entity);
        } else {
            // Create new entity
            var entity = mapper.toEntity(invoice);
            jpaRepository.save(entity);
        }
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }
}

