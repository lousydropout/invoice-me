package com.invoiceme.shared.infrastructure.persistence;

import com.invoiceme.customer.application.commands.CreateCustomerCommand;
import com.invoiceme.customer.application.commands.CreateCustomerHandler;
import com.invoiceme.customer.infrastructure.persistence.CustomerJpaRepository;
import com.invoiceme.invoice.application.commands.CreateInvoiceCommand;
import com.invoiceme.invoice.application.commands.CreateInvoiceHandler;
import com.invoiceme.invoice.application.commands.SendInvoiceCommand;
import com.invoiceme.invoice.application.commands.SendInvoiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Seeds demo data for local development and testing.
 * 
 * Only runs when:
 * - The "dev" profile is active
 * - The database is empty (no customers exist)
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    
    private final CustomerJpaRepository customerJpaRepository;
    private final CreateCustomerHandler createCustomerHandler;
    private final CreateInvoiceHandler createInvoiceHandler;
    private final SendInvoiceHandler sendInvoiceHandler;

    public DevDataSeeder(
            CustomerJpaRepository customerJpaRepository,
            CreateCustomerHandler createCustomerHandler,
            CreateInvoiceHandler createInvoiceHandler,
            SendInvoiceHandler sendInvoiceHandler
    ) {
        this.customerJpaRepository = customerJpaRepository;
        this.createCustomerHandler = createCustomerHandler;
        this.createInvoiceHandler = createInvoiceHandler;
        this.sendInvoiceHandler = sendInvoiceHandler;
    }

    @Override
    public void run(String... args) {
        // Only seed if database is empty
        long customerCount = customerJpaRepository.count();
        if (customerCount > 0) {
            log.info("Database already contains {} customer(s). Skipping data seeding.", customerCount);
            return;
        }

        log.info("Database is empty. Seeding demo data...");

        try {
            // Create demo customers
            UUID acmeId = UUID.randomUUID();
            UUID techCorpId = UUID.randomUUID();
            UUID startupId = UUID.randomUUID();

            createCustomerHandler.handle(new CreateCustomerCommand(
                acmeId,
                "Acme Corporation",
                "billing@acme.com",
                "123 Business Ave",
                "New York",
                "10001",
                "US",
                "555-0100",
                "NET_30"
            ));
            log.info("Created demo customer: Acme Corporation");

            createCustomerHandler.handle(new CreateCustomerCommand(
                techCorpId,
                "TechCorp Solutions",
                "accounts@techcorp.com",
                "456 Innovation Drive",
                "San Francisco",
                "94102",
                "US",
                "555-0200",
                "NET_15"
            ));
            log.info("Created demo customer: TechCorp Solutions");

            createCustomerHandler.handle(new CreateCustomerCommand(
                startupId,
                "Startup Inc",
                "finance@startup.com",
                "789 Startup Blvd",
                "Austin",
                "78701",
                "US",
                "555-0300",
                "DUE_ON_RECEIPT"
            ));
            log.info("Created demo customer: Startup Inc");

            // Create demo invoices for Acme Corporation
            UUID invoice1Id = UUID.randomUUID();
            createInvoiceHandler.handle(new CreateInvoiceCommand(
                invoice1Id,
                acmeId,
                List.of(
                    new CreateInvoiceCommand.LineItemDto(
                        "Web Development Services",
                        BigDecimal.valueOf(40),
                        BigDecimal.valueOf(150.00),
                        "USD"
                    ),
                    new CreateInvoiceCommand.LineItemDto(
                        "UI/UX Design",
                        BigDecimal.valueOf(20),
                        BigDecimal.valueOf(125.00),
                        "USD"
                    )
                ),
                LocalDate.now().minusDays(10),
                LocalDate.now().plusDays(20),
                BigDecimal.valueOf(0.10), // 10% tax
                "Q4 2024 project deliverables"
            ));
            sendInvoiceHandler.handle(new SendInvoiceCommand(invoice1Id));
            log.info("Created and sent invoice for Acme Corporation");

            UUID invoice2Id = UUID.randomUUID();
            createInvoiceHandler.handle(new CreateInvoiceCommand(
                invoice2Id,
                acmeId,
                List.of(
                    new CreateInvoiceCommand.LineItemDto(
                        "Consulting Services",
                        BigDecimal.valueOf(15),
                        BigDecimal.valueOf(200.00),
                        "USD"
                    )
                ),
                LocalDate.now().minusDays(45),
                LocalDate.now().minusDays(15), // Overdue invoice
                BigDecimal.valueOf(0.08), // 8% tax
                "Strategic planning consultation"
            ));
            sendInvoiceHandler.handle(new SendInvoiceCommand(invoice2Id));
            log.info("Created overdue invoice for Acme Corporation");

            // Create demo invoice for TechCorp Solutions
            UUID invoice3Id = UUID.randomUUID();
            createInvoiceHandler.handle(new CreateInvoiceCommand(
                invoice3Id,
                techCorpId,
                List.of(
                    new CreateInvoiceCommand.LineItemDto(
                        "Cloud Infrastructure Setup",
                        BigDecimal.valueOf(1),
                        BigDecimal.valueOf(5000.00),
                        "USD"
                    ),
                    new CreateInvoiceCommand.LineItemDto(
                        "Monthly Maintenance",
                        BigDecimal.valueOf(3),
                        BigDecimal.valueOf(500.00),
                        "USD"
                    )
                ),
                LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(10),
                BigDecimal.valueOf(0.10), // 10% tax
                "Infrastructure setup and 3 months maintenance"
            ));
            sendInvoiceHandler.handle(new SendInvoiceCommand(invoice3Id));
            log.info("Created and sent invoice for TechCorp Solutions");

            log.info("Demo data seeding completed successfully!");
            log.info("Created 3 customers and 3 invoices (1 overdue)");

        } catch (Exception e) {
            log.error("Failed to seed demo data", e);
            throw new RuntimeException("Data seeding failed", e);
        }
    }
}

