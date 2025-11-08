package com.invoiceme.customer.infrastructure.persistence.mappers;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.customer.infrastructure.persistence.entities.CustomerEntity;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Customer domain aggregate and CustomerEntity.
 */
@Component
public class CustomerMapper {

    /**
     * Converts a Customer domain aggregate to a CustomerEntity.
     * 
     * @param customer the domain customer
     * @return the JPA entity
     */
    public CustomerEntity toEntity(Customer customer) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(customer.getId());
        entity.setName(customer.getName());
        entity.setEmail(customer.getEmail().getValue());
        entity.setPhone(customer.getPhone());
        
        Address address = customer.getAddress();
        if (address != null) {
            entity.setStreet(address.getStreet());
            entity.setCity(address.getCity());
            entity.setPostalCode(address.getPostalCode());
            entity.setCountry(address.getCountry());
        }
        
        entity.setPaymentTerms(customer.getDefaultPaymentTerms().getValue());
        
        return entity;
    }

    /**
     * Converts a CustomerEntity to a Customer domain aggregate.
     * Uses reconstruct() to avoid emitting events when loading from database.
     * 
     * @param entity the JPA entity
     * @return the domain customer
     */
    public Customer toDomain(CustomerEntity entity) {
        Address address = new Address(
            entity.getStreet(),
            entity.getCity(),
            entity.getPostalCode(),
            entity.getCountry()
        );

        return Customer.reconstruct(
            entity.getId(),
            entity.getName(),
            Email.of(entity.getEmail()),
            address,
            entity.getPhone(),
            PaymentTerms.of(entity.getPaymentTerms())
        );
    }
}

