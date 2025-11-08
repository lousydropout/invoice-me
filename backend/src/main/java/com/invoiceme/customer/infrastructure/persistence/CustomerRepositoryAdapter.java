package com.invoiceme.customer.infrastructure.persistence;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.infrastructure.persistence.mappers.CustomerMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository adapter that implements the domain CustomerRepository interface.
 * 
 * Bridges the domain layer and Spring Data JPA infrastructure.
 */
@Repository
public class CustomerRepositoryAdapter implements CustomerRepository {
    private final CustomerJpaRepository jpaRepository;
    private final CustomerMapper mapper;

    public CustomerRepositoryAdapter(CustomerJpaRepository jpaRepository, CustomerMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return jpaRepository.findById(id)
            .map(mapper::toDomain);
    }

    @Override
    public void save(Customer customer) {
        var existingEntity = jpaRepository.findById(customer.getId());
        
        if (existingEntity.isPresent()) {
            // Update existing entity
            var entity = existingEntity.get();
            entity.setName(customer.getName());
            entity.setEmail(customer.getEmail().getValue());
            entity.setPhone(customer.getPhone());
            
            var address = customer.getAddress();
            if (address != null) {
                entity.setStreet(address.getStreet());
                entity.setCity(address.getCity());
                entity.setPostalCode(address.getPostalCode());
                entity.setCountry(address.getCountry());
            }
            
            entity.setPaymentTerms(customer.getDefaultPaymentTerms().getValue());
            jpaRepository.save(entity);
        } else {
            // Create new entity
            var entity = mapper.toEntity(customer);
            jpaRepository.save(entity);
        }
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }
}

