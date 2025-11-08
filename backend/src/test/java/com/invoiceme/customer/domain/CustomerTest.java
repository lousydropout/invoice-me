package com.invoiceme.customer.domain;

import com.invoiceme.customer.domain.events.CustomerCreated;
import com.invoiceme.customer.domain.events.CustomerDeleted;
import com.invoiceme.customer.domain.events.CustomerUpdated;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.DomainEvent;
import com.invoiceme.shared.domain.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Customer aggregate (Task 7.1).
 * 
 * Tests domain invariants, business rules, and event emission.
 */
@DisplayName("T7.1 - Customer Domain Tests")
class CustomerTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String CUSTOMER_NAME = "Test Customer";
    private static final Email CUSTOMER_EMAIL = Email.of("test@example.com");
    private static final Address CUSTOMER_ADDRESS = new Address("123 Main St", "City", "12345", "US");
    private static final String CUSTOMER_PHONE = "555-1234";
    private static final PaymentTerms PAYMENT_TERMS = PaymentTerms.net30();

    @Test
    @DisplayName("T7.1.1 - Create customer with valid data")
    void createCustomer_withValidData() {
        // When
        Customer customer = Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        );

        // Then
        assertThat(customer.getId()).isEqualTo(CUSTOMER_ID);
        assertThat(customer.getName()).isEqualTo(CUSTOMER_NAME);
        assertThat(customer.getEmail()).isEqualTo(CUSTOMER_EMAIL);
        assertThat(customer.getAddress()).isEqualTo(CUSTOMER_ADDRESS);
        assertThat(customer.getPhone()).isEqualTo(CUSTOMER_PHONE);
        assertThat(customer.getDefaultPaymentTerms()).isEqualTo(PAYMENT_TERMS);

        // Verify event
        List<DomainEvent> events = customer.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(CustomerCreated.class);
        CustomerCreated event = (CustomerCreated) events.get(0);
        assertThat(event.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(event.name()).isEqualTo(CUSTOMER_NAME);
        assertThat(event.email()).isEqualTo(CUSTOMER_EMAIL.getValue());
    }

    @Test
    @DisplayName("T7.1.2 - Prevent customer creation with null ID")
    void createCustomer_withNullId_throwsException() {
        // When/Then
        assertThatThrownBy(() -> Customer.create(
            null,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Customer ID cannot be null");
    }

    @Test
    @DisplayName("T7.1.3 - Prevent customer creation with null or empty name")
    void createCustomer_withNullOrEmptyName_throwsException() {
        // When/Then - null name
        assertThatThrownBy(() -> Customer.create(
            CUSTOMER_ID,
            null,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Customer name cannot be null or empty");

        // When/Then - empty name
        assertThatThrownBy(() -> Customer.create(
            CUSTOMER_ID,
            "",
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Customer name cannot be null or empty");

        // When/Then - blank name
        assertThatThrownBy(() -> Customer.create(
            CUSTOMER_ID,
            "   ",
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Customer name cannot be null or empty");
    }

    @Test
    @DisplayName("T7.1.4 - Prevent customer creation with null email")
    void createCustomer_withNullEmail_throwsException() {
        // When/Then
        assertThatThrownBy(() -> Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            null,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Customer email cannot be null");
    }

    @Test
    @DisplayName("T7.1.5 - Prevent customer creation with null address")
    void createCustomer_withNullAddress_throwsException() {
        // When/Then
        assertThatThrownBy(() -> Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            null,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Customer address cannot be null");
    }

    @Test
    @DisplayName("T7.1.6 - Prevent customer creation with null payment terms")
    void createCustomer_withNullPaymentTerms_throwsException() {
        // When/Then
        assertThatThrownBy(() -> Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Default payment terms cannot be null");
    }

    @Test
    @DisplayName("T7.1.7 - Update customer name")
    void updateCustomerName() {
        // Given
        Customer customer = Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        );
        customer.pullDomainEvents(); // Clear initial events

        // When
        String newName = "Updated Customer Name";
        customer.updateName(newName);

        // Then
        assertThat(customer.getName()).isEqualTo(newName);

        // Verify event
        List<DomainEvent> events = customer.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(CustomerUpdated.class);
    }

    @Test
    @DisplayName("T7.1.8 - Prevent updating customer name to null or empty")
    void updateCustomerName_withNullOrEmpty_throwsException() {
        // Given
        Customer customer = Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        );

        // When/Then - null name
        assertThatThrownBy(() -> customer.updateName(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Customer name cannot be null or empty");

        // When/Then - empty name
        assertThatThrownBy(() -> customer.updateName(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Customer name cannot be null or empty");
    }

    @Test
    @DisplayName("T7.1.9 - Update customer contact information")
    void updateCustomerContactInfo() {
        // Given
        Customer customer = Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        );
        customer.pullDomainEvents(); // Clear initial events

        Address newAddress = new Address("456 Oak Ave", "New City", "67890", "US");
        Email newEmail = Email.of("newemail@example.com");
        String newPhone = "555-5678";

        // When
        customer.updateContactInfo(newAddress, newEmail, newPhone);

        // Then
        assertThat(customer.getAddress()).isEqualTo(newAddress);
        assertThat(customer.getEmail()).isEqualTo(newEmail);
        assertThat(customer.getPhone()).isEqualTo(newPhone);

        // Verify event
        List<DomainEvent> events = customer.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(CustomerUpdated.class);
    }

    @Test
    @DisplayName("T7.1.10 - Prevent updating contact info with null address")
    void updateContactInfo_withNullAddress_throwsException() {
        // Given
        Customer customer = Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        );

        // When/Then
        assertThatThrownBy(() -> customer.updateContactInfo(
            null,
            CUSTOMER_EMAIL,
            CUSTOMER_PHONE
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Address cannot be null");
    }

    @Test
    @DisplayName("T7.1.11 - Prevent updating contact info with null email")
    void updateContactInfo_withNullEmail_throwsException() {
        // Given
        Customer customer = Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        );

        // When/Then
        assertThatThrownBy(() -> customer.updateContactInfo(
            CUSTOMER_ADDRESS,
            null,
            CUSTOMER_PHONE
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Email cannot be null");
    }

    @Test
    @DisplayName("T7.1.12 - Set default payment terms")
    void setDefaultPaymentTerms() {
        // Given
        Customer customer = Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        );
        customer.pullDomainEvents(); // Clear initial events

        PaymentTerms newTerms = PaymentTerms.net15();

        // When
        customer.setDefaultPaymentTerms(newTerms);

        // Then
        assertThat(customer.getDefaultPaymentTerms()).isEqualTo(newTerms);

        // Verify event
        List<DomainEvent> events = customer.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(CustomerUpdated.class);
    }

    @Test
    @DisplayName("T7.1.13 - Prevent setting null payment terms")
    void setDefaultPaymentTerms_withNull_throwsException() {
        // Given
        Customer customer = Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        );

        // When/Then
        assertThatThrownBy(() -> customer.setDefaultPaymentTerms(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Payment terms cannot be null");
    }

    @Test
    @DisplayName("T7.1.14 - Delete customer")
    void deleteCustomer() {
        // Given
        Customer customer = Customer.create(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        );
        customer.pullDomainEvents(); // Clear initial events

        // When
        customer.delete();

        // Then - verify event
        List<DomainEvent> events = customer.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(CustomerDeleted.class);
        CustomerDeleted event = (CustomerDeleted) events.get(0);
        assertThat(event.customerId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    @DisplayName("T7.1.15 - Reconstruct customer without events")
    void reconstructCustomer_doesNotEmitEvents() {
        // When
        Customer customer = Customer.reconstruct(
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_EMAIL,
            CUSTOMER_ADDRESS,
            CUSTOMER_PHONE,
            PAYMENT_TERMS
        );

        // Then
        assertThat(customer.getId()).isEqualTo(CUSTOMER_ID);
        assertThat(customer.getName()).isEqualTo(CUSTOMER_NAME);
        
        // Verify no events
        List<DomainEvent> events = customer.pullDomainEvents();
        assertThat(events).isEmpty();
    }
}

