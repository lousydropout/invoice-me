package com.invoiceme.customer.domain;

import com.invoiceme.customer.domain.events.CustomerCreated;
import com.invoiceme.customer.domain.events.CustomerDeleted;
import com.invoiceme.customer.domain.events.CustomerUpdated;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.DomainEvent;
import com.invoiceme.shared.domain.Email;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Customer aggregate root.
 * 
 * Represents the entity being billed — the client or organization that receives invoices.
 * 
 * Invariants:
 * - Email must be valid (enforced by Email value object)
 * - Name cannot be empty
 */
public class Customer {
    private final UUID id;
    private String name;
    private Email email;
    private Address address;
    private String phone;
    private PaymentTerms defaultPaymentTerms;
    
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Customer(UUID id, String name, Email email, Address address, String phone, PaymentTerms defaultPaymentTerms) {
        if (id == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be null or empty");
        }
        if (email == null) {
            throw new IllegalArgumentException("Customer email cannot be null");
        }
        if (address == null) {
            throw new IllegalArgumentException("Customer address cannot be null");
        }
        if (defaultPaymentTerms == null) {
            throw new IllegalArgumentException("Default payment terms cannot be null");
        }
        
        this.id = id;
        this.name = name;
        this.email = email;
        this.address = address;
        this.phone = phone;
        this.defaultPaymentTerms = defaultPaymentTerms;
    }

    /**
     * Creates a new Customer aggregate.
     * Emits CustomerCreated event.
     * 
     * @param id the customer ID
     * @param name the customer name
     * @param email the customer email
     * @param address the customer address
     * @param phone the customer phone (optional)
     * @param defaultPaymentTerms the default payment terms
     * @return a new Customer instance
     */
    public static Customer create(UUID id, String name, Email email, Address address, String phone, PaymentTerms defaultPaymentTerms) {
        Customer customer = new Customer(id, name, email, address, phone, defaultPaymentTerms);
        customer.domainEvents.add(new CustomerCreated(id, name, email.getValue(), Instant.now()));
        return customer;
    }

    /**
     * Reconstructs a Customer aggregate from existing data (e.g., from database).
     * Does NOT emit events - used for loading existing aggregates.
     * 
     * @param id the customer ID
     * @param name the customer name
     * @param email the customer email
     * @param address the customer address
     * @param phone the customer phone (optional)
     * @param defaultPaymentTerms the default payment terms
     * @return a Customer instance without events
     */
    public static Customer reconstruct(UUID id, String name, Email email, Address address, String phone, PaymentTerms defaultPaymentTerms) {
        return new Customer(id, name, email, address, phone, defaultPaymentTerms);
    }

    /**
     * Updates customer name.
     * 
     * @param name the new name
     */
    public void updateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be null or empty");
        }
        
        this.name = name;
        this.domainEvents.add(new CustomerUpdated(id, Instant.now()));
    }

    /**
     * Updates customer contact information.
     * 
     * @param address the new address
     * @param email the new email
     * @param phone the new phone
     */
    public void updateContactInfo(Address address, Email email, String phone) {
        if (address == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("Email cannot be null");
        }
        
        this.address = address;
        this.email = email;
        this.phone = phone;
        
        this.domainEvents.add(new CustomerUpdated(id, Instant.now()));
    }

    /**
     * Sets the default payment terms.
     * 
     * @param paymentTerms the new payment terms
     */
    public void setDefaultPaymentTerms(PaymentTerms paymentTerms) {
        if (paymentTerms == null) {
            throw new IllegalArgumentException("Payment terms cannot be null");
        }
        
        this.defaultPaymentTerms = paymentTerms;
        this.domainEvents.add(new CustomerUpdated(id, Instant.now()));
    }

    /**
     * Marks the customer as deleted.
     * Emits CustomerDeleted event.
     */
    public void delete() {
        this.domainEvents.add(new CustomerDeleted(id, Instant.now()));
    }

    /**
     * Pulls and clears all domain events raised by this aggregate.
     * 
     * @return a list of domain events
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Email getEmail() {
        return email;
    }

    public Address getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public PaymentTerms getDefaultPaymentTerms() {
        return defaultPaymentTerms;
    }
}

