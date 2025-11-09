package com.invoiceme.customer.api;

import com.invoiceme.customer.api.dto.CreateCustomerRequest;
import com.invoiceme.customer.api.dto.CustomerResponse;
import com.invoiceme.customer.api.dto.UpdateCustomerRequest;
import com.invoiceme.customer.application.commands.CreateCustomerHandler;
import com.invoiceme.customer.application.commands.DeleteCustomerHandler;
import com.invoiceme.customer.application.commands.UpdateCustomerHandler;
import com.invoiceme.customer.application.queries.OutstandingByCustomerHandler;
import com.invoiceme.customer.application.queries.OutstandingByCustomerQuery;
import com.invoiceme.customer.application.queries.dto.CustomerView;
import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Customer resources.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CreateCustomerHandler createHandler;
    private final UpdateCustomerHandler updateHandler;
    private final DeleteCustomerHandler deleteHandler;
    private final CustomerRepository customerRepository;
    private final OutstandingByCustomerHandler outstandingByCustomerHandler;

    public CustomerController(
        CreateCustomerHandler createHandler,
        UpdateCustomerHandler updateHandler,
        DeleteCustomerHandler deleteHandler,
        CustomerRepository customerRepository,
        OutstandingByCustomerHandler outstandingByCustomerHandler
    ) {
        this.createHandler = createHandler;
        this.updateHandler = updateHandler;
        this.deleteHandler = deleteHandler;
        this.customerRepository = customerRepository;
        this.outstandingByCustomerHandler = outstandingByCustomerHandler;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@RequestBody @Valid CreateCustomerRequest request) {
        UUID id = UUID.randomUUID();
        createHandler.handle(request.toCommand(id));
        
        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Customer not found after creation"));
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CustomerResponse.from(customer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
        @PathVariable UUID id,
        @RequestBody @Valid UpdateCustomerRequest request
    ) {
        updateHandler.handle(request.toCommand(id));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteHandler.handle(new com.invoiceme.customer.application.commands.DeleteCustomerCommand(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> listAll() {
        List<CustomerResponse> customers = customerRepository.findAll().stream()
            .map(CustomerResponse::from)
            .toList();
        return ResponseEntity.ok(customers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(@PathVariable UUID id) {
        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> com.invoiceme.shared.application.errors.ApplicationError.notFound("Customer"));
        
        return ResponseEntity.ok(CustomerResponse.from(customer));
    }

    @GetMapping("/outstanding")
    public ResponseEntity<List<CustomerView>> getOutstanding() {
        List<CustomerView> outstanding = outstandingByCustomerHandler.handle(new OutstandingByCustomerQuery());
        return ResponseEntity.ok(outstanding);
    }
}

