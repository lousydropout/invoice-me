package com.invoiceme.invoice.application.queries;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.application.queries.dto.InvoiceSummaryView;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.invoice.domain.valueobjects.InvoiceStatus;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
import com.invoiceme.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
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
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ListOverdueInvoicesHandler.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("ListOverdueInvoicesHandler Tests")
@Transactional
class ListOverdueInvoicesHandlerTest {

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
    private ListOverdueInvoicesHandler handler;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID customerId;
    private Currency USD = Currency.getInstance("USD");

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
    }

    @Test
    @DisplayName("Should list only overdue invoices")
    void shouldListOnlyOverdueInvoices() {
        // Given
        LocalDate today = LocalDate.now();
        
        // Create overdue invoice (due date in the past)
        UUID overdueInvoiceId = UUID.randomUUID();
        Invoice overdueInvoice = Invoice.create(
            overdueInvoiceId,
            customerId,
            InvoiceNumber.generate(),
            today.minusDays(10),
            today.minusDays(5), // Overdue
            List.of(LineItem.of("Service A", BigDecimal.valueOf(2), Money.of(BigDecimal.valueOf(100), USD))),
            "Overdue invoice",
            BigDecimal.valueOf(0.10)
        );
        overdueInvoice.sendInvoice();
        invoiceRepository.save(overdueInvoice);

        // Create current invoice (not overdue)
        UUID currentInvoiceId = UUID.randomUUID();
        Invoice currentInvoice = Invoice.create(
            currentInvoiceId,
            customerId,
            InvoiceNumber.generate(),
            today,
            today.plusDays(30), // Not overdue
            List.of(LineItem.of("Service B", BigDecimal.valueOf(1), Money.of(BigDecimal.valueOf(50), USD))),
            "Current invoice",
            BigDecimal.valueOf(0.10)
        );
        currentInvoice.sendInvoice();
        invoiceRepository.save(currentInvoice);
        
        // Flush to ensure data is visible to JdbcTemplate
        entityManager.flush();
        entityManager.clear();

        // When
        List<InvoiceSummaryView> result = handler.handle(new ListOverdueInvoicesQuery());

        // Then
        assertThat(result).hasSizeGreaterThanOrEqualTo(1);
        assertThat(result).anyMatch(view -> view.id().equals(overdueInvoiceId));
        assertThat(result).noneMatch(view -> view.id().equals(currentInvoiceId));
        assertThat(result).allMatch(view -> 
            view.status() != InvoiceStatus.PAID.name() && 
            view.dueDate().isBefore(LocalDate.now())
        );
    }

    @Test
    @DisplayName("Should not include paid invoices even if overdue")
    void shouldNotIncludePaidInvoices() {
        // Given
        LocalDate today = LocalDate.now();
        
        // Create and pay an invoice (was overdue but now paid)
        UUID paidInvoiceId = UUID.randomUUID();
        Invoice paidInvoice = Invoice.create(
            paidInvoiceId,
            customerId,
            InvoiceNumber.generate(),
            today.minusDays(10),
            today.minusDays(5), // Was overdue
            List.of(LineItem.of("Service A", BigDecimal.valueOf(1), Money.of(BigDecimal.valueOf(100), USD))),
            "Paid invoice",
            BigDecimal.valueOf(0.10)
        );
        paidInvoice.sendInvoice();
        // Mark as paid by recording full payment
        paidInvoice.recordPayment(
            new com.invoiceme.payment.domain.Payment(
                UUID.randomUUID(),
                paidInvoice.calculateTotal(),
                today,
                com.invoiceme.payment.domain.PaymentMethod.BANK_TRANSFER,
                "REF-123"
            )
        );
        invoiceRepository.save(paidInvoice);
        
        // Flush to ensure data is visible to JdbcTemplate
        entityManager.flush();
        entityManager.clear();

        // When
        List<InvoiceSummaryView> result = handler.handle(new ListOverdueInvoicesQuery());

        // Then
        assertThat(result).noneMatch(view -> view.id().equals(paidInvoiceId));
    }
}

