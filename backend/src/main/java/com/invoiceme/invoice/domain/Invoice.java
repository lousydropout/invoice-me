package com.invoiceme.invoice.domain;

import com.invoiceme.invoice.domain.events.InvoiceCreated;
import com.invoiceme.invoice.domain.events.InvoicePaid;
import com.invoiceme.invoice.domain.events.InvoiceSent;
import com.invoiceme.invoice.domain.events.InvoiceUpdated;
import com.invoiceme.invoice.domain.events.PaymentRecorded;
import com.invoiceme.invoice.domain.exceptions.PaymentExceedsBalanceException;
import com.invoiceme.invoice.domain.valueobjects.InvoiceNumber;
import com.invoiceme.invoice.domain.valueobjects.InvoiceStatus;
import com.invoiceme.payment.domain.Payment;
import com.invoiceme.shared.domain.DomainEvent;
import com.invoiceme.shared.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Invoice aggregate root.
 * 
 * Represents a bill sent from the business to a customer.
 * 
 * Invariants:
 * - Only editable while status == DRAFT
 * - sum(payments.amount) ≤ total
 * - When balance < 0.01 → status = PAID (accounts for overpayment and rounding)
 */
public class Invoice {
    private final UUID id;
    private final UUID customerId;
    private final InvoiceNumber invoiceNumber;
    private final LocalDate issueDate;
    private LocalDate dueDate;
    private InvoiceStatus status;
    private final List<LineItem> lineItems;
    private final List<Payment> payments;
    private String notes;
    private BigDecimal taxRate; // e.g., 0.10 for 10%
    
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Invoice(
        UUID id,
        UUID customerId,
        InvoiceNumber invoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        InvoiceStatus status,
        List<LineItem> lineItems,
        List<Payment> payments,
        String notes,
        BigDecimal taxRate
    ) {
        if (id == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (invoiceNumber == null) {
            throw new IllegalArgumentException("Invoice number cannot be null");
        }
        if (issueDate == null) {
            throw new IllegalArgumentException("Issue date cannot be null");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("Due date cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Invoice status cannot be null");
        }
        if (lineItems == null) {
            throw new IllegalArgumentException("Line items cannot be null");
        }
        if (payments == null) {
            throw new IllegalArgumentException("Payments cannot be null");
        }
        if (taxRate == null) {
            throw new IllegalArgumentException("Tax rate cannot be null");
        }
        if (taxRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Tax rate cannot be negative");
        }
        
        this.id = id;
        this.customerId = customerId;
        this.invoiceNumber = invoiceNumber;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.status = status;
        this.lineItems = new ArrayList<>(lineItems);
        this.payments = new ArrayList<>(payments);
        this.notes = notes;
        this.taxRate = taxRate;
    }

    /**
     * Creates a new Invoice aggregate.
     * Emits InvoiceCreated event.
     * 
     * @param id the invoice ID
     * @param customerId the customer ID
     * @param invoiceNumber the invoice number
     * @param issueDate the issue date
     * @param dueDate the due date
     * @param lineItems the line items
     * @param notes optional notes
     * @param taxRate the tax rate (e.g., 0.10 for 10%)
     * @return a new Invoice instance
     */
    public static Invoice create(
        UUID id,
        UUID customerId,
        InvoiceNumber invoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        List<LineItem> lineItems,
        String notes,
        BigDecimal taxRate
    ) {
        if (lineItems == null || lineItems.isEmpty()) {
            throw new IllegalArgumentException("Invoice must have at least one line item");
        }
        
        Invoice invoice = new Invoice(
            id,
            customerId,
            invoiceNumber,
            issueDate,
            dueDate,
            InvoiceStatus.DRAFT,
            lineItems,
            Collections.emptyList(),
            notes,
            taxRate
        );
        
        invoice.domainEvents.add(new InvoiceCreated(
            invoice.id,
            invoice.customerId,
            invoice.invoiceNumber.getValue(),
            Instant.now()
        ));
        
        return invoice;
    }

    /**
     * Reconstructs an Invoice aggregate from existing data (e.g., from database).
     * Does NOT emit events - used for loading existing aggregates.
     * 
     * @param id the invoice ID
     * @param customerId the customer ID
     * @param invoiceNumber the invoice number
     * @param issueDate the issue date
     * @param dueDate the due date
     * @param status the invoice status
     * @param lineItems the line items
     * @param payments the payments
     * @param notes optional notes
     * @param taxRate the tax rate
     * @return an Invoice instance without events
     */
    public static Invoice reconstruct(
        UUID id,
        UUID customerId,
        InvoiceNumber invoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        InvoiceStatus status,
        List<LineItem> lineItems,
        List<Payment> payments,
        String notes,
        BigDecimal taxRate
    ) {
        return new Invoice(
            id,
            customerId,
            invoiceNumber,
            issueDate,
            dueDate,
            status,
            lineItems,
            payments,
            notes,
            taxRate
        );
    }

    /**
     * Adds a line item to the invoice.
     * Only allowed if status is DRAFT.
     * 
     * @param lineItem the line item to add
     */
    public void addLineItem(LineItem lineItem) {
        if (lineItem == null) {
            throw new IllegalArgumentException("Line item cannot be null");
        }
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Cannot add line items to invoice with status: " + status);
        }
        
        lineItems.add(lineItem);
        domainEvents.add(new InvoiceUpdated(id, Instant.now()));
    }

    /**
     * Updates the line items of the invoice.
     * Only allowed if status is DRAFT.
     * 
     * @param newLineItems the new list of line items
     */
    public void updateLineItems(List<LineItem> newLineItems) {
        if (newLineItems == null || newLineItems.isEmpty()) {
            throw new IllegalArgumentException("Invoice must have at least one line item");
        }
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Cannot update line items on invoice with status: " + status);
        }
        
        lineItems.clear();
        lineItems.addAll(newLineItems);
        domainEvents.add(new InvoiceUpdated(id, Instant.now()));
    }

    /**
     * Updates the due date.
     * Only allowed if status is DRAFT.
     * 
     * @param newDueDate the new due date
     */
    public void updateDueDate(LocalDate newDueDate) {
        if (newDueDate == null) {
            throw new IllegalArgumentException("Due date cannot be null");
        }
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Cannot update due date on invoice with status: " + status);
        }
        
        this.dueDate = newDueDate;
        domainEvents.add(new InvoiceUpdated(id, Instant.now()));
    }

    /**
     * Updates the tax rate.
     * Only allowed if status is DRAFT.
     * 
     * @param newTaxRate the new tax rate
     */
    public void updateTaxRate(BigDecimal newTaxRate) {
        if (newTaxRate == null) {
            throw new IllegalArgumentException("Tax rate cannot be null");
        }
        if (newTaxRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Tax rate cannot be negative");
        }
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Cannot update tax rate on invoice with status: " + status);
        }
        
        this.taxRate = newTaxRate;
        domainEvents.add(new InvoiceUpdated(id, Instant.now()));
    }

    /**
     * Updates the notes.
     * Only allowed if status is DRAFT.
     * 
     * @param newNotes the new notes
     */
    public void updateNotes(String newNotes) {
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Cannot update notes on invoice with status: " + status);
        }
        
        this.notes = newNotes;
        domainEvents.add(new InvoiceUpdated(id, Instant.now()));
    }

    /**
     * Sends the invoice to the customer.
     * Transitions status from DRAFT to SENT.
     */
    public void sendInvoice() {
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Can only send invoices with DRAFT status. Current status: " + status);
        }
        
        this.status = InvoiceStatus.SENT;
        domainEvents.add(new InvoiceSent(id, customerId, invoiceNumber.getValue(), Instant.now()));
    }

    /**
     * Records a payment against this invoice.
     * Enforces that payment amount does not exceed outstanding balance.
     * May trigger InvoicePaid event if balance reaches zero.
     * 
     * @param payment the payment to record
     */
    public void recordPayment(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment cannot be null");
        }
        if (status == InvoiceStatus.PAID) {
            throw new IllegalStateException("Cannot record payment on already paid invoice");
        }
        
        Money balance = calculateBalance();
        // Round balance to pennies (HALF_UP) for validation to allow paying the displayed amount
        Money roundedBalance = Money.of(
            balance.getAmount().setScale(2, RoundingMode.HALF_UP),
            balance.getCurrency()
        );
        if (payment.getAmount().isGreaterThan(roundedBalance)) {
            throw new PaymentExceedsBalanceException(
                String.format("Payment amount %s exceeds outstanding balance %s", payment.getAmount(), roundedBalance)
            );
        }
        
        payments.add(payment);
        domainEvents.add(new PaymentRecorded(
            id,
            payment.getId(),
            payment.getAmount().getAmount(),
            payment.getMethod().name(),
            Instant.now()
        ));
        
        // Check if invoice is now fully paid (balance < $0.01 including overpayments)
        Money newBalance = calculateBalance();
        if (newBalance.isEffectivelyZero()) {
            this.status = InvoiceStatus.PAID;
            domainEvents.add(new InvoicePaid(id, customerId, invoiceNumber.getValue(), Instant.now()));
        }
    }

    /**
     * Calculates the subtotal (sum of all line item subtotals).
     * 
     * @return the subtotal as Money
     */
    public Money calculateSubtotal() {
        return lineItems.stream()
            .map(LineItem::getSubtotal)
            .reduce(Money.zero(), Money::add);
    }

    /**
     * Calculates the tax amount based on subtotal and tax rate.
     * 
     * @return the tax amount as Money
     */
    public Money calculateTax() {
        Money subtotal = calculateSubtotal();
        return subtotal.multiply(taxRate);
    }

    /**
     * Calculates the total amount (subtotal + tax).
     * 
     * @return the total amount as Money
     */
    public Money calculateTotal() {
        return calculateSubtotal().add(calculateTax());
    }

    /**
     * Calculates the outstanding balance (total - sum of payments).
     * 
     * @return the balance as Money
     */
    public Money calculateBalance() {
        Money total = calculateTotal();
        Money paidAmount = payments.stream()
            .map(Payment::getAmount)
            .reduce(Money.zero(), Money::add);
        return total.subtract(paidAmount);
    }

    /**
     * Pulls and clears all domain events raised by this aggregate.
     * 
     * @return a list of domain events
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public InvoiceNumber getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public List<LineItem> getLineItems() {
        return Collections.unmodifiableList(lineItems);
    }

    public List<Payment> getPayments() {
        return Collections.unmodifiableList(payments);
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }
}

