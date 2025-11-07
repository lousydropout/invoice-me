package com.invoiceme.api.controller;

import com.invoiceme.api.dto.CreateInvoiceRequest;
import com.invoiceme.api.dto.InvoiceResponse;
import com.invoiceme.api.dto.UpdateInvoiceRequest;
import com.invoiceme.api.mapper.InvoiceMapper;
import com.invoiceme.api.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceMapper invoiceMapper;

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invoiceMapper.toResponse(invoiceService.create(request)));
    }

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> findAll() {
        return ResponseEntity.ok(
                invoiceService.findAll().stream()
                        .map(invoiceMapper::toResponse)
                        .toList()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                invoiceMapper.toResponse(invoiceService.findById(id))
        );
    }

    @PatchMapping("/{id}")
    public ResponseEntity<InvoiceResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInvoiceRequest request
    ) {
        return ResponseEntity.ok(
                invoiceMapper.toResponse(invoiceService.update(id, request))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

