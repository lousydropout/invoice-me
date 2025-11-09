package com.invoiceme.customer.api;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
import jakarta.persistence.EntityManager;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Auth-enabled integration tests for Customer API endpoints.
 * Tests that all endpoints require authentication and work correctly when authenticated.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Customer API Auth Integration Tests")
@Transactional
class CustomerApiAuthIntegrationTest {

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
        registry.add("spring.security.user.name", () -> "testuser");
        registry.add("spring.security.user.password", () -> "testpass");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        Customer customer = Customer.create(
            customerId,
            "Test Customer",
            Email.of("test@example.com"),
            new Address("123 Main St", "City", "12345", "US"),
            "555-1234",
            PaymentTerms.net30()
        );
        customerRepository.save(customer);
        
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("GET /api/customers/{id} - Unauthenticated returns 401")
    void getCustomerById_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", customerId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/customers/{id} - Authenticated returns 200")
    void getCustomerById_Authenticated() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", customerId)
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(customerId.toString()))
            .andExpect(jsonPath("$.name").value("Test Customer"))
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /api/customers - Unauthenticated returns 401")
    void createCustomer_Unauthenticated() throws Exception {
        String requestBody = """
            {
                "name": "New Customer",
                "email": "new@example.com",
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

        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/customers - Authenticated returns 201")
    void createCustomer_Authenticated() throws Exception {
        String requestBody = """
            {
                "name": "New Customer",
                "email": "new@example.com",
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

        mockMvc.perform(post("/api/customers")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value("New Customer"))
            .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    @DisplayName("PUT /api/customers/{id} - Unauthenticated returns 401")
    void updateCustomer_Unauthenticated() throws Exception {
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
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/customers/{id} - Authenticated returns 204")
    void updateCustomer_Authenticated() throws Exception {
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
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestBody))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/customers/{id} - Unauthenticated returns 401")
    void deleteCustomer_Unauthenticated() throws Exception {
        mockMvc.perform(delete("/api/customers/{id}", customerId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/customers/{id} - Authenticated returns 204")
    void deleteCustomer_Authenticated() throws Exception {
        mockMvc.perform(delete("/api/customers/{id}", customerId)
                .with(httpBasic("testuser", "testpass")))
            .andExpect(status().isNoContent());
    }
}

