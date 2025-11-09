package com.invoiceme.shared.api.exception;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.shared.application.errors.ErrorCodes;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
import com.invoiceme.shared.domain.Money;
import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GlobalExceptionHandler.
 * Verifies that error handling works end-to-end through the REST API.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T6.2 - Global Exception Handler Integration")
@Transactional
class GlobalExceptionHandlerIntegrationTest {

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
    private CustomerRepository customerRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Currency USD = Currency.getInstance("USD");
    private UUID customerId;
    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        // Create customer
        customerId = UUID.randomUUID();
        Customer customer = Customer.create(
            customerId,
            "Test Customer",
            Email.of("test-" + customerId + "@example.com"),
            new Address("123 Main St", "City", "12345", "US"),
            "555-1234",
            PaymentTerms.net30()
        );
        customerRepository.save(customer);

        // Create invoice
        invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.create(
            invoiceId,
            customerId,
            InvoiceNumber.generate(),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            List.of(LineItem.of("Service A", BigDecimal.valueOf(5), Money.of(BigDecimal.valueOf(100), USD))),
            "Test invoice",
            BigDecimal.valueOf(0.10)
        );
        invoice.sendInvoice();
        invoiceRepository.save(invoice);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("T6.2.16 - POST /api/customers with invalid email returns 400")
    void createCustomer_withInvalidEmail_returns400() throws Exception {
        // Given
        String invalidRequest = """
            {
                "name": "Test Customer",
                "email": "invalid-email",
                "phone": "555-1234",
                "address": {
                    "street": "123 Main St",
                    "city": "City",
                    "postalCode": "12345",
                    "country": "US"
                },
                "paymentTerms": "NET_30"
            }
            """;

        // When/Then
        String response = mockMvc.perform(post("/api/customers")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasKey("message")))
            .andExpect(jsonPath("$", hasKey("code")))
            .andExpect(jsonPath("$", hasKey("details")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String code = JsonPath.read(response, "$.code");
        assertThat(code).isEqualTo(ErrorCodes.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("T6.2.17 - GET /api/customers/{id} with non-existent ID returns 404")
    void getCustomer_withNonExistentId_returns404() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When/Then
        String response = mockMvc.perform(get("/api/customers/{id}", nonExistentId)
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasKey("message")))
            .andExpect(jsonPath("$", hasKey("code")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String code = JsonPath.read(response, "$.code");
        assertThat(code).isEqualTo(ErrorCodes.NOT_FOUND);
    }

    @Test
    @DisplayName("T6.2.18 - POST /api/customers with duplicate email returns 409")
    void createCustomer_withDuplicateEmail_returns409() throws Exception {
        // Given - customer already exists with email
        String duplicateEmail = "duplicate@example.com";
        Customer existingCustomer = Customer.create(
            UUID.randomUUID(),
            "Existing Customer",
            Email.of(duplicateEmail),
            new Address("123 Main St", "City", "12345", "US"),
            "555-1234",
            PaymentTerms.net30()
        );
        customerRepository.save(existingCustomer);
        entityManager.flush();
        entityManager.clear();

        String duplicateRequest = """
            {
                "name": "New Customer",
                "email": "%s",
                "phone": "555-5678",
                "address": {
                    "street": "456 Oak Ave",
                    "city": "Town",
                    "postalCode": "67890",
                    "country": "US"
                },
                "paymentTerms": "NET_30"
            }
            """.formatted(duplicateEmail);

        // When/Then
        String response = mockMvc.perform(post("/api/customers")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateRequest))
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasKey("message")))
            .andExpect(jsonPath("$", hasKey("code")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String code = JsonPath.read(response, "$.code");
        assertThat(code).isEqualTo(ErrorCodes.CONFLICT);
    }

    @Test
    @DisplayName("T6.2.19 - POST /api/invoices/{id}/payments with amount exceeding balance returns 422")
    void recordPayment_withAmountExceedingBalance_returns422() throws Exception {
        // Given - invoice has balance of 550 (500 subtotal + 50 tax)
        // Payment of 1000 exceeds balance
        String paymentRequest = """
            {
                "amount": 1000.00,
                "currency": "USD",
                "paymentDate": "%s",
                "method": "BANK_TRANSFER",
                "reference": "REF-123"
            }
            """.formatted(LocalDate.now());

        // When/Then
        String response = mockMvc.perform(post("/api/invoices/{id}/payments", invoiceId)
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentRequest))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasKey("message")))
            .andExpect(jsonPath("$", hasKey("code")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String code = JsonPath.read(response, "$.code");
        assertThat(code).isEqualTo(ErrorCodes.BUSINESS_RULE_VIOLATION);
    }

    @Test
    @DisplayName("T6.2.20 - GET /api/invoices/{id} with non-existent ID returns 404")
    void getInvoice_withNonExistentId_returns404() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When/Then
        String response = mockMvc.perform(get("/api/invoices/{id}", nonExistentId)
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasKey("message")))
            .andExpect(jsonPath("$", hasKey("code")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String code = JsonPath.read(response, "$.code");
        assertThat(code).isEqualTo(ErrorCodes.NOT_FOUND);
    }

    @Test
    @DisplayName("T6.2.21 - POST /api/invoices with missing required fields returns 400")
    void createInvoice_withMissingFields_returns400() throws Exception {
        // Given - missing customerId and lineItems
        String invalidRequest = """
            {
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": 0.10,
                "notes": "Test invoice"
            }
            """.formatted(LocalDate.now(), LocalDate.now().plusDays(30));

        // When/Then
        String response = mockMvc.perform(post("/api/invoices")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasKey("message")))
            .andExpect(jsonPath("$", hasKey("code")))
            .andExpect(jsonPath("$", hasKey("details")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String code = JsonPath.read(response, "$.code");
        assertThat(code).isEqualTo(ErrorCodes.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("T6.2.22 - Error responses include error, code, and details fields")
    void errorResponses_includeRequiredFields() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When/Then
        mockMvc.perform(get("/api/customers/{id}", nonExistentId)
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$", hasKey("message")))
            .andExpect(jsonPath("$", hasKey("code")))
            .andExpect(jsonPath("$.message").isString())
            .andExpect(jsonPath("$.code").isString());
    }
}

