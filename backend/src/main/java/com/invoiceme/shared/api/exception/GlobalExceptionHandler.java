package com.invoiceme.shared.api.exception;

import com.invoiceme.invoice.domain.exceptions.PaymentExceedsBalanceException;
import com.invoiceme.shared.api.dto.ApiError;
import com.invoiceme.shared.application.errors.ApplicationError;
import com.invoiceme.shared.application.errors.ErrorCodes;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
    public ResponseEntity<ApiError> handleApplicationError(ApplicationError ex) {
        ApiError error = ApiError.of(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles validation errors from @Valid annotations.
     * Returns 400 (BAD_REQUEST) with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException ex
    ) {
        Map<String, Object> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() != null 
                                ? error.getDefaultMessage() 
                                : "Invalid value",
                        (existing, replacement) -> existing
                ));
        
        ApiError error = ApiError.of(
            ErrorCodes.VALIDATION_FAILED,
            "Validation failed",
            fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles JPA EntityNotFoundException.
     * Returns 404 (NOT_FOUND).
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFoundException(
            EntityNotFoundException ex
    ) {
        ApiError error = ApiError.of(
            ErrorCodes.NOT_FOUND,
            "Resource not found",
            Map.of("message", ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handles PaymentExceedsBalanceException (business rule violation).
     * Returns 422 (UNPROCESSABLE_ENTITY).
     */
    @ExceptionHandler(PaymentExceedsBalanceException.class)
    public ResponseEntity<ApiError> handlePaymentExceedsBalanceException(
            PaymentExceedsBalanceException ex
    ) {
        ApiError error = ApiError.of(
            ErrorCodes.BUSINESS_RULE_VIOLATION,
            "Payment exceeds remaining balance",
            Map.of("message", ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handles IllegalArgumentException (invalid arguments).
     * Returns 400 (BAD_REQUEST).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(
            IllegalArgumentException ex
    ) {
        ApiError error = ApiError.of(
            ErrorCodes.INVALID_ARGUMENT,
            "Invalid argument",
            Map.of("message", ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles all other unexpected exceptions.
     * Returns 500 (INTERNAL_SERVER_ERROR).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex) {
        // Only include details in development; in production, log but don't expose
        ApiError error = ApiError.of(
            ErrorCodes.INTERNAL_SERVER_ERROR,
            "Internal server error",
            ex.getMessage() != null ? Map.of("message", ex.getMessage()) : null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

