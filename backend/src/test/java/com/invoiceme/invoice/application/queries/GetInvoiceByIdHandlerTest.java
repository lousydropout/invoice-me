package com.invoiceme.invoice.application.queries;

import com.invoiceme.customer.domain.Customer;
import com.invoiceme.customer.domain.CustomerRepository;
import com.invoiceme.customer.domain.valueobjects.PaymentTerms;
import com.invoiceme.invoice.application.queries.dto.InvoiceDetailView;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.invoice.domain.LineItem;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.invoice.domain.valueobjects.InvoiceStatus;
import com.invoiceme.shared.application.errors.ApplicationError;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for GetInvoiceByIdHandler.
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("GetInvoiceByIdHandler Tests")
@Transactional
class GetInvoiceByIdHandlerTest {

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
    private GetInvoiceByIdHandler handler;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private UUID customerId;
    private UUID invoiceId;

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

        // Create invoice
        invoiceId = UUID.randomUUID();
        InvoiceNumber invoiceNumber = InvoiceNumber.generate();
        Currency USD = Currency.getInstance("USD");
        List<LineItem> lineItems = List.of(
            LineItem.of("Service A", BigDecimal.valueOf(5), Money.of(BigDecimal.valueOf(100), USD))
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
        invoiceRepository.save(invoice);
    }

    @Test
    @DisplayName("Should retrieve invoice by ID with full details")
    void shouldRetrieveInvoiceById() {
        // Given
        GetInvoiceByIdQuery query = new GetInvoiceByIdQuery(invoiceId);

        // When
        InvoiceDetailView result = handler.handle(query);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(invoiceId);
        assertThat(result.customerName()).isEqualTo("Test Customer");
        assertThat(result.customerEmail()).isEqualTo("test@example.com");
        assertThat(result.status()).isEqualTo(InvoiceStatus.SENT.name());
        assertThat(result.lineItems()).hasSize(1);
        assertThat(result.lineItems().get(0).description()).isEqualTo("Service A");
        assertThat(result.subtotal()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(result.tax()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(result.total()).isEqualByComparingTo(BigDecimal.valueOf(550));
        assertThat(result.balance()).isEqualByComparingTo(BigDecimal.valueOf(550));
    }

    @Test
    @DisplayName("Should throw exception when invoice not found")
    void shouldThrowExceptionWhenInvoiceNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        GetInvoiceByIdQuery query = new GetInvoiceByIdQuery(nonExistentId);

        // When/Then
        assertThatThrownBy(() -> handler.handle(query))
            .isInstanceOf(ApplicationError.class);
    }
}

