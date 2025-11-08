package com.invoiceme.shared.api.exception;

import com.invoiceme.invoice.domain.exceptions.PaymentExceedsBalanceException;
import com.invoiceme.shared.application.errors.ApplicationError;
import com.invoiceme.shared.application.errors.ErrorCodes;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler.
 * Verifies that all exception types are correctly mapped to HTTP status codes.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("T6.2.8 - handleApplicationError returns correct status for notFound (404)")
    void handleApplicationError_notFound_returns404() {
        // Given
        ApplicationError error = ApplicationError.notFound("Invoice");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleApplicationError(error);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Invoice not found");
        assertThat(body.get("code")).isEqualTo(ErrorCodes.NOT_FOUND);
    }

    @Test
    @DisplayName("T6.2.9 - handleApplicationError returns correct status for conflict (409)")
    void handleApplicationError_conflict_returns409() {
        // Given
        ApplicationError error = ApplicationError.conflict("Customer with email test@example.com already exists");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleApplicationError(error);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Customer with email test@example.com already exists");
        assertThat(body.get("code")).isEqualTo(ErrorCodes.CONFLICT);
    }

    @Test
    @DisplayName("T6.2.10 - handleApplicationError returns correct status for validation (422)")
    void handleApplicationError_validation_returns422() {
        // Given
        ApplicationError error = ApplicationError.validation("Invalid payment amount");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleApplicationError(error);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Invalid payment amount");
        assertThat(body.get("code")).isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("T6.2.11 - handleValidationException returns 400 with field errors")
    void handleValidationException_returns400WithFieldErrors() {
        // Given
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        List<FieldError> fieldErrors = new ArrayList<>();
        fieldErrors.add(new FieldError("createInvoiceRequest", "customerId", "must not be null"));
        fieldErrors.add(new FieldError("createInvoiceRequest", "lineItems", "must not be empty"));

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Validation failed");
        assertThat(body.get("code")).isEqualTo(ErrorCodes.VALIDATION_FAILED);
        
        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) body.get("details");
        assertThat(details).isNotNull();
        assertThat(details.get("customerId")).isEqualTo("must not be null");
        assertThat(details.get("lineItems")).isEqualTo("must not be empty");
    }

    @Test
    @DisplayName("T6.2.12 - handleEntityNotFoundException returns 404")
    void handleEntityNotFoundException_returns404() {
        // Given
        EntityNotFoundException ex = new EntityNotFoundException("Invoice with id 123 not found");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleEntityNotFoundException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Resource not found");
        assertThat(body.get("code")).isEqualTo(ErrorCodes.NOT_FOUND);
        assertThat(body.get("details")).isEqualTo("Invoice with id 123 not found");
    }

    @Test
    @DisplayName("T6.2.13 - handlePaymentExceedsBalanceException returns 422")
    void handlePaymentExceedsBalanceException_returns422() {
        // Given
        PaymentExceedsBalanceException ex = new PaymentExceedsBalanceException("Payment amount 1000 exceeds remaining balance 500");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handlePaymentExceedsBalanceException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Payment exceeds remaining balance");
        assertThat(body.get("code")).isEqualTo(ErrorCodes.BUSINESS_RULE_VIOLATION);
        assertThat(body.get("details")).isEqualTo("Payment amount 1000 exceeds remaining balance 500");
    }

    @Test
    @DisplayName("T6.2.14 - handleIllegalArgumentException returns 400")
    void handleIllegalArgumentException_returns400() {
        // Given
        IllegalArgumentException ex = new IllegalArgumentException("Invalid invoice status");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgumentException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Invalid argument");
        assertThat(body.get("code")).isEqualTo(ErrorCodes.INVALID_ARGUMENT);
        assertThat(body.get("details")).isEqualTo("Invalid invoice status");
    }

    @Test
    @DisplayName("T6.2.15 - handleGenericException returns 500")
    void handleGenericException_returns500() {
        // Given
        RuntimeException ex = new RuntimeException("Unexpected error occurred");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Internal server error");
        assertThat(body.get("code")).isEqualTo(ErrorCodes.INTERNAL_SERVER_ERROR);
        assertThat(body.get("details")).isEqualTo("Unexpected error occurred");
    }
}

