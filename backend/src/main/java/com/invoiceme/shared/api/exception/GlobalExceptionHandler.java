package com.invoiceme.shared.api.exception;

import com.invoiceme.invoice.domain.exceptions.PaymentExceedsBalanceException;
import com.invoiceme.shared.application.errors.ApplicationError;
import com.invoiceme.shared.application.errors.ErrorCodes;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API.
 * 
 * Maps domain and application errors to appropriate HTTP status codes:
 * - 400 (BAD_REQUEST): Validation errors (MethodArgumentNotValidException, IllegalArgumentException)
 * - 404 (NOT_FOUND): Resource not found (EntityNotFoundException, ApplicationError.notFound)
 * - 409 (CONFLICT): Conflict errors (ApplicationError.conflict)
 * - 422 (UNPROCESSABLE_ENTITY): Business rule violations (PaymentExceedsBalanceException, ApplicationError.validation)
 * - 500 (INTERNAL_SERVER_ERROR): Unexpected errors
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles ApplicationError exceptions.
     * Maps to appropriate HTTP status codes based on error type:
     * - 404 for notFound()
     * - 409 for conflict()
     * - 422 for validation()
     */
    @ExceptionHandler(ApplicationError.class)
    public ResponseEntity<Map<String, Object>> handleApplicationError(ApplicationError ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", ex.getMessage());
        response.put("code", ex.getCode());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Handles validation errors from @Valid annotations.
     * Returns 400 (BAD_REQUEST) with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation failed");
        response.put("code", ErrorCodes.VALIDATION_FAILED);
        
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() != null 
                                ? error.getDefaultMessage() 
                                : "Invalid value",
                        (existing, replacement) -> existing
                ));
        
        response.put("details", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles JPA EntityNotFoundException.
     * Returns 404 (NOT_FOUND).
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFoundException(
            EntityNotFoundException ex
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Resource not found");
        response.put("code", ErrorCodes.NOT_FOUND);
        response.put("details", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles PaymentExceedsBalanceException (business rule violation).
     * Returns 422 (UNPROCESSABLE_ENTITY).
     */
    @ExceptionHandler(PaymentExceedsBalanceException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentExceedsBalanceException(
            PaymentExceedsBalanceException ex
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Payment exceeds remaining balance");
        response.put("code", ErrorCodes.BUSINESS_RULE_VIOLATION);
        response.put("details", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Handles IllegalArgumentException (invalid arguments).
     * Returns 400 (BAD_REQUEST).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Invalid argument");
        response.put("code", ErrorCodes.INVALID_ARGUMENT);
        response.put("details", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles all other unexpected exceptions.
     * Returns 500 (INTERNAL_SERVER_ERROR).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Internal server error");
        response.put("code", ErrorCodes.INTERNAL_SERVER_ERROR);
        // Only include details in development; in production, log but don't expose
        response.put("details", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

