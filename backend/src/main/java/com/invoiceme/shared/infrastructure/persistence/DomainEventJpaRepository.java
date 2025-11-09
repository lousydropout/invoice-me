package com.invoiceme.shared.infrastructure.persistence;

import com.invoiceme.shared.infrastructure.persistence.entities.DomainEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for domain events.
 */
@Repository
public interface DomainEventJpaRepository extends JpaRepository<DomainEventEntity, UUID> {
    
    /**
     * Find all events ordered by creation date (newest first).
     */
    List<DomainEventEntity> findAllByOrderByCreatedAtDesc();
    
    /**
     * Find events by type, ordered by creation date (newest first).
     */
    List<DomainEventEntity> findByTypeOrderByCreatedAtDesc(String type);
}

