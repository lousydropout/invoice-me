package com.invoiceme.customer.application.commands;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.events.CustomerCreated;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.domain.DomainEvent;
import com.invoiceme.shared.test.FakeDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for CreateCustomerHandler.
 * Tests full persistence and event publishing with Testcontainers.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("CreateCustomerHandler Integration Tests")
@Transactional
@org.springframework.context.annotation.Import(CreateCustomerHandlerTest.TestConfig.class)
class CreateCustomerHandlerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("invoiceme_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Configuration
    static class TestConfig {
        @Bean
        @Primary
        public DomainEventPublisher fakeEventPublisher() {
            return new FakeDomainEventPublisher();
        }
    }

    @Autowired
    private CreateCustomerHandler handler;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DomainEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        // Clear events
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            fake.clear();
        }
    }

    @Test
    @DisplayName("Should create customer and persist to database")
    void shouldCreateCustomerAndPersistToDatabase() {
        // Given
        UUID customerId = UUID.randomUUID();
        CreateCustomerCommand command = new CreateCustomerCommand(
            customerId,
            "Test Customer",
            "test@example.com",
            "123 Main St",
            "City",
            "12345",
            "US",
            "555-1234",
            "NET_30"
        );

        // When
        UUID resultId = handler.handle(command);

        // Then
        assertThat(resultId).isEqualTo(customerId);
        
        // Verify customer is persisted
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new AssertionError("Customer should be persisted"));
        
        assertThat(customer.getId()).isEqualTo(customerId);
        assertThat(customer.getName()).isEqualTo("Test Customer");
        assertThat(customer.getEmail().getValue()).isEqualTo("test@example.com");
        assertThat(customer.getAddress().getStreet()).isEqualTo("123 Main St");
        assertThat(customer.getAddress().getCity()).isEqualTo("City");
        assertThat(customer.getAddress().getPostalCode()).isEqualTo("12345");
        assertThat(customer.getAddress().getCountry()).isEqualTo("US");
        assertThat(customer.getPhone()).isEqualTo("555-1234");
    }

    @Test
    @DisplayName("Should publish CustomerCreated event")
    void shouldPublishCustomerCreatedEvent() {
        // Given
        UUID customerId = UUID.randomUUID();
        CreateCustomerCommand command = new CreateCustomerCommand(
            customerId,
            "Test Customer",
            "test@example.com",
            "123 Main St",
            "City",
            "12345",
            "US",
            "555-1234",
            "NET_30"
        );

        // When
        handler.handle(command);

        // Then
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(CustomerCreated.class);
            
            CustomerCreated event = (CustomerCreated) events.get(0);
            assertThat(event.customerId()).isEqualTo(customerId);
            assertThat(event.name()).isEqualTo("Test Customer");
            assertThat(event.email()).isEqualTo("test@example.com");
        }
    }

    @Test
    @DisplayName("Should fail when email already exists")
    void shouldFailWhenEmailAlreadyExists() {
        // Given - create first customer
        UUID customerId1 = UUID.randomUUID();
        CreateCustomerCommand command1 = new CreateCustomerCommand(
            customerId1,
            "First Customer",
            "duplicate@example.com",
            "123 Main St",
            "City",
            "12345",
            "US",
            "555-1234",
            "NET_30"
        );
        handler.handle(command1);

        // When/Then - try to create second customer with same email
        UUID customerId2 = UUID.randomUUID();
        CreateCustomerCommand command2 = new CreateCustomerCommand(
            customerId2,
            "Second Customer",
            "duplicate@example.com",
            "456 Other St",
            "Other City",
            "67890",
            "US",
            "555-5678",
            "NET_15"
        );

        assertThatThrownBy(() -> handler.handle(command2))
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should verify database state after creation")
    void shouldVerifyDatabaseStateAfterCreation() {
        // Given
        UUID customerId = UUID.randomUUID();
        CreateCustomerCommand command = new CreateCustomerCommand(
            customerId,
            "Database Test Customer",
            "db-test@example.com",
            "789 Test St",
            "Test City",
            "99999",
            "CA",
            "555-9999",
            "NET_45"
        );

        // When
        handler.handle(command);

        // Then - verify database state
        Customer savedCustomer = customerRepository.findById(customerId)
            .orElseThrow(() -> new AssertionError("Customer should exist in database"));
        
        assertThat(savedCustomer.getName()).isEqualTo("Database Test Customer");
        assertThat(savedCustomer.getEmail().getValue()).isEqualTo("db-test@example.com");
        assertThat(savedCustomer.getAddress().getStreet()).isEqualTo("789 Test St");
        assertThat(savedCustomer.getAddress().getCity()).isEqualTo("Test City");
        assertThat(savedCustomer.getAddress().getPostalCode()).isEqualTo("99999");
        assertThat(savedCustomer.getAddress().getCountry()).isEqualTo("CA");
        assertThat(savedCustomer.getPhone()).isEqualTo("555-9999");
        
        // Verify customer can be found by email
        assertThat(customerRepository.existsByEmail("db-test@example.com")).isTrue();
    }
}

