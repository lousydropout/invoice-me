package com.invoiceme.invoice.application.commands;

import com.invoiceme.customer.application.commands.CreateCustomerCommand;
import com.invoiceme.customer.application.commands.CreateCustomerHandler;
import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.events.InvoiceCreated;
import com.invoiceme.invoice.domain.events.InvoicePaid;
import com.invoiceme.invoice.domain.events.InvoiceSent;
import com.invoiceme.invoice.domain.events.PaymentRecorded;
import com.invoiceme.invoice.domain.valueobjects.InvoiceStatus;
import com.invoiceme.shared.application.bus.DomainEventPublisher;
import com.invoiceme.shared.domain.DomainEvent;
import com.invoiceme.shared.test.FakeDomainEventPublisher;
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

/**
 * End-to-end integration test for the complete invoice flow.
 * Tests: Create customer → Create invoice → Send invoice → Record payment
 * 
 * This test verifies the full persistence and CQRS flow as required by Task 7.2.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("End-to-End Invoice Flow Integration Test")
@Transactional
@org.springframework.context.annotation.Import(EndToEndInvoiceFlowTest.TestConfig.class)
class EndToEndInvoiceFlowTest {

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
    private CreateCustomerHandler createCustomerHandler;

    @Autowired
    private CreateInvoiceHandler createInvoiceHandler;

    @Autowired
    private SendInvoiceHandler sendInvoiceHandler;

    @Autowired
    private RecordPaymentHandler recordPaymentHandler;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private DomainEventPublisher eventPublisher;

    @Test
    @DisplayName("End-to-end: Create customer → invoice → send → record payment")
    void endToEndInvoiceFlow() {
        // Step 1: Create Customer
        UUID customerId = UUID.randomUUID();
        CreateCustomerCommand createCustomerCommand = new CreateCustomerCommand(
            customerId,
            "End-to-End Test Customer",
            "e2e@example.com",
            "123 E2E St",
            "E2E City",
            "12345",
            "US",
            "555-E2E",
            "NET_30"
        );
        UUID createdCustomerId = createCustomerHandler.handle(createCustomerCommand);
        assertThat(createdCustomerId).isEqualTo(customerId);

        // Verify customer is persisted
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new AssertionError("Customer should be persisted"));
        assertThat(customer.getName()).isEqualTo("End-to-End Test Customer");
        assertThat(customer.getEmail().getValue()).isEqualTo("e2e@example.com");

        // Clear events after customer creation
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            fake.clear();
        }

        // Step 2: Create Invoice
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceCommand createInvoiceCommand = new CreateInvoiceCommand(
            invoiceId,
            customerId,
            List.of(
                new CreateInvoiceCommand.LineItemDto(
                    "Consulting Services",
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(150),
                    "USD"
                ),
                new CreateInvoiceCommand.LineItemDto(
                    "Development Services",
                    BigDecimal.valueOf(5),
                    BigDecimal.valueOf(200),
                    "USD"
                )
            ),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            BigDecimal.valueOf(0.10),
            "End-to-end test invoice"
        );
        UUID createdInvoiceId = createInvoiceHandler.handle(createInvoiceCommand);
        assertThat(createdInvoiceId).isEqualTo(invoiceId);

        // Verify invoice is persisted in DRAFT status
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should be persisted"));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.getCustomerId()).isEqualTo(customerId);
        assertThat(invoice.getLineItems()).hasSize(2);
        
        // Verify totals: (10 * 150) + (5 * 200) = 1500 + 1000 = 2500 subtotal
        // Tax: 2500 * 0.10 = 250
        // Total: 2500 + 250 = 2750
        assertThat(invoice.calculateSubtotal().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(2500));
        assertThat(invoice.calculateTotal().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(2750));

        // Verify InvoiceCreated event
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(InvoiceCreated.class);
            fake.clear();
        }

        // Step 3: Send Invoice
        SendInvoiceCommand sendInvoiceCommand = new SendInvoiceCommand(invoiceId);
        sendInvoiceHandler.handle(sendInvoiceCommand);

        // Verify invoice status changed to SENT
        invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist"));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);

        // Verify InvoiceSent event
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(InvoiceSent.class);
            fake.clear();
        }

        // Step 4: Record Payment (partial)
        UUID paymentId1 = UUID.randomUUID();
        RecordPaymentCommand recordPaymentCommand1 = new RecordPaymentCommand(
            invoiceId,
            paymentId1,
            BigDecimal.valueOf(1000),
            "USD",
            LocalDate.now(),
            "Bank Transfer",
            "E2E-REF-001"
        );
        recordPaymentHandler.handle(recordPaymentCommand1);

        // Verify payment is recorded
        invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist"));
        assertThat(invoice.getPayments()).hasSize(1);
        assertThat(invoice.calculateBalance().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(1750)); // 2750 - 1000

        // Verify PaymentRecorded event
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(PaymentRecorded.class);
            fake.clear();
        }

        // Step 5: Record Final Payment (completing the invoice)
        UUID paymentId2 = UUID.randomUUID();
        RecordPaymentCommand recordPaymentCommand2 = new RecordPaymentCommand(
            invoiceId,
            paymentId2,
            BigDecimal.valueOf(1750),
            "USD",
            LocalDate.now(),
            "Credit Card",
            "E2E-REF-002"
        );
        recordPaymentHandler.handle(recordPaymentCommand2);

        // Verify invoice is now PAID
        invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist"));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPayments()).hasSize(2);
        assertThat(invoice.calculateBalance().isZero()).isTrue();

        // Verify both PaymentRecorded and InvoicePaid events
        if (eventPublisher instanceof FakeDomainEventPublisher fake) {
            List<DomainEvent> events = fake.getPublishedEvents();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(PaymentRecorded.class);
            assertThat(events.get(1)).isInstanceOf(InvoicePaid.class);
        }

        // Final verification: Database state
        // Verify customer still exists
        Customer finalCustomer = customerRepository.findById(customerId)
            .orElseThrow(() -> new AssertionError("Customer should still exist"));
        assertThat(finalCustomer.getName()).isEqualTo("End-to-End Test Customer");

        // Verify invoice final state
        Invoice finalInvoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new AssertionError("Invoice should exist"));
        assertThat(finalInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(finalInvoice.getPayments()).hasSize(2);
        assertThat(finalInvoice.calculateBalance().isZero()).isTrue();
        assertThat(finalInvoice.calculateTotal().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(2750));
    }
}

