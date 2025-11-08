package com.invoiceme.customer.infrastructure.persistence;

import com.invoiceme.customer.infrastructure.persistence.entities.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for CustomerEntity.
 */
@Repository
public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {
    /**
     * Checks if a customer exists with the given email.
     * 
     * @param email the email address
     * @return true if a customer with this email exists
     */
    boolean existsByEmail(String email);
}

