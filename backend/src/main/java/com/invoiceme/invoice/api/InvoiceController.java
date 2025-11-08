package com.invoiceme.invoice.api;

import com.invoiceme.invoice.api.dto.CreateInvoiceRequest;
import com.invoiceme.invoice.api.dto.InvoiceResponse;
import com.invoiceme.invoice.api.dto.RecordPaymentRequest;
import com.invoiceme.invoice.api.dto.UpdateInvoiceRequest;
import com.invoiceme.invoice.application.commands.CreateInvoiceHandler;
import com.invoiceme.invoice.application.commands.RecordPaymentHandler;
import com.invoiceme.invoice.application.commands.SendInvoiceHandler;
import com.invoiceme.invoice.application.commands.UpdateInvoiceHandler;
import com.invoiceme.invoice.application.queries.GetInvoiceByIdHandler;
import com.invoiceme.invoice.application.queries.GetInvoiceByIdQuery;
import com.invoiceme.invoice.application.queries.ListInvoicesHandler;
import com.invoiceme.invoice.application.queries.ListInvoicesQuery;
import com.invoiceme.invoice.application.queries.ListOverdueInvoicesHandler;
import com.invoiceme.invoice.application.queries.ListOverdueInvoicesQuery;
import com.invoiceme.invoice.application.queries.dto.InvoiceDetailView;
import com.invoiceme.invoice.application.queries.dto.InvoiceSummaryView;
import com.invoiceme.invoice.domain.Invoice;
import com.invoiceme.invoice.domain.InvoiceRepository;
import com.invoiceme.shared.application.errors.ApplicationError;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Invoice resources.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final CreateInvoiceHandler createHandler;
    private final UpdateInvoiceHandler updateHandler;
    private final SendInvoiceHandler sendHandler;
    private final RecordPaymentHandler recordPaymentHandler;
    private final InvoiceRepository invoiceRepository;
    private final GetInvoiceByIdHandler getInvoiceByIdHandler;
    private final ListInvoicesHandler listInvoicesHandler;
    private final ListOverdueInvoicesHandler listOverdueInvoicesHandler;

    public InvoiceController(
        CreateInvoiceHandler createHandler,
        UpdateInvoiceHandler updateHandler,
        SendInvoiceHandler sendHandler,
        RecordPaymentHandler recordPaymentHandler,
        InvoiceRepository invoiceRepository,
        GetInvoiceByIdHandler getInvoiceByIdHandler,
        ListInvoicesHandler listInvoicesHandler,
        ListOverdueInvoicesHandler listOverdueInvoicesHandler
    ) {
        this.createHandler = createHandler;
        this.updateHandler = updateHandler;
        this.sendHandler = sendHandler;
        this.recordPaymentHandler = recordPaymentHandler;
        this.invoiceRepository = invoiceRepository;
        this.getInvoiceByIdHandler = getInvoiceByIdHandler;
        this.listInvoicesHandler = listInvoicesHandler;
        this.listOverdueInvoicesHandler = listOverdueInvoicesHandler;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@RequestBody @Valid CreateInvoiceRequest request) {
        UUID id = UUID.randomUUID();
        createHandler.handle(request.toCommand(id));

        Invoice invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Invoice not found after creation"));

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(InvoiceResponse.from(invoice));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
        @PathVariable UUID id,
        @RequestBody @Valid UpdateInvoiceRequest request
    ) {
        updateHandler.handle(request.toCommand(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<Void> send(@PathVariable UUID id) {
        sendHandler.handle(new com.invoiceme.invoice.application.commands.SendInvoiceCommand(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<Void> recordPayment(
        @PathVariable UUID id,
        @RequestBody @Valid RecordPaymentRequest request
    ) {
        UUID paymentId = UUID.randomUUID();
        recordPaymentHandler.handle(request.toCommand(id, paymentId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getById(@PathVariable UUID id) {
        InvoiceDetailView detailView = getInvoiceByIdHandler.handle(new GetInvoiceByIdQuery(id));
        
        // Convert InvoiceDetailView to InvoiceResponse
        // Note: InvoiceDetailView doesn't have customerId or taxRate, so we need to get them from the invoice
        Invoice invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> ApplicationError.notFound("Invoice"));
        
        return ResponseEntity.ok(toInvoiceResponse(detailView, invoice.getCustomerId(), invoice.getTaxRate()));
    }

    @GetMapping
    public ResponseEntity<List<InvoiceSummaryView>> listAll() {
        List<InvoiceSummaryView> summaries = listInvoicesHandler.handle(new ListInvoicesQuery());
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<InvoiceSummaryView>> listOverdue() {
        List<InvoiceSummaryView> overdue = listOverdueInvoicesHandler.handle(new ListOverdueInvoicesQuery());
        return ResponseEntity.ok(overdue);
    }

    /**
     * Converts InvoiceDetailView to InvoiceResponse.
     * This is needed because InvoiceDetailView is a read model optimized for queries,
     * while InvoiceResponse is the API contract.
     */
    private InvoiceResponse toInvoiceResponse(InvoiceDetailView detailView, UUID customerId, java.math.BigDecimal taxRate) {
        // Convert line items
        List<InvoiceResponse.LineItemResponse> lineItemResponses = detailView.lineItems().stream()
            .map(item -> new InvoiceResponse.LineItemResponse(
                item.description(),
                item.quantity(),
                new InvoiceResponse.MoneyResponse(item.unitPrice(), "USD"), // Assuming USD for now
                new InvoiceResponse.MoneyResponse(item.subtotal(), "USD")
            ))
            .toList();

        // Convert payments
        List<InvoiceResponse.PaymentResponse> paymentResponses = detailView.payments().stream()
            .map(payment -> new InvoiceResponse.PaymentResponse(
                payment.id(),
                new InvoiceResponse.MoneyResponse(payment.amount(), "USD"),
                payment.paymentDate(),
                payment.method(),
                payment.reference()
            ))
            .toList();

        return new InvoiceResponse(
            detailView.id(),
            customerId,
            detailView.invoiceNumber(),
            detailView.issueDate(),
            detailView.dueDate(),
            detailView.status(),
            lineItemResponses,
            paymentResponses,
            taxRate,
            detailView.notes(),
            new InvoiceResponse.MoneyResponse(detailView.subtotal(), "USD"),
            new InvoiceResponse.MoneyResponse(detailView.tax(), "USD"),
            new InvoiceResponse.MoneyResponse(detailView.total(), "USD"),
            new InvoiceResponse.MoneyResponse(detailView.balance(), "USD")
        );
    }
}

