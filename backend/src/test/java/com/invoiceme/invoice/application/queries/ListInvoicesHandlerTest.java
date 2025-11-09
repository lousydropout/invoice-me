package com.invoiceme.invoice.application.queries;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.application.queries.dto.InvoiceSummaryView;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
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
 * Unit tests for ListInvoicesHandler.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("ListInvoicesHandler Tests")
@Transactional
class ListInvoicesHandlerTest {

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
    private ListInvoicesHandler handler;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID customerId;

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
    @DisplayName("Should list all invoices")
    void shouldListAllInvoices() {
        // Given
        Currency USD = Currency.getInstance("USD");
        
        // Create first invoice
        UUID invoiceId1 = UUID.randomUUID();
        Invoice invoice1 = Invoice.create(
            invoiceId1,
            customerId,
            InvoiceNumber.generate(),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            List.of(LineItem.of("Service A", BigDecimal.valueOf(2), Money.of(BigDecimal.valueOf(100), USD))),
            "Notes 1",
            BigDecimal.valueOf(0.10)
        );
        invoice1.sendInvoice();
        invoiceRepository.save(invoice1);

        // Create second invoice
        UUID invoiceId2 = UUID.randomUUID();
        Invoice invoice2 = Invoice.create(
            invoiceId2,
            customerId,
            InvoiceNumber.generate(),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            List.of(LineItem.of("Service B", BigDecimal.valueOf(3), Money.of(BigDecimal.valueOf(50), USD))),
            "Notes 2",
            BigDecimal.valueOf(0.10)
        );
        invoice2.sendInvoice();
        invoiceRepository.save(invoice2);
        
        // Flush to ensure data is visible to JdbcTemplate
        entityManager.flush();
        entityManager.clear();

        // When
        List<InvoiceSummaryView> result = handler.handle(new ListInvoicesQuery());

        // Then
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result).anyMatch(view -> view.id().equals(invoiceId1));
        assertThat(result).anyMatch(view -> view.id().equals(invoiceId2));
        assertThat(result).allMatch(view -> view.customerName().equals("Test Customer"));
    }

    @Test
    @DisplayName("Should return empty list when no invoices exist")
    void shouldReturnEmptyListWhenNoInvoices() {
        // When
        List<InvoiceSummaryView> result = handler.handle(new ListInvoicesQuery());

        // Then
        assertThat(result).isEmpty();
    }
}

