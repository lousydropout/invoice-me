package com.invoiceme.shared.api.validation;

import com.invoiceme.shared.application.errors.ErrorCodes;
import com.jayway.jsonpath.JsonPath;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DTO validation (Task 6.3).
 * Verifies that all validation annotations work correctly and return 400 status codes.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T6.3 - DTO Validation Integration")
class DtoValidationIntegrationTest {

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

    @Test
    @DisplayName("T6.3.1 - POST /api/customers with missing name returns 400")
    void createCustomer_missingName_returns400() throws Exception {
        String request = """
            {
                "email": "test@example.com",
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

        String response = mockMvc.perform(post("/api/customers")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$", hasKey("error")))
            .andExpect(jsonPath("$", hasKey("code")))
            .andExpect(jsonPath("$", hasKey("details")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String code = JsonPath.read(response, "$.code");
        assertThat(code).isEqualTo(ErrorCodes.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("T6.3.2 - POST /api/customers with invalid email returns 400")
    void createCustomer_invalidEmail_returns400() throws Exception {
        String request = """
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

        mockMvc.perform(post("/api/customers")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    @DisplayName("T6.3.3 - POST /api/customers with missing address fields returns 400")
    void createCustomer_missingAddressFields_returns400() throws Exception {
        String request = """
            {
                "name": "Test Customer",
                "email": "test@example.com",
                "phone": "555-1234",
                "address": {
                    "street": "123 Main St",
                    "city": "",
                    "postalCode": "12345",
                    "country": "US"
                },
                "paymentTerms": "NET_30"
            }
            """;

        mockMvc.perform(post("/api/customers")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details").exists());
    }

    @Test
    @DisplayName("T6.3.4 - POST /api/invoices with missing customerId returns 400")
    void createInvoice_missingCustomerId_returns400() throws Exception {
        String request = """
            {
                "lineItems": [
                    {
                        "description": "Service A",
                        "quantity": 5,
                        "unitPriceAmount": 100,
                        "unitPriceCurrency": "USD"
                    }
                ],
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": 0.10,
                "notes": "Test invoice"
            }
            """.formatted(LocalDate.now(), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/invoices")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details.customerId").exists());
    }

    @Test
    @DisplayName("T6.3.5 - POST /api/invoices with empty lineItems returns 400")
    void createInvoice_emptyLineItems_returns400() throws Exception {
        String request = """
            {
                "customerId": "%s",
                "lineItems": [],
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": 0.10,
                "notes": "Test invoice"
            }
            """.formatted(UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/invoices")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details.lineItems").exists());
    }

    @Test
    @DisplayName("T6.3.6 - POST /api/invoices with invalid currency code returns 400")
    void createInvoice_invalidCurrency_returns400() throws Exception {
        String request = """
            {
                "customerId": "%s",
                "lineItems": [
                    {
                        "description": "Service A",
                        "quantity": 5,
                        "unitPriceAmount": 100,
                        "unitPriceCurrency": "usd"
                    }
                ],
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": 0.10,
                "notes": "Test invoice"
            }
            """.formatted(UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/invoices")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details").exists());
    }

    @Test
    @DisplayName("T6.3.7 - POST /api/invoices with zero quantity returns 400")
    void createInvoice_zeroQuantity_returns400() throws Exception {
        String request = """
            {
                "customerId": "%s",
                "lineItems": [
                    {
                        "description": "Service A",
                        "quantity": 0,
                        "unitPriceAmount": 100,
                        "unitPriceCurrency": "USD"
                    }
                ],
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": 0.10,
                "notes": "Test invoice"
            }
            """.formatted(UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/invoices")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details").exists());
    }

    @Test
    @DisplayName("T6.3.8 - POST /api/invoices with negative tax rate returns 400")
    void createInvoice_negativeTaxRate_returns400() throws Exception {
        String request = """
            {
                "customerId": "%s",
                "lineItems": [
                    {
                        "description": "Service A",
                        "quantity": 5,
                        "unitPriceAmount": 100,
                        "unitPriceCurrency": "USD"
                    }
                ],
                "issueDate": "%s",
                "dueDate": "%s",
                "taxRate": -0.10,
                "notes": "Test invoice"
            }
            """.formatted(UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/invoices")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details.taxRate").exists());
    }

    @Test
    @DisplayName("T6.3.9 - POST /api/invoices/{id}/payments with missing amount returns 400")
    void recordPayment_missingAmount_returns400() throws Exception {
        String request = """
            {
                "currency": "USD",
                "paymentDate": "%s",
                "method": "BANK_TRANSFER",
                "reference": "REF-123"
            }
            """.formatted(LocalDate.now());

        mockMvc.perform(post("/api/invoices/{id}/payments", UUID.randomUUID())
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details.amount").exists());
    }

    @Test
    @DisplayName("T6.3.10 - POST /api/invoices/{id}/payments with zero amount returns 400")
    void recordPayment_zeroAmount_returns400() throws Exception {
        String request = """
            {
                "amount": 0,
                "currency": "USD",
                "paymentDate": "%s",
                "method": "BANK_TRANSFER",
                "reference": "REF-123"
            }
            """.formatted(LocalDate.now());

        mockMvc.perform(post("/api/invoices/{id}/payments", UUID.randomUUID())
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details.amount").exists());
    }

    @Test
    @DisplayName("T6.3.11 - POST /api/invoices/{id}/payments with invalid currency returns 400")
    void recordPayment_invalidCurrency_returns400() throws Exception {
        String request = """
            {
                "amount": 100,
                "currency": "usd",
                "paymentDate": "%s",
                "method": "BANK_TRANSFER",
                "reference": "REF-123"
            }
            """.formatted(LocalDate.now());

        mockMvc.perform(post("/api/invoices/{id}/payments", UUID.randomUUID())
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details.currency").exists());
    }

    @Test
    @DisplayName("T6.3.12 - POST /api/invoices/{id}/payments with invalid payment method returns 400")
    void recordPayment_invalidPaymentMethod_returns400() throws Exception {
        String request = """
            {
                "amount": 100,
                "currency": "USD",
                "paymentDate": "%s",
                "method": "INVALID_METHOD",
                "reference": "REF-123"
            }
            """.formatted(LocalDate.now());

        mockMvc.perform(post("/api/invoices/{id}/payments", UUID.randomUUID())
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details.method").exists());
    }

    @Test
    @DisplayName("T6.3.13 - POST /api/invoices/{id}/payments with missing payment method returns 400")
    void recordPayment_missingPaymentMethod_returns400() throws Exception {
        String request = """
            {
                "amount": 100,
                "currency": "USD",
                "paymentDate": "%s",
                "reference": "REF-123"
            }
            """.formatted(LocalDate.now());

        mockMvc.perform(post("/api/invoices/{id}/payments", UUID.randomUUID())
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
            .andExpect(jsonPath("$.details.method").exists());
    }

    @Test
    @DisplayName("T6.3.14 - Validation error responses include clear error messages")
    void validationError_clearErrorMessage() throws Exception {
        String request = """
            {
                "name": "",
                "email": "invalid-email",
                "phone": "555-1234",
                "address": {
                    "street": "123 Main St",
                    "city": "City",
                    "postalCode": "",
                    "country": "US"
                },
                "paymentTerms": "NET_30"
            }
            """;

        String response = mockMvc.perform(post("/api/customers")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Verify error structure
        String error = JsonPath.read(response, "$.error");
        String code = JsonPath.read(response, "$.code");
        Object details = JsonPath.read(response, "$.details");
        assertThat(error).isEqualTo("Validation failed");
        assertThat(code).isEqualTo(ErrorCodes.VALIDATION_FAILED);
        assertThat(details).isNotNull();
        
        // Verify specific field errors are present
        String nameError = JsonPath.read(response, "$.details.name");
        String emailError = JsonPath.read(response, "$.details.email");
        assertThat(nameError).isNotNull();
        assertThat(emailError).isNotNull();
    }
}

