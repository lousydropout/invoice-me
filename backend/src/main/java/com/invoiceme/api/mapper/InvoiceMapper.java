package com.invoiceme.api.mapper;

import com.invoiceme.api.domain.Invoice;
import com.invoiceme.api.dto.InvoiceResponse;
import org.springframework.stereotype.Component;

@Component
public class InvoiceMapper {

    public InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCustomerName(),
                invoice.getAmount(),
                invoice.getStatus(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}

