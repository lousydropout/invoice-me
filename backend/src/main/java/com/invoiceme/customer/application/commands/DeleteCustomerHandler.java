package com.invoiceme.customer.application.commands;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.application.errors.ApplicationError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for deleting a customer.
 */
@Service
public class DeleteCustomerHandler {
    private final CustomerRepository customerRepository;
    private final DomainEventPublisher eventPublisher;

    public DeleteCustomerHandler(CustomerRepository customerRepository, DomainEventPublisher eventPublisher) {
        this.customerRepository = customerRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(DeleteCustomerCommand command) {
        // Load customer
        Customer customer = customerRepository.findById(command.customerId())
            .orElseThrow(() -> ApplicationError.notFound("Customer"));

        // Mark as deleted (emits CustomerDeleted event)
        customer.delete();

        // Save and publish events
        customerRepository.save(customer);
        eventPublisher.publish(customer.pullDomainEvents());

        // Actually delete from repository
        customerRepository.delete(command.customerId());
    }
}

