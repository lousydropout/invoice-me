package com.invoiceme.invoice.application.queries;

import com.invoiceme.invoice.application.queries.dto.InvoiceSummaryView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Query handler for listing overdue invoices.
 * Overdue = due_date < CURRENT_DATE AND status != 'PAID'
 */
@Service
public class ListOverdueInvoicesHandler {
    private final JdbcTemplate jdbcTemplate;

    public ListOverdueInvoicesHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummaryView> handle(ListOverdueInvoicesQuery query) {
        String sql = """
            SELECT 
                i.id,
                i.invoice_number,
                i.customer_id,
                c.name AS customer_name,
                i.status,
                i.issue_date,
                i.due_date,
                COALESCE(
                    (SELECT SUM(li2.subtotal) FROM line_items li2 WHERE li2.invoice_id = i.id) * (1 + i.tax_rate),
                    0
                ) AS total,
                COALESCE(
                    (SELECT SUM(li2.subtotal) FROM line_items li2 WHERE li2.invoice_id = i.id) * (1 + i.tax_rate),
                    0
                ) - COALESCE(
                    (SELECT SUM(p2.amount) FROM payments p2 WHERE p2.invoice_id = i.id),
                    0
                ) AS balance
            FROM invoices i
            JOIN customers c ON i.customer_id = c.id
            WHERE i.status != 'PAID' AND i.due_date < CURRENT_DATE
            ORDER BY i.due_date ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new InvoiceSummaryView(
            UUID.fromString(rs.getString("id")),
            rs.getString("invoice_number"),
            UUID.fromString(rs.getString("customer_id")),
            rs.getString("customer_name"),
            rs.getString("status"),
            rs.getDate("issue_date").toLocalDate(),
            rs.getDate("due_date").toLocalDate(),
            rs.getBigDecimal("total"),
            rs.getBigDecimal("balance")
        ));
    }
}

