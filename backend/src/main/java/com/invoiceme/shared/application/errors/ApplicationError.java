package com.invoiceme.shared.application.errors;

/**
 * Application-level error for business rule violations.
 * 
 * Maps to HTTP status codes via GlobalExceptionHandler.
 */
public class ApplicationError extends RuntimeException {
    private final String code;
    private final int httpStatus;

    private ApplicationError(String message, String code, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Creates a "not found" error (404).
     * 
     * @param entity the entity type that was not found
     * @return ApplicationError with 404 status
     */
    public static ApplicationError notFound(String entity) {
        return new ApplicationError(
            entity + " not found",
            ErrorCodes.NOT_FOUND,
            404
        );
    }

    /**
     * Creates a "conflict" error (409).
     * 
     * @param reason the reason for the conflict
     * @return ApplicationError with 409 status
     */
    public static ApplicationError conflict(String reason) {
        return new ApplicationError(
            reason,
            ErrorCodes.CONFLICT,
            409
        );
    }

    /**
     * Creates a "validation" error (422).
     * 
     * @param message the validation message
     * @return ApplicationError with 422 status
     */
    public static ApplicationError validation(String message) {
        return new ApplicationError(
            message,
            ErrorCodes.VALIDATION_ERROR,
            422
        );
    }
}

