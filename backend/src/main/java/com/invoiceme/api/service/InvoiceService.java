package com.invoiceme.api.service;

import com.invoiceme.api.domain.Invoice;
import com.invoiceme.api.domain.InvoiceStatus;
import com.invoiceme.api.dto.CreateInvoiceRequest;
import com.invoiceme.api.dto.UpdateInvoiceRequest;
import com.invoiceme.api.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    @Transactional
    public Invoice create(CreateInvoiceRequest request) {
        log.info("Creating invoice for customer: {}", request.customerName());
        Invoice invoice = new Invoice();
        invoice.setCustomerName(request.customerName());
        invoice.setAmount(request.amount());
        invoice.setStatus(InvoiceStatus.DRAFT);
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Created invoice with id: {}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Invoice> findAll() {
        return invoiceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Invoice findById(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Invoice not found with id: " + id));
    }

    @Transactional
    public Invoice update(UUID id, UpdateInvoiceRequest request) {
        log.info("Updating invoice with id: {}", id);
        Invoice invoice = findById(id);
        
        request.customerName().ifPresent(invoice::setCustomerName);
        request.amount().ifPresent(amount -> {
            if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            invoice.setAmount(amount);
        });
        request.status().ifPresent(invoice::setStatus);
        
        Invoice updated = invoiceRepository.save(invoice);
        log.info("Updated invoice with id: {}", updated.getId());
        return updated;
    }

    @Transactional
    public void delete(UUID id) {
        log.info("Deleting invoice with id: {}", id);
        Invoice invoice = findById(id);
        invoiceRepository.delete(invoice);
        log.info("Deleted invoice with id: {}", id);
    }
}

