package com.invoiceme.invoice.api;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.invoice.domain.valueobjects.InvoiceStatus;
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
 * Auth-enabled integration tests for Invoice Query API endpoints (Epic 5 Final Verification).
 * These tests verify authentication is required and works correctly.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Epic 5 - Invoice Query API Auth Integration")
@Transactional
class InvoiceQueryApiAuthIntegrationTest {

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
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Currency USD = Currency.getInstance("USD");
    private UUID customerId;
    private UUID invoiceId1;

    @BeforeEach
    void setUp() {
        // Create customer
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

        // Create invoice
        invoiceId1 = UUID.randomUUID();
        Invoice invoice1 = Invoice.create(
            invoiceId1,
            customerId,
            InvoiceNumber.generate(),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            List.of(LineItem.of("Service A", BigDecimal.valueOf(5), Money.of(BigDecimal.valueOf(100), USD))),
            "Invoice 1 notes",
            BigDecimal.valueOf(0.10)
        );
        invoice1.sendInvoice();
        invoiceRepository.save(invoice1);
        
        // Flush to ensure data is visible to JdbcTemplate
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Epic 5.1 - GET /api/invoices - Unauthenticated returns 401")
    void listAllInvoices_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Epic 5.1 - GET /api/invoices - Authenticated returns 200")
    void listAllInvoices_Authenticated() throws Exception {
        mockMvc.perform(get("/api/invoices")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[?(@.id == '%s')]", invoiceId1.toString()).exists());
    }

    @Test
    @DisplayName("Epic 5.1 - GET /api/invoices/{id} - Unauthenticated returns 401")
    void getInvoiceById_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/invoices/{id}", invoiceId1)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Epic 5.1 - GET /api/invoices/{id} - Authenticated returns 200")
    void getInvoiceById_Authenticated() throws Exception {
        mockMvc.perform(get("/api/invoices/{id}", invoiceId1)
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(invoiceId1.toString()))
            .andExpect(jsonPath("$.status").value(InvoiceStatus.SENT.name()));
    }

    @Test
    @DisplayName("Epic 5.1 - GET /api/invoices/overdue - Unauthenticated returns 401")
    void listOverdueInvoices_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/invoices/overdue")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Epic 5.1 - GET /api/invoices/overdue - Authenticated returns 200")
    void listOverdueInvoices_Authenticated() throws Exception {
        mockMvc.perform(get("/api/invoices/overdue")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray());
    }
}

