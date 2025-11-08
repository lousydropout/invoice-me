package com.invoiceme.customer.api;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.payment.domain.Payment;
import com.invoiceme.payment.domain.PaymentMethod;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
import com.invoiceme.shared.domain.Money;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for Customer Query API endpoints (Task 5.3).
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T5.3 - Customer Query API Integration")
@Transactional
class CustomerQueryApiIntegrationTest {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Currency USD = Currency.getInstance("USD");
    private UUID customerId1;
    private UUID customerId2;

    @BeforeEach
    void setUp() {
        // Create first customer
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

        // Create second customer
        customerId2 = UUID.randomUUID();
        Customer customer2 = Customer.create(
            customerId2,
            "Customer B",
            Email.of("customerB@example.com"),
            new Address("456 Oak Ave", "Town", "67890", "US"),
            "555-5678",
            PaymentTerms.net30()
        );
        customerRepository.save(customer2);

        // Create invoice for customer 1 with partial payment
        UUID invoiceId1 = UUID.randomUUID();
        Invoice invoice1 = Invoice.create(
            invoiceId1,
            customerId1,
            InvoiceNumber.generate(),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            List.of(LineItem.of("Service A", BigDecimal.valueOf(5), Money.of(BigDecimal.valueOf(100), USD))),
            "Invoice 1",
            BigDecimal.valueOf(0.10)
        );
        invoice1.sendInvoice();
        // Record partial payment: 250
        invoice1.recordPayment(
            new Payment(
                UUID.randomUUID(),
                Money.of(BigDecimal.valueOf(250), USD),
                LocalDate.now(),
                PaymentMethod.BANK_TRANSFER,
                "REF-123"
            )
        );
        invoiceRepository.save(invoice1);

        // Create invoice for customer 2 with no payments
        UUID invoiceId2 = UUID.randomUUID();
        Invoice invoice2 = Invoice.create(
            invoiceId2,
            customerId2,
            InvoiceNumber.generate(),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            List.of(LineItem.of("Service B", BigDecimal.valueOf(2), Money.of(BigDecimal.valueOf(100), USD))),
            "Invoice 2",
            BigDecimal.valueOf(0.10)
        );
        invoice2.sendInvoice();
        invoiceRepository.save(invoice2);
        
        // Flush to ensure data is visible to JdbcTemplate
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("T5.3.5 - GET /api/customers/outstanding - Get outstanding balances")
    void getOutstandingBalances() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/customers/outstanding")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$[?(@.id == '%s')].id", customerId1.toString()).value(customerId1.toString()))
            .andExpect(jsonPath("$[?(@.id == '%s')].outstandingBalance", customerId1.toString()).value(300.0))
            .andExpect(jsonPath("$[?(@.id == '%s')].id", customerId2.toString()).value(customerId2.toString()))
            .andExpect(jsonPath("$[?(@.id == '%s')].outstandingBalance", customerId2.toString()).value(220.0));
    }
}

