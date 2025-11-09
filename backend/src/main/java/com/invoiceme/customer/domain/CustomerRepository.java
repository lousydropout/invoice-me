package com.invoiceme.customer.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Customer aggregate.
 * 
 * Defined in the domain layer to preserve DDD purity.
 * Infrastructure layer provides the implementation.
 */
public interface CustomerRepository {
    /**
     * Finds a customer by ID.
     * 
     * @param id the customer ID
     * @return Optional containing the customer if found
     */
    Optional<Customer> findById(UUID id);

    /**
     * Saves a customer aggregate.
     * 
     * @param customer the customer to save
     */
    void save(Customer customer);

    /**
     * Deletes a customer by ID.
     * 
     * @param id the customer ID
     */
    void delete(UUID id);

    /**
     * Checks if a customer exists with the given email.
     * 
     * @param email the email address
     * @return true if a customer with this email exists
     */
    boolean existsByEmail(String email);

    /**
     * Finds all customers.
     * 
     * @return List of all customers
     */
    List<Customer> findAll();
}

