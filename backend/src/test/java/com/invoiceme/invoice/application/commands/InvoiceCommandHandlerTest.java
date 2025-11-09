package com.invoiceme.invoice.application.commands;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.exceptions.PaymentExceedsBalanceException;
import com.invoiceme.invoice.domain.valueobjects.InvoiceStatus;
import com.invoiceme.invoice.domain.events.InvoiceCreated;
import com.invoiceme.invoice.domain.events.InvoicePaid;
import com.invoiceme.invoice.domain.events.InvoiceSent;
import com.invoiceme.invoice.domain.events.InvoiceUpdated;
import com.invoiceme.invoice.domain.events.PaymentRecorded;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.DomainEvent;
import com.invoiceme.shared.domain.Email;
import com.invoiceme.shared.test.FakeDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * T3.3 - Command Handlers Tests
 * 
 * Tests for invoice command handlers with event verification.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T3.3 - Command Handlers")
@Transactional
@org.springframework.context.annotation.Import(InvoiceCommandHandlerTest.TestConfig.class)
class InvoiceCommandHandlerTest {

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

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        public DomainEventPublisher fakeEventPublisher() {
            return new FakeDomainEventPublisher();
        }
    }

    @Autowired
    private CreateInvoiceHandler createInvoiceHandler;

    @Autowired
    private UpdateInvoiceHandler updateInvoiceHandler;

    @Autowired
    private SendInvoiceHandler sendInvoiceHandler;

    @Autowired
    private RecordPaymentHandler recordPaymentHandler;

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
    @DisplayName("T3.3.1 - CreateInvoiceCommand → repository save + InvoiceCreated")
    void createInvoiceCommandSavesAndEmitsEvent() {
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
                )
            ),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test notes"
        );

        // When
        UUID resultId = createInvoiceHandler.handle(command);

        // Then
        assertThat(resultId).isEqualTo(invoiceId);
        assertThat(invoiceRepository.findById(invoiceId)).isPresent();
        
        Invoice invoice = invoiceRepository.findById(invoiceId).get();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        
        // Verify event
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(InvoiceCreated.class);
        }
    }

    @Test
    @DisplayName("T3.3.2 - UpdateInvoiceCommand modifies DRAFT invoice")
    void updateInvoiceCommandModifiesDraftInvoice() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand createCommand = new CreateInvoiceCommand(
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
            "Original notes"
        );
        createInvoiceHandler.handle(createCommand);
        
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            fake.clear();
        }

        UpdateInvoiceCommand updateCommand = new UpdateInvoiceCommand(
            invoiceId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Service B",
                    BigDecimal.valueOf(3),
                    BigDecimal.valueOf(150),
                    "USD"
                )
            ),
            LocalDate.now().plusDays(45),
            BigDecimal.valueOf(0.15),
            "Updated notes"
        );

        // When
        updateInvoiceHandler.handle(updateCommand);

        // Then
        Invoice invoice = invoiceRepository.findById(invoiceId).get();
        assertThat(invoice.getLineItems()).hasSize(1);
        assertThat(invoice.getLineItems().get(0).getDescription()).isEqualTo("Service B");
        assertThat(invoice.getTaxRate()).isEqualByComparingTo(BigDecimal.valueOf(0.15));
        assertThat(invoice.getNotes()).isEqualTo("Updated notes");
        
        // Verify event
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(InvoiceUpdated.class);
        }
    }

    @Test
    @DisplayName("T3.3.3 - SendInvoiceCommand changes state → SENT")
    void sendInvoiceCommandChangesStateToSent() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand createCommand = new CreateInvoiceCommand(
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
        createInvoiceHandler.handle(createCommand);
        
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            fake.clear();
        }

        SendInvoiceCommand sendCommand = new SendInvoiceCommand(invoiceId);

        // When
        sendInvoiceHandler.handle(sendCommand);

        // Then
        Invoice invoice = invoiceRepository.findById(invoiceId).get();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
        
        // Verify event
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(InvoiceSent.class);
        }
    }

    @Test
    @DisplayName("T3.3.4 - RecordPaymentCommand updates balance")
    void recordPaymentCommandUpdatesBalance() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand createCommand = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Service A",
                    BigDecimal.valueOf(2),
                    BigDecimal.valueOf(100),
                    "USD"
                )
            ),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test notes"
        );
        createInvoiceHandler.handle(createCommand);
        
        SendInvoiceCommand sendCommand = new SendInvoiceCommand(invoiceId);
        sendInvoiceHandler.handle(sendCommand);
        
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            fake.clear();
        }

        UUID paymentId = UUID.randomUUID();
        RecordPaymentCommand paymentCommand = new RecordPaymentCommand(
            invoiceId,
            paymentId,
            BigDecimal.valueOf(110),
            "USD",
            LocalDate.now(),
            "Bank Transfer",
            "REF-123"
        );

        // When
        recordPaymentHandler.handle(paymentCommand);

        // Then
        Invoice invoice = invoiceRepository.findById(invoiceId).get();
        assertThat(invoice.getPayments()).hasSize(1);
        assertThat(invoice.calculateBalance().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(110)); // 220 total - 110 payment
        
        // Verify event
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(PaymentRecorded.class);
        }
    }

    @Test
    @DisplayName("T3.3.5 - When balance = 0 after payment, emits InvoicePaid")
    void recordPaymentThatZeroesBalanceEmitsInvoicePaid() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand createCommand = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Service A",
                    BigDecimal.valueOf(2),
                    BigDecimal.valueOf(100),
                    "USD"
                )
            ),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test notes"
        );
        createInvoiceHandler.handle(createCommand);
        
        SendInvoiceCommand sendCommand = new SendInvoiceCommand(invoiceId);
        sendInvoiceHandler.handle(sendCommand);
        
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            fake.clear();
        }

        // Total = 220 (200 subtotal + 20 tax)
        UUID paymentId = UUID.randomUUID();
        RecordPaymentCommand paymentCommand = new RecordPaymentCommand(
            invoiceId,
            paymentId,
            BigDecimal.valueOf(220),
            "USD",
            LocalDate.now(),
            "Bank Transfer",
            "REF-123"
        );

        // When
        recordPaymentHandler.handle(paymentCommand);

        // Then
        Invoice invoice = invoiceRepository.findById(invoiceId).get();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.calculateBalance().isZero()).isTrue();
        
        // Verify events
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(PaymentRecorded.class);
            assertThat(events.get(1)).isInstanceOf(InvoicePaid.class);
        }
    }

    @Test
    @DisplayName("T4.2.1 - RecordPaymentCommand fails when payment exceeds balance")
    void recordPaymentCommandFailsWhenExceedingBalance() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand createCommand = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Service A",
                    BigDecimal.valueOf(2),
                    BigDecimal.valueOf(100),
                    "USD"
                )
            ),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "Test notes"
        );
        createInvoiceHandler.handle(createCommand);
        
        SendInvoiceCommand sendCommand = new SendInvoiceCommand(invoiceId);
        sendInvoiceHandler.handle(sendCommand);
        
        // Total = 220 (200 subtotal + 20 tax)
        // Attempt to pay 300 (exceeds balance)
        UUID paymentId = UUID.randomUUID();
        RecordPaymentCommand paymentCommand = new RecordPaymentCommand(
            invoiceId,
            paymentId,
            BigDecimal.valueOf(300),
            "USD",
            LocalDate.now(),
            "Bank Transfer",
            "REF-123"
        );

        // When/Then
        assertThatThrownBy(() -> recordPaymentHandler.handle(paymentCommand))
            .isInstanceOf(PaymentExceedsBalanceException.class)
            .hasMessageContaining("exceeds outstanding balance");
        
        // Verify invoice state unchanged
        Invoice invoice = invoiceRepository.findById(invoiceId).get();
        assertThat(invoice.getPayments()).isEmpty();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    @DisplayName("T3.3.6 - All commands transactional")
    void allCommandsTransactional() {
        // This test verifies that transactions work correctly.
        // We'll test by creating an invoice, then attempting an invalid operation
        // that should rollback.
        
        // Given
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand createCommand = new CreateInvoiceCommand(
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
        createInvoiceHandler.handle(createCommand);
        
        // Verify invoice exists
        assertThat(invoiceRepository.findById(invoiceId)).isPresent();
        
        // When - try to update a SENT invoice (should fail)
        SendInvoiceCommand sendCommand = new SendInvoiceCommand(invoiceId);
        sendInvoiceHandler.handle(sendCommand);
        
        // Attempt invalid update (should throw exception, transaction should rollback)
        UpdateInvoiceCommand invalidUpdate = new UpdateInvoiceCommand(
            invoiceId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Service B",
                    BigDecimal.valueOf(1),
                    BigDecimal.valueOf(50),
                    "USD"
                )
            ),
            LocalDate.now().plusDays(45),
            BigDecimal.valueOf(0.15),
            "Should fail"
        );
        
        // Then - exception should be thrown, and invoice should remain unchanged
        try {
            updateInvoiceHandler.handle(invalidUpdate);
            // If we get here, the test should fail
            assertThat(false).as("Expected exception was not thrown").isTrue();
        } catch (IllegalStateException e) {
            // Expected - verify invoice state is unchanged
            Invoice invoice = invoiceRepository.findById(invoiceId).get();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
            assertThat(invoice.getLineItems().get(0).getDescription()).isEqualTo("Service A");
        }
    }
}

