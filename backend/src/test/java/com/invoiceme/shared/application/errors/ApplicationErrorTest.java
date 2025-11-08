package com.invoiceme.shared.application.errors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ApplicationError.
 * Verifies that error codes and HTTP status codes are correctly set.
 */
class ApplicationErrorTest {

    @Test
    @DisplayName("T6.2.1 - ApplicationError.notFound() returns 404 with NOT_FOUND code")
    void notFound_returns404WithNotFoundCode() {
        // When
        ApplicationError error = ApplicationError.notFound("Invoice");

        // Then
        assertThat(error.getMessage()).isEqualTo("Invoice not found");
        assertThat(error.getCode()).isEqualTo(ErrorCodes.NOT_FOUND);
        assertThat(error.getHttpStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("T6.2.2 - ApplicationError.conflict() returns 409 with CONFLICT code")
    void conflict_returns409WithConflictCode() {
        // When
        ApplicationError error = ApplicationError.conflict("Customer with email test@example.com already exists");

        // Then
        assertThat(error.getMessage()).isEqualTo("Customer with email test@example.com already exists");
        assertThat(error.getCode()).isEqualTo(ErrorCodes.CONFLICT);
        assertThat(error.getHttpStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("T6.2.3 - ApplicationError.validation() returns 422 with VALIDATION_ERROR code")
    void validation_returns422WithValidationErrorCode() {
        // When
        ApplicationError error = ApplicationError.validation("Invalid payment amount");

        // Then
        assertThat(error.getMessage()).isEqualTo("Invalid payment amount");
        assertThat(error.getCode()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(error.getHttpStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("T6.2.4 - ApplicationError extends RuntimeException")
    void applicationError_extendsRuntimeException() {
        // When
        ApplicationError error = ApplicationError.notFound("Customer");

        // Then
        assertThat(error).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("T6.2.5 - ApplicationError can be thrown and caught")
    void applicationError_canBeThrown() {
        // When/Then
        try {
            throw ApplicationError.notFound("Invoice");
        } catch (ApplicationError e) {
            assertThat(e.getCode()).isEqualTo(ErrorCodes.NOT_FOUND);
            assertThat(e.getHttpStatus()).isEqualTo(404);
        }
    }
}

