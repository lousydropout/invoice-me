package com.invoiceme.invoice.api;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T3.5 - Performance Benchmark Tests
 * 
 * Tests for invoice API performance (latency and throughput).
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T3.5 - Performance Benchmark")
@Transactional
class InvoicePerformanceTest {

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

    private UUID customerId;
    private static final long MAX_AVERAGE_LATENCY_MS = 200;

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
    @DisplayName("T3.5.1 - CRUD average latency under 200 ms")
    void crudAverageLatencyUnder200ms() throws Exception {
        List<Long> createLatencies = new ArrayList<>();
        List<Long> readLatencies = new ArrayList<>();
        List<Long> updateLatencies = new ArrayList<>();
        List<Long> deleteLatencies = new ArrayList<>();

        // Perform 10 operations of each type
        List<String> invoiceIds = new ArrayList<>();

        // CREATE operations
        for (int i = 0; i < 10; i++) {
            String requestBody = """
                {
                    "customerId": "%s",
                    "lineItems": [
                        {
                            "description": "Service %d",
                            "quantity": 1,
                            "unitPriceAmount": 100.00,
                            "unitPriceCurrency": "USD"
                        }
                    ],
                    "issueDate": "%s",
                    "dueDate": "%s",
                    "taxRate": 0.10,
                    "notes": "Performance test"
                }
                """.formatted(
                    customerId,
                    i,
                    LocalDate.now(),
                    LocalDate.now().plusDays(30)
                );

            long startTime = System.currentTimeMillis();
            String response = mockMvc.perform(post("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
            long latency = System.currentTimeMillis() - startTime;
            createLatencies.add(latency);

            // Extract invoice ID for later operations
            String invoiceId = response.substring(
                response.indexOf("\"id\":\"") + 6,
                response.indexOf("\"", response.indexOf("\"id\":\"") + 6)
            );
            invoiceIds.add(invoiceId);
        }

        // READ operations
        for (String invoiceId : invoiceIds) {
            long startTime = System.currentTimeMillis();
            mockMvc.perform(get("/api/invoices/" + invoiceId))
                .andExpect(status().isOk());
            long latency = System.currentTimeMillis() - startTime;
            readLatencies.add(latency);
        }

        // UPDATE operations
        for (String invoiceId : invoiceIds) {
            String updateBody = """
                {
                    "lineItems": [
                        {
                            "description": "Updated Service",
                            "quantity": 2,
                            "unitPriceAmount": 150.00,
                            "unitPriceCurrency": "USD"
                        }
                    ],
                    "dueDate": "%s",
                    "taxRate": 0.15,
                    "notes": "Updated"
                }
                """.formatted(LocalDate.now().plusDays(45));

            long startTime = System.currentTimeMillis();
            mockMvc.perform(put("/api/invoices/" + invoiceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody))
                .andExpect(status().isNoContent());
            long latency = System.currentTimeMillis() - startTime;
            updateLatencies.add(latency);
        }

        // Note: DELETE endpoint not implemented yet, skipping delete operations

        // Calculate averages
        double avgCreate = createLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
        double avgRead = readLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
        double avgUpdate = updateLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
        double overallAvg = (avgCreate + avgRead + avgUpdate) / 3.0;

        System.out.printf("Performance Results:%n");
        System.out.printf("  CREATE average: %.2f ms%n", avgCreate);
        System.out.printf("  READ average: %.2f ms%n", avgRead);
        System.out.printf("  UPDATE average: %.2f ms%n", avgUpdate);
        System.out.printf("  Overall average: %.2f ms%n", overallAvg);

        // Assert
        assertThat(overallAvg)
            .as("Overall average latency should be under %d ms, but was %.2f ms", MAX_AVERAGE_LATENCY_MS, overallAvg)
            .isLessThan(MAX_AVERAGE_LATENCY_MS);
    }

    @Test
    @DisplayName("T3.5.2 - 100 consecutive invoices creation")
    void create100ConsecutiveInvoices() throws Exception {
        // Create 100 invoices consecutively (sequentially)
        // Note: MockMvc is not thread-safe, so we test sequential creation
        // which still validates the API can handle many requests in quick succession
        int successCount = 0;
        int errorCount = 0;
        
        for (int i = 0; i < 100; i++) {
            try {
                String requestBody = """
                    {
                        "customerId": "%s",
                        "lineItems": [
                            {
                                "description": "Service %d",
                                "quantity": 1,
                                "unitPriceAmount": 100.00,
                                "unitPriceCurrency": "USD"
                            }
                        ],
                        "issueDate": "%s",
                        "dueDate": "%s",
                        "taxRate": 0.10,
                        "notes": "Consecutive test %d"
                    }
                    """.formatted(
                        customerId,
                        i,
                        LocalDate.now(),
                        LocalDate.now().plusDays(30),
                        i
                    );

                mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                    .andExpect(status().isCreated());
                successCount++;
            } catch (Exception e) {
                errorCount++;
            }
        }

        // Verify all invoices were created successfully
        assertThat(errorCount)
            .as("No errors should occur during consecutive invoice creation. Found %d errors out of 100 attempts", errorCount)
            .isEqualTo(0);
        assertThat(successCount)
            .as("All 100 invoices should be created successfully")
            .isEqualTo(100);

        System.out.printf("Successfully created %d invoices consecutively with %d errors%n", successCount, errorCount);
    }
}

