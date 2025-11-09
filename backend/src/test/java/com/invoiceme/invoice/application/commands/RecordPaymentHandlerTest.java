package com.invoiceme.invoice.application.commands;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.exceptions.PaymentExceedsBalanceException;
import com.invoiceme.invoice.domain.events.InvoicePaid;
import com.invoiceme.invoice.domain.events.PaymentRecorded;
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
 * Integration tests for RecordPaymentHandler.
 * Tests full persistence and event publishing with Testcontainers.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("RecordPaymentHandler Integration Tests")
@Transactional
@org.springframework.context.annotation.Import(RecordPaymentHandlerTest.TestConfig.class)
class RecordPaymentHandlerTest {

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
    private RecordPaymentHandler handler;

    @Autowired
    private CreateInvoiceHandler createInvoiceHandler;

    @Autowired
    private SendInvoiceHandler sendInvoiceHandler;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DomainEventPublisher eventPublisher;

    private UUID customerId;
    private UUID invoiceId;

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

        // Create and send an invoice
        invoiceId = UUID.randomUUID();
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
        
        // Clear events
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            fake.clear();
        }
    }

    @Test
    @DisplayName("Should record payment and persist to database")
    void shouldRecordPaymentAndPersistToDatabase() {
        // Given
        UUID paymentId = UUID.randomUUID();
        RecordPaymentCommand command = new RecordPaymentCommand(
            invoiceId,
            paymentId,
            BigDecimal.valueOf(110),
            "USD",
            LocalDate.now(),
            "Bank Transfer",
            "REF-123"
        );

        // When
        handler.handle(command);

        // Then
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist"));
        
        assertThat(invoice.getPayments()).hasSize(1);
        assertThat(invoice.getPayments().get(0).getId()).isEqualTo(paymentId);
        assertThat(invoice.getPayments().get(0).getAmount().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(110));
        assertThat(invoice.getPayments().get(0).getReference()).isEqualTo("REF-123");
        
        // Balance should be updated: 220 total - 110 payment = 110 remaining
        assertThat(invoice.calculateBalance().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(110));
    }

    @Test
    @DisplayName("Should publish PaymentRecorded event")
    void shouldPublishPaymentRecordedEvent() {
        // Given
        UUID paymentId = UUID.randomUUID();
        RecordPaymentCommand command = new RecordPaymentCommand(
            invoiceId,
            paymentId,
            BigDecimal.valueOf(110),
            "USD",
            LocalDate.now(),
            "Bank Transfer",
            "REF-123"
        );

        // When
        handler.handle(command);

        // Then
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(PaymentRecorded.class);
            
            PaymentRecorded event = (PaymentRecorded) events.get(0);
            assertThat(event.invoiceId()).isEqualTo(invoiceId);
            assertThat(event.paymentId()).isEqualTo(paymentId);
            assertThat(event.amount()).isEqualByComparingTo(BigDecimal.valueOf(110));
        }
    }

    @Test
    @DisplayName("Should publish InvoicePaid event when balance becomes zero")
    void shouldPublishInvoicePaidEventWhenBalanceBecomesZero() {
        // Given - invoice total is 220 (200 subtotal + 20 tax)
        UUID paymentId = UUID.randomUUID();
        RecordPaymentCommand command = new RecordPaymentCommand(
            invoiceId,
            paymentId,
            BigDecimal.valueOf(220),
            "USD",
            LocalDate.now(),
            "Bank Transfer",
            "REF-123"
        );

        // When
        handler.handle(command);

        // Then
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist"));
        
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.calculateBalance().isZero()).isTrue();
        
        // Verify events
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(PaymentRecorded.class);
            assertThat(events.get(1)).isInstanceOf(InvoicePaid.class);
            
            InvoicePaid paidEvent = (InvoicePaid) events.get(1);
            assertThat(paidEvent.invoiceId()).isEqualTo(invoiceId);
        }
    }

    @Test
    @DisplayName("Should fail when payment exceeds balance")
    void shouldFailWhenPaymentExceedsBalance() {
        // Given - invoice total is 220, attempt to pay 300
        UUID paymentId = UUID.randomUUID();
        RecordPaymentCommand command = new RecordPaymentCommand(
            invoiceId,
            paymentId,
            BigDecimal.valueOf(300),
            "USD",
            LocalDate.now(),
            "Bank Transfer",
            "REF-123"
        );

        // When/Then
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(PaymentExceedsBalanceException.class)
            .hasMessageContaining("exceeds outstanding balance");
        
        // Verify invoice state unchanged
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist"));
        assertThat(invoice.getPayments()).isEmpty();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    @DisplayName("Should fail when invoice does not exist")
    void shouldFailWhenInvoiceDoesNotExist() {
        // Given
        UUID nonExistentInvoiceId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        RecordPaymentCommand command = new RecordPaymentCommand(
            nonExistentInvoiceId,
            paymentId,
            BigDecimal.valueOf(100),
            "USD",
            LocalDate.now(),
            "Bank Transfer",
            "REF-123"
        );

        // When/Then
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(ApplicationError.class)
            .hasMessageContaining("Invoice");
    }

    @Test
    @DisplayName("Should verify database state after payment")
    void shouldVerifyDatabaseStateAfterPayment() {
        // Given
        UUID paymentId = UUID.randomUUID();
        LocalDate paymentDate = LocalDate.of(2024, 1, 20);
        RecordPaymentCommand command = new RecordPaymentCommand(
            invoiceId,
            paymentId,
            BigDecimal.valueOf(110),
            "USD",
            paymentDate,
            "Credit Card",
            "CC-REF-456"
        );

        // When
        handler.handle(command);

        // Then - verify database state
        Invoice savedInvoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist in database"));
        
        assertThat(savedInvoice.getPayments()).hasSize(1);
        var payment = savedInvoice.getPayments().get(0);
        assertThat(payment.getId()).isEqualTo(paymentId);
        assertThat(payment.getAmount().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(110));
        assertThat(payment.getPaymentDate()).isEqualTo(paymentDate);
        assertThat(payment.getReference()).isEqualTo("CC-REF-456");
        
        // Verify balance is updated
        assertThat(savedInvoice.calculateBalance().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(110));
    }

    @Test
    @DisplayName("Should handle multiple payments correctly")
    void shouldHandleMultiplePaymentsCorrectly() {
        // Given - invoice total is 220
        UUID paymentId1 = UUID.randomUUID();
        RecordPaymentCommand command1 = new RecordPaymentCommand(
            invoiceId,
            paymentId1,
            BigDecimal.valueOf(100),
            "USD",
            LocalDate.now(),
            "Bank Transfer",
            "REF-1"
        );

        // When - first payment
        handler.handle(command1);

        // Then
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist"));
        assertThat(invoice.getPayments()).hasSize(1);
        assertThat(invoice.calculateBalance().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(120)); // 220 - 100

        // Clear events for second payment
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            fake.clear();
        }

        // When - second payment
        UUID paymentId2 = UUID.randomUUID();
        RecordPaymentCommand command2 = new RecordPaymentCommand(
            invoiceId,
            paymentId2,
            BigDecimal.valueOf(120),
            "USD",
            LocalDate.now(),
            "Credit Card",
            "REF-2"
        );
        handler.handle(command2);

        // Then
        invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist"));
        assertThat(invoice.getPayments()).hasSize(2);
        assertThat(invoice.calculateBalance().isZero()).isTrue();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }
}

