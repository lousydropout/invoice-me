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

/**
 * Handler for updating an existing customer.
 */
@Service
public class UpdateCustomerHandler {
    private final CustomerRepository customerRepository;
    private final DomainEventPublisher eventPublisher;

    public UpdateCustomerHandler(CustomerRepository customerRepository, DomainEventPublisher eventPublisher) {
        this.customerRepository = customerRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(UpdateCustomerCommand command) {
        // Load customer
        Customer customer = customerRepository.findById(command.customerId())
            .orElseThrow(() -> ApplicationError.notFound("Customer"));

        // Validate email uniqueness if email changed
        if (!customer.getEmail().getValue().equals(command.email())) {
            if (customerRepository.existsByEmail(command.email())) {
                throw ApplicationError.conflict("Customer with email " + command.email() + " already exists");
            }
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

        // Update customer
        customer.updateContactInfo(address, email, command.phone());
        customer.setDefaultPaymentTerms(paymentTerms);

        // Save and publish events
        customerRepository.save(customer);
        eventPublisher.publish(customer.pullDomainEvents());
    }
}

