package com.invoiceme.shared.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Standardized error response DTO for all API error responses.
 * 
 * Ensures consistent error structure across all endpoints:
 * - code: Error code (e.g., "VALIDATION_FAILED", "NOT_FOUND")
 * - message: Human-readable error message
 * - details: Optional additional error details (field-level errors, etc.)
 * 
 * Example response:
 * {
 *   "code": "VALIDATION_FAILED",
 *   "message": "Validation failed",
 *   "details": {
 *     "email": "Email must be valid",
 *     "name": "Name is required"
 *   }
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    String code,
    String message,
    Map<String, Object> details
) {
    /**
     * Creates an ApiError with code and message only (no details).
     */
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    /**
     * Creates an ApiError with code, message, and details.
     */
    public static ApiError of(String code, String message, Map<String, Object> details) {
        return new ApiError(code, message, details);
    }
}

