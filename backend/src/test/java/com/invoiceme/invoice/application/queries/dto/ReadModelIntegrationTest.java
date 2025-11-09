package com.invoiceme.invoice.application.queries.dto;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for read model DTOs.
 * Verifies that DTOs can be correctly constructed from domain aggregates.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Read Model DTO Integration Tests")
@Transactional
class ReadModelIntegrationTest {

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

    private static final Currency USD = Currency.getInstance("USD");
    private UUID customerId;
    private Customer customer;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        // Create customer
        customerId = UUID.randomUUID();
        customer = Customer.create(
            customerId,
            "Test Customer",
            Email.of("test@example.com"),
            new Address("123 Main St", "City", "12345", "US"),
            "555-1234",
            PaymentTerms.net30()
        );
        customerRepository.save(customer);

        // Create invoice
        UUID invoiceId = UUID.randomUUID();
        InvoiceNumber invoiceNumber = InvoiceNumber.generate();
        List<LineItem> lineItems = List.of(
            LineItem.of("Service A", BigDecimal.valueOf(5), Money.of(BigDecimal.valueOf(100), USD))
        );
        invoice = Invoice.create(
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
        invoiceRepository.save(invoice);
    }

    @Test
    @DisplayName("Should create InvoiceSummaryView from domain Invoice")
    void shouldCreateInvoiceSummaryViewFromDomain() {
        // Given
        Invoice loadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        Money total = loadedInvoice.calculateTotal();
        Money balance = loadedInvoice.calculateBalance();

        // When
        InvoiceSummaryView summaryView = new InvoiceSummaryView(
            loadedInvoice.getId(),
            loadedInvoice.getInvoiceNumber().getValue(),
            loadedInvoice.getCustomerId(),
            customer.getName(),
            loadedInvoice.getStatus().name(),
            loadedInvoice.getIssueDate(),
            loadedInvoice.getDueDate(),
            total.getAmount(),
            balance.getAmount()
        );

        // Then
        assertThat(summaryView.id()).isEqualTo(loadedInvoice.getId());
        assertThat(summaryView.invoiceNumber()).isEqualTo(loadedInvoice.getInvoiceNumber().getValue());
        assertThat(summaryView.customerId()).isEqualTo(customerId);
        assertThat(summaryView.customerName()).isEqualTo("Test Customer");
        assertThat(summaryView.status()).isEqualTo(InvoiceStatus.SENT.name());
        assertThat(summaryView.total()).isEqualByComparingTo(total.getAmount());
        assertThat(summaryView.balance()).isEqualByComparingTo(balance.getAmount());
    }

    @Test
    @DisplayName("Should create InvoiceDetailView from domain Invoice with payments")
    void shouldCreateInvoiceDetailViewFromDomain() {
        // Given
        Invoice loadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        
        // Record a payment
        Payment payment = new Payment(
            UUID.randomUUID(),
            Money.of(BigDecimal.valueOf(250), USD),
            LocalDate.now(),
            PaymentMethod.BANK_TRANSFER,
            "REF-123"
        );
        loadedInvoice.recordPayment(payment);
        invoiceRepository.save(loadedInvoice);
        
        // Reload to get updated state
        loadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();

        // When
        List<InvoiceDetailView.LineItemView> lineItemViews = loadedInvoice.getLineItems().stream()
            .map(item -> new InvoiceDetailView.LineItemView(
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice().getAmount(),
                item.getSubtotal().getAmount()
            ))
            .toList();

        List<InvoiceDetailView.PaymentView> paymentViews = loadedInvoice.getPayments().stream()
            .map(p -> new InvoiceDetailView.PaymentView(
                p.getId(),
                p.getAmount().getAmount(),
                p.getPaymentDate(),
                p.getMethod().name(),
                p.getReference()
            ))
            .toList();

        InvoiceDetailView detailView = new InvoiceDetailView(
            loadedInvoice.getId(),
            loadedInvoice.getInvoiceNumber().getValue(),
            customer.getName(),
            customer.getEmail().getValue(),
            loadedInvoice.getIssueDate(),
            loadedInvoice.getDueDate(),
            loadedInvoice.getStatus().name(),
            loadedInvoice.calculateSubtotal().getAmount(),
            loadedInvoice.calculateTax().getAmount(),
            loadedInvoice.calculateTotal().getAmount(),
            loadedInvoice.calculateBalance().getAmount(),
            loadedInvoice.getNotes(),
            lineItemViews,
            paymentViews
        );

        // Then
        assertThat(detailView.id()).isEqualTo(loadedInvoice.getId());
        assertThat(detailView.customerName()).isEqualTo("Test Customer");
        assertThat(detailView.customerEmail()).isEqualTo("test@example.com");
        assertThat(detailView.status()).isEqualTo(InvoiceStatus.SENT.name());
        assertThat(detailView.lineItems()).hasSize(1);
        assertThat(detailView.payments()).hasSize(1);
        assertThat(detailView.payments().get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(detailView.payments().get(0).method()).isEqualTo("BANK_TRANSFER");
    }
}

