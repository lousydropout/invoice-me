package com.invoiceme.invoice.api;

import com.invoiceme.invoice.api.dto.CreateInvoiceRequest;
import com.invoiceme.invoice.api.dto.InvoiceResponse;
import com.invoiceme.invoice.api.dto.RecordPaymentRequest;
import com.invoiceme.invoice.api.dto.UpdateInvoiceRequest;
import com.invoiceme.invoice.application.commands.CreateInvoiceHandler;
import com.invoiceme.invoice.application.commands.RecordPaymentHandler;
import com.invoiceme.invoice.application.commands.SendInvoiceHandler;
import com.invoiceme.invoice.application.commands.UpdateInvoiceHandler;
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

    public InvoiceController(
        CreateInvoiceHandler createHandler,
        UpdateInvoiceHandler updateHandler,
        SendInvoiceHandler sendHandler,
        RecordPaymentHandler recordPaymentHandler,
        InvoiceRepository invoiceRepository
    ) {
        this.createHandler = createHandler;
        this.updateHandler = updateHandler;
        this.sendHandler = sendHandler;
        this.recordPaymentHandler = recordPaymentHandler;
        this.invoiceRepository = invoiceRepository;
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
        Invoice invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> ApplicationError.notFound("Invoice"));

        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> listAll() {
        // TODO: Implement query handler for listing invoices (Epic 5: CQRS Read Side)
        // For now, return empty list
        return ResponseEntity.ok(List.of());
    }
}

