package com.invoiceme.customer.api;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration tests for Customer REST API endpoints.
 * Tests all CRUD operations: Create, Read, Update, Delete, and List.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Customer API Integration Tests")
@Transactional
class CustomerApiIntegrationTest {

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
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    @DisplayName("GET /api/customers - List all customers returns empty list when no customers")
    void listAllCustomers_empty() throws Exception {
        mockMvc.perform(get("/api/customers")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/customers - List all customers returns all customers")
    void listAllCustomers() throws Exception {
        // Given - create multiple customers
        UUID customerId1 = UUID.randomUUID();
        Customer customer1 = Customer.create(
            customerId1,
            "Customer A",
            Email.of("customerA@example.com"),
            new Address("123 Main St", "City A", "12345", "US"),
            "555-1234",
            PaymentTerms.net30()
        );
        customerRepository.save(customer1);

        UUID customerId2 = UUID.randomUUID();
        Customer customer2 = Customer.create(
            customerId2,
            "Customer B",
            Email.of("customerB@example.com"),
            new Address("456 Oak Ave", "City B", "67890", "US"),
            "555-5678",
            PaymentTerms.of("NET_45")
        );
        customerRepository.save(customer2);

        // When/Then
        mockMvc.perform(get("/api/customers")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isNotEmpty())
            .andExpect(jsonPath("$[?(@.id == '%s')].name", customerId1.toString()).value("Customer A"))
            .andExpect(jsonPath("$[?(@.id == '%s')].email", customerId1.toString()).value("customera@example.com"))
            .andExpect(jsonPath("$[?(@.id == '%s')].name", customerId2.toString()).value("Customer B"))
            .andExpect(jsonPath("$[?(@.id == '%s')].email", customerId2.toString()).value("customerb@example.com"));
    }

    @Test
    @DisplayName("GET /api/customers/{id} - Get customer by ID returns customer")
    void getCustomerById() throws Exception {
        // Given
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.create(
            customerId,
            "Test Customer",
            Email.of("test@example.com"),
            new Address("123 Main St", "City", "12345", "US"),
            "555-1234",
            PaymentTerms.net30()
        );
        customerRepository.save(customer);

        // When/Then
        mockMvc.perform(get("/api/customers/{id}", customerId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(customerId.toString()))
            .andExpect(jsonPath("$.name").value("Test Customer"))
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.phone").value("555-1234"))
            .andExpect(jsonPath("$.street").value("123 Main St"))
            .andExpect(jsonPath("$.city").value("City"))
            .andExpect(jsonPath("$.postalCode").value("12345"))
            .andExpect(jsonPath("$.country").value("US"))
            .andExpect(jsonPath("$.paymentTerms").value("NET_30"));
    }

    @Test
    @DisplayName("GET /api/customers/{id} - Get non-existent customer returns 404")
    void getCustomerById_notFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/customers/{id}", nonExistentId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/customers - Create customer")
    void createCustomer() throws Exception {
        // Given
        String requestBody = """
            {
                "name": "New Customer",
                "email": "newcustomer@example.com",
                "phone": "555-9999",
                "address": {
                    "street": "789 New St",
                    "city": "New City",
                    "postalCode": "99999",
                    "country": "US"
                },
                "paymentTerms": "NET_30"
            }
            """;

        // When/Then
        String response = mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("New Customer"))
            .andExpect(jsonPath("$.email").value("newcustomer@example.com"))
            .andExpect(jsonPath("$.phone").value("555-9999"))
            .andExpect(jsonPath("$.street").value("789 New St"))
            .andExpect(jsonPath("$.city").value("New City"))
            .andExpect(jsonPath("$.postalCode").value("99999"))
            .andExpect(jsonPath("$.country").value("US"))
            .andExpect(jsonPath("$.paymentTerms").value("NET_30"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Verify customer was persisted
        String customerId = JsonPath.read(response, "$.id");
        Customer savedCustomer = customerRepository.findById(UUID.fromString(customerId))
            .orElseThrow(() -> new AssertionError("Customer should be persisted"));
        assertThat(savedCustomer.getName()).isEqualTo("New Customer");
        assertThat(savedCustomer.getEmail().getValue()).isEqualTo("newcustomer@example.com");
    }

    @Test
    @DisplayName("PUT /api/customers/{id} - Update customer")
    void updateCustomer() throws Exception {
        // Given - create a customer
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.create(
            customerId,
            "Original Name",
            Email.of("original@example.com"),
            new Address("123 Original St", "Original City", "11111", "US"),
            "555-1111",
            PaymentTerms.net30()
        );
        customerRepository.save(customer);

        // When - update customer
        String updateRequestBody = """
            {
                "name": "Updated Name",
                "email": "updated@example.com",
                "phone": "555-2222",
                "address": {
                    "street": "456 Updated St",
                    "city": "Updated City",
                    "postalCode": "22222",
                    "country": "CA"
                },
                "paymentTerms": "NET_45"
            }
            """;

        mockMvc.perform(put("/api/customers/{id}", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestBody))
            .andExpect(status().isNoContent());

        // Then - verify customer was updated
        Customer updatedCustomer = customerRepository.findById(customerId)
            .orElseThrow(() -> new AssertionError("Customer should exist"));
        assertThat(updatedCustomer.getName()).isEqualTo("Updated Name");
        assertThat(updatedCustomer.getEmail().getValue()).isEqualTo("updated@example.com");
        assertThat(updatedCustomer.getPhone()).isEqualTo("555-2222");
        assertThat(updatedCustomer.getAddress().getStreet()).isEqualTo("456 Updated St");
        assertThat(updatedCustomer.getAddress().getCity()).isEqualTo("Updated City");
        assertThat(updatedCustomer.getAddress().getPostalCode()).isEqualTo("22222");
        assertThat(updatedCustomer.getAddress().getCountry()).isEqualTo("CA");
        assertThat(updatedCustomer.getDefaultPaymentTerms().getValue()).isEqualTo("NET_45");
    }

    @Test
    @DisplayName("PUT /api/customers/{id} - Update non-existent customer returns 404")
    void updateCustomer_notFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        String updateRequestBody = """
            {
                "name": "Updated Name",
                "email": "updated@example.com",
                "phone": "555-2222",
                "address": {
                    "street": "456 Updated St",
                    "city": "Updated City",
                    "postalCode": "22222",
                    "country": "CA"
                },
                "paymentTerms": "NET_45"
            }
            """;

        mockMvc.perform(put("/api/customers/{id}", nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestBody))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/customers/{id} - Delete customer")
    void deleteCustomer() throws Exception {
        // Given - create a customer
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.create(
            customerId,
            "To Be Deleted",
            Email.of("delete@example.com"),
            new Address("123 Delete St", "Delete City", "11111", "US"),
            "555-1111",
            PaymentTerms.net30()
        );
        customerRepository.save(customer);

        // Verify customer exists
        assertThat(customerRepository.findById(customerId)).isPresent();

        // When - delete customer
        mockMvc.perform(delete("/api/customers/{id}", customerId))
            .andExpect(status().isNoContent());

        // Then - verify customer was deleted
        assertThat(customerRepository.findById(customerId)).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/customers/{id} - Delete non-existent customer returns 404")
    void deleteCustomer_notFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/customers/{id}", nonExistentId))
            .andExpect(status().isNotFound());
    }
}

