package com.invoiceme.customer.application.queries;

import com.invoiceme.customer.application.queries.dto.CustomerView;
import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.payment.domain.Payment;
import com.invoiceme.payment.domain.PaymentMethod;
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
 * Integration tests for OutstandingByCustomerHandler.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("OutstandingByCustomerHandler Tests")
@Transactional
class OutstandingByCustomerHandlerTest {

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
    private OutstandingByCustomerHandler handler;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID customerId1;
    private UUID customerId2;
    private Currency USD = Currency.getInstance("USD");

    @BeforeEach
    void setUp() {
        // Create first customer
        customerId1 = UUID.randomUUID();
        Customer customer1 = Customer.create(
            customerId1,
            "Customer A",
            Email.of("customerA@example.com"),
            new Address("123 Main St", "City", "12345", "US"),
            "555-1234",
            PaymentTerms.net30()
        );
        customerRepository.save(customer1);

        // Create second customer
        customerId2 = UUID.randomUUID();
        Customer customer2 = Customer.create(
            customerId2,
            "Customer B",
            Email.of("customerB@example.com"),
            new Address("456 Oak Ave", "Town", "67890", "US"),
            "555-5678",
            PaymentTerms.net30()
        );
        customerRepository.save(customer2);
    }

    @Test
    @DisplayName("Should calculate outstanding balance per customer")
    void shouldCalculateOutstandingBalancePerCustomer() {
        // Given
        LocalDate today = LocalDate.now();
        
        // Create invoice for customer 1: 500 subtotal + 50 tax = 550 total
        UUID invoiceId1 = UUID.randomUUID();
        Invoice invoice1 = Invoice.create(
            invoiceId1,
            customerId1,
            InvoiceNumber.generate(),
            today,
            today.plusDays(30),
            List.of(LineItem.of("Service A", BigDecimal.valueOf(5), Money.of(BigDecimal.valueOf(100), USD))),
            "Invoice 1",
            BigDecimal.valueOf(0.10)
        );
        invoice1.sendInvoice();
        // Record partial payment: 250
        invoice1.recordPayment(
            new Payment(
                UUID.randomUUID(),
                Money.of(BigDecimal.valueOf(250), USD),
                today,
                PaymentMethod.BANK_TRANSFER,
                "REF-123"
            )
        );
        invoiceRepository.save(invoice1);

        // Create invoice for customer 2: 200 subtotal + 20 tax = 220 total, no payments
        UUID invoiceId2 = UUID.randomUUID();
        Invoice invoice2 = Invoice.create(
            invoiceId2,
            customerId2,
            InvoiceNumber.generate(),
            today,
            today.plusDays(30),
            List.of(LineItem.of("Service B", BigDecimal.valueOf(2), Money.of(BigDecimal.valueOf(100), USD))),
            "Invoice 2",
            BigDecimal.valueOf(0.10)
        );
        invoice2.sendInvoice();
        invoiceRepository.save(invoice2);
        
        // Flush to ensure data is visible to JdbcTemplate
        entityManager.flush();
        entityManager.clear();

        // When
        List<CustomerView> result = handler.handle(new OutstandingByCustomerQuery());

        // Then
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        
        CustomerView customer1View = result.stream()
            .filter(view -> view.id().equals(customerId1))
            .findFirst()
            .orElseThrow();
        // Outstanding: 550 - 250 = 300
        assertThat(customer1View.outstandingBalance()).isEqualByComparingTo(BigDecimal.valueOf(300));
        
        CustomerView customer2View = result.stream()
            .filter(view -> view.id().equals(customerId2))
            .findFirst()
            .orElseThrow();
        // Outstanding: 220 - 0 = 220
        assertThat(customer2View.outstandingBalance()).isEqualByComparingTo(BigDecimal.valueOf(220));
    }

    @Test
    @DisplayName("Should return zero balance for customers with no invoices")
    void shouldReturnZeroBalanceForCustomersWithNoInvoices() {
        // Flush to ensure data is visible to JdbcTemplate
        entityManager.flush();
        entityManager.clear();
        
        // When
        List<CustomerView> result = handler.handle(new OutstandingByCustomerQuery());

        // Then
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result).allMatch(view -> 
            view.outstandingBalance().compareTo(BigDecimal.ZERO) == 0
        );
    }
}

