package com.invoiceme.customer.api;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
import com.invoiceme.shared.domain.Money;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Auth-enabled integration tests for Customer Query API endpoints (Epic 5 Final Verification).
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Epic 5 - Customer Query API Auth Integration")
@Transactional
class CustomerQueryApiAuthIntegrationTest {

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
        registry.add("spring.security.user.name", () -> "testuser");
        registry.add("spring.security.user.password", () -> "testpass");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID customerId1;

    @BeforeEach
    void setUp() {
        customerId1 = UUID.randomUUID();
        Customer customer1 = Customer.create(
            customerId1,
            "Customer A",
            Email.of("customerA@example.com"),
            new Address("123 Main St", "City", "12345", "US"),
            "555-1234",
            PaymentTerms.net30()
        );
        customerRepository.save(customer1);
        
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Epic 5.1 - GET /api/customers/outstanding - Unauthenticated returns 401")
    void getOutstandingBalances_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/customers/outstanding")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Epic 5.1 - GET /api/customers/outstanding - Authenticated returns 200")
    void getOutstandingBalances_Authenticated() throws Exception {
        mockMvc.perform(get("/api/customers/outstanding")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray());
    }
}

