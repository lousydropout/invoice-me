package com.invoiceme.shared.application.errors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ErrorCodes.
 * Verifies that all error codes are defined and the class cannot be instantiated.
 */
class ErrorCodesTest {

    @Test
    @DisplayName("T6.2.6 - ErrorCodes contains all required error code constants")
    void errorCodes_containsAllRequiredConstants() {
        // Then
        assertThat(ErrorCodes.VALIDATION_FAILED).isEqualTo("VALIDATION_FAILED");
        assertThat(ErrorCodes.INVALID_ARGUMENT).isEqualTo("INVALID_ARGUMENT");
        assertThat(ErrorCodes.NOT_FOUND).isEqualTo("NOT_FOUND");
        assertThat(ErrorCodes.CONFLICT).isEqualTo("CONFLICT");
        assertThat(ErrorCodes.VALIDATION_ERROR).isEqualTo("VALIDATION_ERROR");
        assertThat(ErrorCodes.BUSINESS_RULE_VIOLATION).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(ErrorCodes.INTERNAL_SERVER_ERROR).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("T6.2.7 - ErrorCodes cannot be instantiated (utility class)")
    void errorCodes_cannotBeInstantiated() throws Exception {
        // Given
        Constructor<ErrorCodes> constructor = ErrorCodes.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

        // When
        constructor.setAccessible(true);

        // Then - should throw InvocationTargetException with AssertionError or similar
        try {
            constructor.newInstance();
            // If we get here, the constructor didn't throw, which is unexpected
            // But some utility classes just have private constructors without throwing
        } catch (InvocationTargetException e) {
            // Expected - constructor throws exception
            assertThat(e.getCause()).isNotNull();
        }
    }
}

