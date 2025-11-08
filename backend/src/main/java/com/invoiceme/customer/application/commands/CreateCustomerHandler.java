package com.invoiceme.customer.application.commands;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.application.errors.ApplicationError;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handler for creating a new customer.
 */
@Service
public class CreateCustomerHandler {
    private final CustomerRepository customerRepository;
    private final DomainEventPublisher eventPublisher;

    public CreateCustomerHandler(CustomerRepository customerRepository, DomainEventPublisher eventPublisher) {
        this.customerRepository = customerRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UUID handle(CreateCustomerCommand command) {
        // Validate email uniqueness
        if (customerRepository.existsByEmail(command.email())) {
            throw ApplicationError.conflict("Customer with email " + command.email() + " already exists");
        }

        // Create value objects
        Email email = Email.of(command.email());
        Address address = new Address(
            command.street(),
            command.city(),
            command.postalCode(),
            command.country()
        );
        PaymentTerms paymentTerms = PaymentTerms.of(command.defaultPaymentTerms());

        // Create customer aggregate
        Customer customer = Customer.create(
            command.id(),
            command.name(),
            email,
            address,
            command.phone(),
            paymentTerms
        );

        // Save and publish events
        customerRepository.save(customer);
        eventPublisher.publish(customer.pullDomainEvents());

        return customer.getId();
    }
}

