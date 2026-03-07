package tech.yesboss.memory.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheException.
 */
@DisplayName("Cache Exception Tests")
public class CacheExceptionTest {

    @Test
    @DisplayName("Should create exception with message")
    void testExceptionWithMessage() {
        // Arrange
        String message = "Test error message";

        // Act
        CacheException exception = new CacheException(message);

        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals("UNKNOWN_ERROR", exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create exception with message and error code")
    void testExceptionWithMessageAndCode() {
        // Arrange
        String message = "Test error";
        String code = "TEST_ERROR";

        // Act
        CacheException exception = new CacheException(message, code);

        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(code, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void testExceptionWithMessageAndCause() {
        // Arrange
        String message = "Test error";
        Throwable cause = new RuntimeException("Root cause");

        // Act
        CacheException exception = new CacheException(message, cause);

        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals("UNKNOWN_ERROR", exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create exception with message, cause, and error code")
    void testExceptionWithAllParameters() {
        // Arrange
        String message = "Test error";
        Throwable cause = new RuntimeException("Root cause");
        String code = CacheException.ERROR_BACKEND;

        // Act
        CacheException exception = new CacheException(message, cause, code);

        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(code, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create initialization error")
    void testInitializationError() {
        // Act
        CacheException exception = CacheException.initializationError("Init failed");

        // Assert
        assertEquals("Init failed", exception.getMessage());
        assertEquals(CacheException.ERROR_INITIALIZATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create initialization error with cause")
    void testInitializationErrorWithCause() {
        // Arrange
        Throwable cause = new RuntimeException("Root cause");

        // Act
        CacheException exception = CacheException.initializationError("Init failed", cause);

        // Assert
        assertEquals("Init failed", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(CacheException.ERROR_INITIALIZATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create serialization error")
    void testSerializationError() {
        // Act
        CacheException exception = CacheException.serializationError("Serialization failed");

        // Assert
        assertEquals("Serialization failed", exception.getMessage());
        assertEquals(CacheException.ERROR_SERIALIZATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create serialization error with cause")
    void testSerializationErrorWithCause() {
        // Arrange
        Throwable cause = new RuntimeException("IO error");

        // Act
        CacheException exception = CacheException.serializationError("Serialization failed", cause);

        // Assert
        assertEquals("Serialization failed", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(CacheException.ERROR_SERIALIZATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create backend error")
    void testBackendError() {
        // Act
        CacheException exception = CacheException.backendError("Backend connection failed");

        // Assert
        assertEquals("Backend connection failed", exception.getMessage());
        assertEquals(CacheException.ERROR_BACKEND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create backend error with cause")
    void testBackendErrorWithCause() {
        // Arrange
        Throwable cause = new RuntimeException("Network error");

        // Act
        CacheException exception = CacheException.backendError("Backend failed", cause);

        // Assert
        assertEquals("Backend failed", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(CacheException.ERROR_BACKEND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create configuration error")
    void testConfigurationError() {
        // Act
        CacheException exception = CacheException.configurationError("Invalid config");

        // Assert
        assertEquals("Invalid config", exception.getMessage());
        assertEquals(CacheException.ERROR_CONFIGURATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create timeout error")
    void testTimeoutError() {
        // Act
        CacheException exception = CacheException.timeoutError("Operation timed out");

        // Assert
        assertEquals("Operation timed out", exception.getMessage());
        assertEquals(CacheException.ERROR_TIMEOUT, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create eviction error")
    void testEvictionError() {
        // Act
        CacheException exception = CacheException.evictionError("Eviction failed");

        // Assert
        assertEquals("Eviction failed", exception.getMessage());
        assertEquals(CacheException.ERROR_EVICTION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create eviction error with cause")
    void testEvictionErrorWithCause() {
        // Arrange
        Throwable cause = new RuntimeException("Lock error");

        // Act
        CacheException exception = CacheException.evictionError("Eviction failed", cause);

        // Assert
        assertEquals("Eviction failed", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(CacheException.ERROR_EVICTION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should verify error code constants")
    void testErrorCodeConstants() {
        // Assert - Verify all error codes are defined
        assertEquals("CACHE_INITIALIZATION_ERROR", CacheException.ERROR_INITIALIZATION);
        assertEquals("CACHE_SERIALIZATION_ERROR", CacheException.ERROR_SERIALIZATION);
        assertEquals("CACHE_BACKEND_ERROR", CacheException.ERROR_BACKEND);
        assertEquals("CACHE_CONFIGURATION_ERROR", CacheException.ERROR_CONFIGURATION);
        assertEquals("CACHE_TIMEOUT_ERROR", CacheException.ERROR_TIMEOUT);
        assertEquals("CACHE_EVICTION_ERROR", CacheException.ERROR_EVICTION);
    }

    @Test
    @DisplayName("Should be throwable and catchable")
    void testExceptionCatchable() {
        // Act & Assert
        try {
            throw CacheException.backendError("Test error");
        } catch (CacheException e) {
            assertEquals("Test error", e.getMessage());
            assertEquals(CacheException.ERROR_BACKEND, e.getErrorCode());
        }
    }

    @Test
    @DisplayName("Should preserve stack trace")
    void testStackTrace() {
        // Act
        CacheException exception = CacheException.backendError("Test error", new RuntimeException("Cause"));

        // Assert
        assertNotNull(exception.getStackTrace());
        assertTrue(exception.getStackTrace().length > 0);
    }
}
