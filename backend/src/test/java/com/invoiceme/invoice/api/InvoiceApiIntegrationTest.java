package com.invoiceme.invoice.api;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.api.dto.InvoiceResponse;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.valueobjects.InvoiceStatus;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T3.4 - REST API Integration Tests
 * 
 * Tests for Invoice REST API endpoints using MockMvc.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T3.4 - REST API Integration")
@Transactional
class InvoiceApiIntegrationTest {

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
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        // Create a customer for invoices
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
    }

    @Test
    @DisplayName("T3.4.1 - POST /api/invoices - Create invoice (DRAFT)")
    void createInvoice() throws Exception {
        // Given
        String requestBody = """
            {
                "customerId": "%s",
                "lineItems": [
                    {
                        "description": "Service A",
                        "quantity": 2,
                        "unitPriceAmount": 100.00,
                        "unitPriceCurrency": "USD"
                    }
                ],
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": 0.10,
                "notes": "Test invoice"
            }
            """.formatted(
                customerId,
                LocalDate.now(),
                LocalDate.now().plusDays(30)
            );

        // When/Then
        String response = mockMvc.perform(post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.lineItems").isArray())
            .andExpect(jsonPath("$.lineItems[0].description").value("Service A"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Verify invoice was persisted
        String invoiceId = JsonPath.read(response, "$.id");
        assertThat(invoiceRepository.findById(UUID.fromString(invoiceId))).isPresent();
    }

    @Test
    @DisplayName("T3.4.2 - PUT /api/invoices/{id} - Update draft invoice")
    void updateDraftInvoice() throws Exception {
        // Given - create an invoice first
        String createRequestBody = """
            {
                "customerId": "%s",
                "lineItems": [
                    {
                        "description": "Service A",
                        "quantity": 1,
                        "unitPriceAmount": 100.00,
                        "unitPriceCurrency": "USD"
                    }
                ],
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": 0.10,
                "notes": "Original notes"
            }
            """.formatted(
                customerId,
                LocalDate.now(),
                LocalDate.now().plusDays(30)
            );

        String createResponse = mockMvc.perform(post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String invoiceId = JsonPath.read(createResponse, "$.id");

        // When - update the invoice
        String updateRequestBody = """
            {
                "lineItems": [
                    {
                        "description": "Service B",
                        "quantity": 3,
                        "unitPriceAmount": 150.00,
                        "unitPriceCurrency": "USD"
                    }
                ],
                "dueDate": "%s",
                "taxRate": 0.15,
                "notes": "Updated notes"
            }
            """.formatted(LocalDate.now().plusDays(45));

        mockMvc.perform(put("/api/invoices/" + invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestBody))
            .andExpect(status().isNoContent());

        // Then - verify invoice was updated
        Invoice invoice = invoiceRepository.findById(UUID.fromString(invoiceId)).get();
        assertThat(invoice.getLineItems().get(0).getDescription()).isEqualTo("Service B");
        assertThat(invoice.getTaxRate()).isEqualByComparingTo(BigDecimal.valueOf(0.15));
        assertThat(invoice.getNotes()).isEqualTo("Updated notes");
    }

    @Test
    @DisplayName("T3.4.3 - POST /api/invoices/{id}/send - Send invoice")
    void sendInvoice() throws Exception {
        // Given - create an invoice first
        String createRequestBody = """
            {
                "customerId": "%s",
                "lineItems": [
                    {
                        "description": "Service A",
                        "quantity": 1,
                        "unitPriceAmount": 100.00,
                        "unitPriceCurrency": "USD"
                    }
                ],
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": 0.10,
                "notes": "Test invoice"
            }
            """.formatted(
                customerId,
                LocalDate.now(),
                LocalDate.now().plusDays(30)
            );

        String createResponse = mockMvc.perform(post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String invoiceId = JsonPath.read(createResponse, "$.id");

        // When
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/send"))
            .andExpect(status().isNoContent());

        // Then
        Invoice invoice = invoiceRepository.findById(UUID.fromString(invoiceId)).get();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    @DisplayName("T3.4.4 - POST /api/invoices/{id}/payments - Record payment")
    void recordPayment() throws Exception {
        // Given - create and send an invoice first
        String createRequestBody = """
            {
                "customerId": "%s",
                "lineItems": [
                    {
                        "description": "Service A",
                        "quantity": 2,
                        "unitPriceAmount": 100.00,
                        "unitPriceCurrency": "USD"
                    }
                ],
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": 0.10,
                "notes": "Test invoice"
            }
            """.formatted(
                customerId,
                LocalDate.now(),
                LocalDate.now().plusDays(30)
            );

        String createResponse = mockMvc.perform(post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String invoiceId = JsonPath.read(createResponse, "$.id");

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/send"))
            .andExpect(status().isNoContent());

        // Get initial balance
        Invoice invoiceBefore = invoiceRepository.findById(UUID.fromString(invoiceId)).get();
        BigDecimal balanceBefore = invoiceBefore.calculateBalance().getAmount();

        // When
        String paymentRequestBody = """
            {
                "amount": 110.00,
                "currency": "USD",
                "paymentDate": "%s",
                "method": "BANK_TRANSFER",
                "reference": "REF-123"
            }
            """.formatted(LocalDate.now());

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentRequestBody))
            .andExpect(status().isNoContent());

        // Then
        Invoice invoiceAfter = invoiceRepository.findById(UUID.fromString(invoiceId)).get();
        BigDecimal balanceAfter = invoiceAfter.calculateBalance().getAmount();
        assertThat(balanceAfter).isLessThan(balanceBefore);
        assertThat(invoiceAfter.getPayments()).hasSize(1);
    }

    @Test
    @DisplayName("T3.4.5 - GET /api/invoices/{id} - Retrieve invoice detail")
    void getInvoiceById() throws Exception {
        // Given - create an invoice first
        String createRequestBody = """
            {
                "customerId": "%s",
                "lineItems": [
                    {
                        "description": "Service A",
                        "quantity": 2,
                        "unitPriceAmount": 100.00,
                        "unitPriceCurrency": "USD"
                    }
                ],
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": 0.10,
                "notes": "Test invoice"
            }
            """.formatted(
                customerId,
                LocalDate.now(),
                LocalDate.now().plusDays(30)
            );

        String createResponse = mockMvc.perform(post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String invoiceId = JsonPath.read(createResponse, "$.id");

        // When/Then
        mockMvc.perform(get("/api/invoices/" + invoiceId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(invoiceId))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.lineItems").isArray())
            .andExpect(jsonPath("$.lineItems[0].description").value("Service A"))
            .andExpect(jsonPath("$.subtotal").exists())
            .andExpect(jsonPath("$.tax").exists())
            .andExpect(jsonPath("$.total").exists())
            .andExpect(jsonPath("$.balance").exists());
    }

    @Test
    @DisplayName("T3.4.6 - GET /api/invoices - List invoices")
    void listInvoices() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/invoices"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray());
        // Note: Currently returns empty list (TODO for query handlers)
    }
}

