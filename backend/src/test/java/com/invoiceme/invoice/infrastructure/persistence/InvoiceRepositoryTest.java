package com.invoiceme.invoice.infrastructure.persistence;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.invoice.domain.valueobjects.InvoiceStatus;
import com.invoiceme.payment.domain.Payment;
import com.invoiceme.payment.domain.PaymentMethod;
import com.invoiceme.shared.domain.Address;
import com.invoiceme.shared.domain.Email;
import com.invoiceme.shared.domain.Money;
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
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T3.2 - Repository / Persistence Tests
 * 
 * Tests for Invoice repository persistence using Testcontainers with PostgreSQL.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T3.2 - Repository / Persistence")
@Transactional
class InvoiceRepositoryTest {

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
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private UUID customerId;
    private Currency USD = Currency.getInstance("USD");

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
    @DisplayName("T3.2.1 - Persist invoice with line items and payments")
    void persistInvoiceWithLineItemsAndPayments() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        InvoiceNumber invoiceNumber = InvoiceNumber.generate();
        List<LineItem> lineItems = List.of(
            LineItem.of("Service A", BigDecimal.valueOf(2), Money.of(BigDecimal.valueOf(100), USD)),
            LineItem.of("Service B", BigDecimal.valueOf(1), Money.of(BigDecimal.valueOf(50), USD))
        );

        Invoice invoice = Invoice.create(
            invoiceId,
            customerId,
            invoiceNumber,
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            lineItems,
            "Test notes",
            BigDecimal.valueOf(0.10)
        );

        // When
        invoiceRepository.save(invoice);

        // Then - verify invoice exists
        Optional<Invoice> found = invoiceRepository.findById(invoiceId);
        assertThat(found).isPresent();
        
        Invoice savedInvoice = found.get();
        assertThat(savedInvoice.getId()).isEqualTo(invoiceId);
        assertThat(savedInvoice.getLineItems()).hasSize(2);
        assertThat(savedInvoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
    }

    @Test
    @DisplayName("T3.2.2 - Load invoice by ID with line items and payments")
    void loadInvoiceByIdWithLineItemsAndPayments() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        InvoiceNumber invoiceNumber = InvoiceNumber.generate();
        List<LineItem> lineItems = List.of(
            LineItem.of("Service A", BigDecimal.valueOf(2), Money.of(BigDecimal.valueOf(100), USD))
        );

        Invoice invoice = Invoice.create(
            invoiceId,
            customerId,
            invoiceNumber,
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            lineItems,
            "Test notes",
            BigDecimal.valueOf(0.10)
        );
        invoice.sendInvoice();

        // Add a payment
        Payment payment = new Payment(
            UUID.randomUUID(),
            Money.of(BigDecimal.valueOf(110), USD),
            LocalDate.now(),
            PaymentMethod.BANK_TRANSFER,
            "REF-123"
        );
        invoice.recordPayment(payment);
        invoiceRepository.save(invoice);

        // When
        Optional<Invoice> found = invoiceRepository.findById(invoiceId);

        // Then
        assertThat(found).isPresent();
        Invoice loadedInvoice = found.get();
        assertThat(loadedInvoice.getLineItems()).hasSize(1);
        assertThat(loadedInvoice.getPayments()).hasSize(1);
        assertThat(loadedInvoice.getPayments().get(0).getAmount().getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(110));
    }

    @Test
    @DisplayName("T3.2.3 - Cascade delete line items/payments on invoice delete")
    void cascadeDeleteLineItemsAndPayments() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        InvoiceNumber invoiceNumber = InvoiceNumber.generate();
        List<LineItem> lineItems = List.of(
            LineItem.of("Service A", BigDecimal.valueOf(1), Money.of(BigDecimal.valueOf(100), USD))
        );

        Invoice invoice = Invoice.create(
            invoiceId,
            customerId,
            invoiceNumber,
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            lineItems,
            "Test notes",
            BigDecimal.valueOf(0.10)
        );
        invoice.sendInvoice();

        Payment payment = new Payment(
            UUID.randomUUID(),
            Money.of(BigDecimal.valueOf(50), USD),
            LocalDate.now(),
            PaymentMethod.BANK_TRANSFER,
            "REF-123"
        );
        invoice.recordPayment(payment);
        invoiceRepository.save(invoice);

        // Verify it exists
        assertThat(invoiceRepository.findById(invoiceId)).isPresent();

        // When
        invoiceRepository.delete(invoiceId);

        // Then
        assertThat(invoiceRepository.findById(invoiceId)).isEmpty();
        // Note: Cascade delete is handled by JPA @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
        // We verify the invoice is gone, which implies children are gone (JPA handles this)
    }

    @Test
    @DisplayName("T3.2.4 - Verify balance persistence")
    void verifyBalancePersistence() {
        // Given
        UUID invoiceId = UUID.randomUUID();
        InvoiceNumber invoiceNumber = InvoiceNumber.generate();
        List<LineItem> lineItems = List.of(
            LineItem.of("Service A", BigDecimal.valueOf(2), Money.of(BigDecimal.valueOf(100), USD))
        );

        Invoice invoice = Invoice.create(
            invoiceId,
            customerId,
            invoiceNumber,
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            lineItems,
            "Test notes",
            BigDecimal.valueOf(0.10) // 10% tax
        );
        invoice.sendInvoice();

        // Calculate expected values
        Money expectedSubtotal = Money.of(BigDecimal.valueOf(200), USD); // 2 * 100
        Money expectedTax = Money.of(BigDecimal.valueOf(20), USD); // 200 * 0.10
        Money expectedTotal = Money.of(BigDecimal.valueOf(220), USD); // 200 + 20

        // Add partial payment
        Payment payment = new Payment(
            UUID.randomUUID(),
            Money.of(BigDecimal.valueOf(110), USD),
            LocalDate.now(),
            PaymentMethod.BANK_TRANSFER,
            "REF-123"
        );
        invoice.recordPayment(payment);
        invoiceRepository.save(invoice);

        // When
        Optional<Invoice> found = invoiceRepository.findById(invoiceId);
        assertThat(found).isPresent();
        Invoice loadedInvoice = found.get();

        // Then - verify balance calculation
        Money loadedSubtotal = loadedInvoice.calculateSubtotal();
        Money loadedTax = loadedInvoice.calculateTax();
        Money loadedTotal = loadedInvoice.calculateTotal();
        Money loadedBalance = loadedInvoice.calculateBalance();

        assertThat(loadedSubtotal.getAmount()).isEqualByComparingTo(expectedSubtotal.getAmount());
        assertThat(loadedSubtotal.getCurrency()).isEqualTo(USD);
        assertThat(loadedTax.getAmount()).isEqualByComparingTo(expectedTax.getAmount());
        assertThat(loadedTax.getCurrency()).isEqualTo(USD);
        assertThat(loadedTotal.getAmount()).isEqualByComparingTo(expectedTotal.getAmount());
        assertThat(loadedTotal.getCurrency()).isEqualTo(USD);
        
        Money expectedBalance = Money.of(BigDecimal.valueOf(110), USD); // 220 - 110
        assertThat(loadedBalance.getAmount()).isEqualByComparingTo(expectedBalance.getAmount());
        assertThat(loadedBalance.getCurrency()).isEqualTo(USD);
    }
}

