package com.invoiceme.customer.application.queries;

import com.invoiceme.customer.application.queries.dto.CustomerView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Query handler for retrieving outstanding balances by customer.
 */
@Service
public class OutstandingByCustomerHandler {
    private final JdbcTemplate jdbcTemplate;

    public OutstandingByCustomerHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<CustomerView> handle(OutstandingByCustomerQuery query) {
        String sql = """
            SELECT 
                c.id,
                c.name,
                c.email,
                c.phone,
                c.country,
                COALESCE(SUM(
                    (COALESCE(inv_totals.subtotal, 0) * (1 + inv_totals.tax_rate)) - COALESCE(p.total_paid, 0)
                ), 0) AS outstanding_balance
            FROM customers c
            LEFT JOIN (
                SELECT 
                    i.id AS invoice_id,
                    i.customer_id,
                    i.tax_rate,
                    COALESCE(SUM(li.subtotal), 0) AS subtotal
                FROM invoices i
                LEFT JOIN line_items li ON li.invoice_id = i.id
                GROUP BY i.id, i.customer_id, i.tax_rate
            ) inv_totals ON inv_totals.customer_id = c.id
            LEFT JOIN (
                SELECT 
                    p.invoice_id,
                    SUM(p.amount) AS total_paid
                FROM payments p
                GROUP BY p.invoice_id
            ) p ON p.invoice_id = inv_totals.invoice_id
            GROUP BY c.id, c.name, c.email, c.phone, c.country
            ORDER BY c.name
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CustomerView(
            UUID.fromString(rs.getString("id")),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("country"),
            rs.getBigDecimal("outstanding_balance")
        ));
    }
}

