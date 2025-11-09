package com.invoiceme.invoice.application.commands;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.events.InvoiceCreated;
import com.invoiceme.invoice.domain.valueobjects.InvoiceStatus;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.application.errors.ApplicationError;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.DomainEvent;
import com.invoiceme.shared.domain.Email;
import com.invoiceme.shared.test.FakeDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for CreateInvoiceHandler.
 * Tests full persistence and event publishing with Testcontainers.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("CreateInvoiceHandler Integration Tests")
@Transactional
@org.springframework.context.annotation.Import(CreateInvoiceHandlerTest.TestConfig.class)
class CreateInvoiceHandlerTest {

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

    @Configuration
    static class TestConfig {
        @Bean
        @Primary
        public DomainEventPublisher fakeEventPublisher() {
            return new FakeDomainEventPublisher();
        }
    }

    @Autowired
    private CreateInvoiceHandler handler;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DomainEventPublisher eventPublisher;

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
        
        // Clear events
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            fake.clear();
        }
    }

    @Test
    @DisplayName("Should create invoice and persist to database")
    void shouldCreateInvoiceAndPersistToDatabase() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand command = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Service A",
                    BigDecimal.valueOf(2),
                    BigDecimal.valueOf(100),
                    "USD"
                ),
                new CreateInvoiceCommand.LineItemDto(
                    "Service B",
                    BigDecimal.valueOf(3),
                    BigDecimal.valueOf(50),
                    "USD"
                )
            ),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test notes"
        );

        // When
        UUID resultId = handler.handle(command);

        // Then
        assertThat(resultId).isEqualTo(invoiceId);
        
        // Verify invoice is persisted
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should be persisted"));
        
        assertThat(invoice.getId()).isEqualTo(invoiceId);
        assertThat(invoice.getCustomerId()).isEqualTo(customerId);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.getLineItems()).hasSize(2);
        assertThat(invoice.getTaxRate()).isEqualByComparingTo(BigDecimal.valueOf(0.10));
        assertThat(invoice.getNotes()).isEqualTo("Test notes");
    }

    @Test
    @DisplayName("Should publish InvoiceCreated event")
    void shouldPublishInvoiceCreatedEvent() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand command = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Service A",
                    BigDecimal.valueOf(1),
                    BigDecimal.valueOf(100),
                    "USD"
                )
            ),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test notes"
        );

        // When
        handler.handle(command);

        // Then
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(InvoiceCreated.class);
            
            InvoiceCreated event = (InvoiceCreated) events.get(0);
            assertThat(event.invoiceId()).isEqualTo(invoiceId);
            assertThat(event.customerId()).isEqualTo(customerId);
            assertThat(event.invoiceNumber()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Should fail when customer does not exist")
    void shouldFailWhenCustomerDoesNotExist() {
        // Given
        UUID nonExistentCustomerId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand command = new CreateInvoiceCommand(
            invoiceId,
            nonExistentCustomerId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Service A",
                    BigDecimal.valueOf(1),
                    BigDecimal.valueOf(100),
                    "USD"
                )
            ),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test notes"
        );

        // When/Then
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(ApplicationError.class)
            .hasMessageContaining("Customer");
    }

    @Test
    @DisplayName("Should verify database state after creation")
    void shouldVerifyDatabaseStateAfterCreation() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand command = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Database Test Service",
                    BigDecimal.valueOf(5),
                    BigDecimal.valueOf(200),
                    "USD"
                )
            ),
            LocalDate.of(2024, 1, 15),
            LocalDate.of(2024, 2, 15),
            BigDecimal.valueOf(0.15),
            "Database test notes"
        );

        // When
        handler.handle(command);

        // Then - verify database state
        Invoice savedInvoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist in database"));
        
        assertThat(savedInvoice.getCustomerId()).isEqualTo(customerId);
        assertThat(savedInvoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(savedInvoice.getLineItems()).hasSize(1);
        assertThat(savedInvoice.getLineItems().get(0).getDescription()).isEqualTo("Database Test Service");
        assertThat(savedInvoice.getLineItems().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(savedInvoice.getTaxRate()).isEqualByComparingTo(BigDecimal.valueOf(0.15));
        assertThat(savedInvoice.getNotes()).isEqualTo("Database test notes");
        assertThat(savedInvoice.getIssueDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(savedInvoice.getDueDate()).isEqualTo(LocalDate.of(2024, 2, 15));
    }

    @Test
    @DisplayName("Should calculate invoice totals correctly")
    void shouldCalculateInvoiceTotalsCorrectly() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand command = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Service A",
                    BigDecimal.valueOf(2),
                    BigDecimal.valueOf(100),
                    "USD"
                ),
                new CreateInvoiceCommand.LineItemDto(
                    "Service B",
                    BigDecimal.valueOf(3),
                    BigDecimal.valueOf(50),
                    "USD"
                )
            ),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test notes"
        );

        // When
        handler.handle(command);

        // Then
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should be persisted"));
        
        // Subtotal: 2 * 100 + 3 * 50 = 200 + 150 = 350
        // Tax: 350 * 0.10 = 35
        // Total: 350 + 35 = 385
        assertThat(invoice.calculateSubtotal().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(350));
        assertThat(invoice.calculateTax().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(35));
        assertThat(invoice.calculateTotal().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(385));
    }
}

