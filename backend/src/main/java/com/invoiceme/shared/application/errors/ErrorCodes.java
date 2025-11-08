package com.invoiceme.shared.application.errors;

/**
 * Centralized error codes for consistent error reporting.
 * 
 * These codes are used in ApplicationError and returned in API error responses.
 */
public final class ErrorCodes {
    
    private ErrorCodes() {
        // Utility class - prevent instantiation
    }
    
    // 400 - Bad Request (Validation)
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    
    // 404 - Not Found
    public static final String NOT_FOUND = "NOT_FOUND";
    
    // 409 - Conflict
    public static final String CONFLICT = "CONFLICT";
    
    // 422 - Unprocessable Entity (Business Rule Violation)
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";
    
    // 500 - Internal Server Error
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
}

