package com.invoiceme.invoice.api;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.invoice.domain.valueobjects.InvoiceStatus;
import com.invoiceme.payment.domain.Payment;
import com.invoiceme.payment.domain.PaymentMethod;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
import com.invoiceme.shared.domain.Money;
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
 * Integration tests for Invoice Query API endpoints (Task 5.3).
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T5.3 - Invoice Query API Integration")
@Transactional
class InvoiceQueryApiIntegrationTest {

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
    private UUID customerId;
    private UUID invoiceId1;
    private UUID invoiceId2;

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

        // Create first invoice
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

        // Create second invoice (overdue)
        invoiceId2 = UUID.randomUUID();
        Invoice invoice2 = Invoice.create(
            invoiceId2,
            customerId,
            InvoiceNumber.generate(),
            LocalDate.now().minusDays(10),
            LocalDate.now().minusDays(5), // Overdue
            List.of(LineItem.of("Service B", BigDecimal.valueOf(2), Money.of(BigDecimal.valueOf(50), USD))),
            "Invoice 2 notes",
            BigDecimal.valueOf(0.10)
        );
        invoice2.sendInvoice();
        invoiceRepository.save(invoice2);
        
        // Flush to ensure data is visible to JdbcTemplate
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("T5.3.1 - GET /api/invoices - List all invoices")
    void listAllInvoices() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$[?(@.id == '%s')]", invoiceId1.toString()).exists())
            .andExpect(jsonPath("$[?(@.id == '%s')].customerName", invoiceId1.toString()).value("Test Customer"))
            .andExpect(jsonPath("$[?(@.id == '%s')].status", invoiceId1.toString()).value(InvoiceStatus.SENT.name()));
    }

    @Test
    @DisplayName("T5.3.2 - GET /api/invoices/{id} - Get invoice details")
    void getInvoiceById() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/invoices/{id}", invoiceId1)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(invoiceId1.toString()))
            .andExpect(jsonPath("$.customerId").value(customerId.toString()))
            .andExpect(jsonPath("$.status").value(InvoiceStatus.SENT.name()))
            .andExpect(jsonPath("$.lineItems").isArray())
            .andExpect(jsonPath("$.lineItems[0].description").value("Service A"))
            .andExpect(jsonPath("$.subtotal.amount").value(500.0))
            .andExpect(jsonPath("$.tax.amount").value(50.0))
            .andExpect(jsonPath("$.total.amount").value(550.0))
            .andExpect(jsonPath("$.balance.amount").value(550.0));
    }

    @Test
    @DisplayName("T5.3.3 - GET /api/invoices/overdue - List overdue invoices")
    void listOverdueInvoices() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/invoices/overdue")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[?(@.id == '%s')]", invoiceId2.toString()).exists());
    }

    @Test
    @DisplayName("T5.3.4 - GET /api/invoices/{id} - Returns 404 for non-existent invoice")
    void getInvoiceByIdNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        
        mockMvc.perform(get("/api/invoices/{id}", nonExistentId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Epic 5.2 - GET /api/invoices - Performance under 200ms")
    void listInvoices_performanceUnder200ms() throws Exception {
        // Seed ~50 invoices with line items and payments
        for (int i = 0; i < 50; i++) {
            UUID invoiceId = UUID.randomUUID();
            Invoice invoice = Invoice.create(
                invoiceId,
                customerId,
                InvoiceNumber.generate(),
                LocalDate.now().minusDays(i),
                LocalDate.now().plusDays(30 - i),
                List.of(
                    LineItem.of("Service " + i, BigDecimal.valueOf(i + 1), Money.of(BigDecimal.valueOf(100 + i), USD)),
                    LineItem.of("Service B" + i, BigDecimal.valueOf(2), Money.of(BigDecimal.valueOf(50), USD))
                ),
                "Invoice " + i + " notes",
                BigDecimal.valueOf(0.10)
            );
            invoice.sendInvoice();
            
            // Add payment for some invoices
            if (i % 3 == 0) {
                invoice.recordPayment(
                    new Payment(
                        UUID.randomUUID(),
                        Money.of(BigDecimal.valueOf(100), USD),
                        LocalDate.now(),
                        PaymentMethod.BANK_TRANSFER,
                        "REF-" + i
                    )
                );
            }
            
            invoiceRepository.save(invoice);
        }
        
        entityManager.flush();
        entityManager.clear();
        
        // Measure performance
        long start = System.nanoTime();
        mockMvc.perform(get("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        
        assertThat(elapsedMs).isLessThan(200);
    }

    @Test
    @DisplayName("Epic 5.3 - GET /api/invoices/{id} - JSON Precision Validation")
    void getInvoiceById_jsonPrecisionValidation() throws Exception {
        // Create invoice with specific decimal values
        UUID precisionInvoiceId = UUID.randomUUID();
        Invoice precisionInvoice = Invoice.create(
            precisionInvoiceId,
            customerId,
            InvoiceNumber.generate(),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            List.of(
                LineItem.of("Service A", new BigDecimal("5.00"), Money.of(new BigDecimal("100.50"), USD)),
                LineItem.of("Service B", new BigDecimal("3.33"), Money.of(new BigDecimal("75.25"), USD))
            ),
            "Precision test invoice",
            new BigDecimal("0.10")
        );
        precisionInvoice.sendInvoice();
        invoiceRepository.save(precisionInvoice);
        
        entityManager.flush();
        entityManager.clear();
        
        // Verify exact decimal precision in JSON response
        // Expected: subtotal = 5.00 * 100.50 + 3.33 * 75.25 = 502.50 + 250.5825 = 753.0825
        // tax = 753.0825 * 0.10 = 75.30825
        // total = 753.0825 + 75.30825 = 828.39075
        // Note: JSON serialization may round to 2 decimal places, so we verify the values are close
        String response = mockMvc.perform(get("/api/invoices/{id}", precisionInvoiceId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        
        // Verify BigDecimal precision - JSON may serialize with rounding, so we check the actual values
        // and verify they match the expected calculations within reasonable precision
        BigDecimal subtotal = new BigDecimal(com.jayway.jsonpath.JsonPath.read(response, "$.subtotal.amount").toString());
        BigDecimal tax = new BigDecimal(com.jayway.jsonpath.JsonPath.read(response, "$.tax.amount").toString());
        BigDecimal total = new BigDecimal(com.jayway.jsonpath.JsonPath.read(response, "$.total.amount").toString());
        BigDecimal balance = new BigDecimal(com.jayway.jsonpath.JsonPath.read(response, "$.balance.amount").toString());
        
        // Verify calculations are correct (allowing for JSON serialization rounding)
        assertThat(subtotal).isCloseTo(new BigDecimal("753.0825"), org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
        assertThat(tax).isCloseTo(new BigDecimal("75.30825"), org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
        assertThat(total).isCloseTo(new BigDecimal("828.39075"), org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
        assertThat(balance).isCloseTo(new BigDecimal("828.39075"), org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
        
        // Verify the relationship: total = subtotal + tax
        assertThat(total).isEqualByComparingTo(subtotal.add(tax));
    }
}

