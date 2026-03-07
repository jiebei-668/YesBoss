package tech.yesboss.memory.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryException.
 */
@DisplayName("Memory Exception Tests")
public class MemoryExceptionTest {

    @Test
    @DisplayName("Should create exception with message")
    void testExceptionWithMessage() {
        // Arrange
        String message = "Test error message";

        // Act
        MemoryException exception = new MemoryException(message);

        // Assert
        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void testExceptionWithMessageAndCause() {
        // Arrange
        String message = "Test error";
        Throwable cause = new RuntimeException("Root cause");

        // Act
        MemoryException exception = new MemoryException(message, cause);

        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should create exception with message, cause, and error code")
    void testExceptionWithAllParameters() {
        // Arrange
        String message = "Test error";
        Throwable cause = new RuntimeException("Root cause");
        String errorCode = "TEST_ERROR";

        // Act
        MemoryException exception = new MemoryException(message, cause, errorCode);

        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(errorCode, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should create exception with error code")
    void testExceptionWithErrorCode() {
        // Arrange
        String message = "Test error";
        String errorCode = "VALIDATION_ERROR";

        // Act
        MemoryException exception = new MemoryException(message, errorCode);

        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should preserve stack trace")
    void testStackTrace() {
        // Arrange
        RuntimeException cause = new RuntimeException("Cause");

        // Act
        MemoryException exception = new MemoryException("Error", cause);

        // Assert
        assertNotNull(exception.getStackTrace());
        assertTrue(exception.getStackTrace().length > 0);
    }

    @Test
    @DisplayName("Should be throwable and catchable")
    void testExceptionCatchable() {
        // Act & Assert
        try {
            throw new MemoryException("Test error");
        } catch (MemoryException e) {
            assertEquals("Test error", e.getMessage());
        }
    }

    @Test
    @DisplayName("Should handle null message")
    void testNullExceptionMessage() {
        // Act
        MemoryException exception = new MemoryException((String) null);

        // Assert
        assertNull(exception.getMessage());
    }

    @Test
    @DisplayName("Should handle null cause")
    void testNullExceptionCause() {
        // Act
        MemoryException exception = new MemoryException("Error", (Throwable) null);

        // Assert
        assertEquals("Error", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should handle null error code")
    void testNullExceptionErrorCode() {
        // Act
        MemoryException exception = new MemoryException("Error", (String) null);

        // Assert
        assertEquals("Error", exception.getMessage());
        assertNull(exception.getErrorCode());
    }

    @Test
    @DisplayName("Should support chaining")
    void testExceptionChaining() {
        // Arrange
        Throwable rootCause = new RuntimeException("Root cause");
        Throwable intermediateCause = new MemoryException("Intermediate", rootCause);

        // Act
        MemoryException exception = new MemoryException("Top level", intermediateCause);

        // Assert
        assertEquals("Top level", exception.getMessage());
        assertEquals(intermediateCause, exception.getCause());
        assertEquals(rootCause, exception.getCause().getCause());
    }

    @Test
    @DisplayName("Should provide meaningful toString")
    void testToString() {
        // Arrange
        MemoryException exception = new MemoryException("Test error", "TEST_CODE");

        // Act
        String str = exception.toString();

        // Assert
        assertNotNull(str);
        assertTrue(str.contains("MemoryException") || str.contains("Test error"));
    }

    @Test
    @DisplayName("Should handle empty error code")
    void testEmptyErrorCode() {
        // Act
        MemoryException exception = new MemoryException("Error", "");

        // Assert
        assertEquals("Error", exception.getMessage());
        assertEquals("", exception.getErrorCode());
    }

    @Test
    @DisplayName("Should support suppressed exceptions")
    void testSuppressedExceptions() {
        // Arrange
        MemoryException exception = new MemoryException("Main error");
        Throwable suppressed1 = new RuntimeException("Suppressed 1");
        Throwable suppressed2 = new RuntimeException("Suppressed 2");

        // Act
        exception.addSuppressed(suppressed1);
        exception.addSuppressed(suppressed2);

        // Assert
        assertEquals(2, exception.getSuppressed().length);
        assertEquals(suppressed1, exception.getSuppressed()[0]);
        assertEquals(suppressed2, exception.getSuppressed()[1]);
    }

    @Test
    @DisplayName("Should set stack trace")
    void testSetStackTrace() {
        // Arrange
        MemoryException exception = new MemoryException("Error");
        StackTraceElement[] stackTrace = {
            new StackTraceElement("Class", "method", "file", 1)
        };

        // Act
        exception.setStackTrace(stackTrace);

        // Assert
        assertEquals(1, exception.getStackTrace().length);
        assertEquals("Class", exception.getStackTrace()[0].getClassName());
    }

    @Test
    @DisplayName("Should fill in stack trace")
    void testFillInStackTrace() {
        // Arrange
        MemoryException exception = new MemoryException("Error");

        // Act
        MemoryException result = exception.fillInStackTrace();

        // Assert
        assertSame(exception, result);
        assertNotNull(exception.getStackTrace());
        assertTrue(exception.getStackTrace().length > 0);
    }

    @Test
    @DisplayName("Should handle complex error scenarios")
    void testComplexErrorScenario() {
        // Arrange
        Throwable rootCause = new NullPointerException("Null value");
        MemoryException intermediate = new MemoryException(
            "Failed to process memory", rootCause, "PROCESSING_ERROR");

        // Act
        MemoryException topException = new MemoryException(
            "System error occurred", intermediate, "SYSTEM_ERROR");

        // Assert
        assertEquals("System error occurred", topException.getMessage());
        assertEquals("SYSTEM_ERROR", topException.getErrorCode());
        assertEquals(intermediate, topException.getCause());
        assertEquals("PROCESSING_ERROR", intermediate.getErrorCode());
        assertEquals(rootCause, intermediate.getCause());
    }

    @Test
    @DisplayName("Should maintain immutability of error code")
    void testErrorCodeImmutability() {
        // Arrange
        MemoryException exception = new MemoryException("Error", "CODE_123");
        String originalCode = exception.getErrorCode();

        // Act
        // Try to modify (if errorCode is not immutable, this could be an issue)
        String retrievedCode = exception.getErrorCode();

        // Assert
        assertSame(originalCode, retrievedCode);
    }

    @Test
    @DisplayName("Should handle special characters in message")
    void testSpecialCharactersInMessage() {
        // Arrange
        String message = "Error: !@#$%^&*()_+-=[]{}|;':\",./<>?";

        // Act
        MemoryException exception = new MemoryException(message);

        // Assert
        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("Should handle unicode characters")
    void testUnicodeCharacters() {
        // Arrange
        String message = "错误信息：内存操作失败";

        // Act
        MemoryException exception = new MemoryException(message);

        // Assert
        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("Should handle very long error messages")
    void testLongErrorMessage() {
        // Arrange
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longMessage.append("Error ");
        }

        // Act
        MemoryException exception = new MemoryException(longMessage.toString());

        // Assert
        assertEquals(longMessage.length(), exception.getMessage().length());
    }

    @Test
    @DisplayName("Should be usable in multi-catch blocks")
    void testMultiCatchBlock() {
        // Arrange
        Exception exception1 = new MemoryException("Memory error");
        Exception exception2 = new RuntimeException("Runtime error");

        // Act & Assert
        for (Exception e : new Exception[]{exception1, exception2}) {
            try {
                throw e;
            } catch (MemoryException me) {
                assertEquals("Memory error", me.getMessage());
            } catch (RuntimeException re) {
                assertEquals("Runtime error", re.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Should support custom error codes")
    void testCustomErrorCodes() {
        // Act
        MemoryException exception1 = new MemoryException("Error1", "CUSTOM_CODE_1");
        MemoryException exception2 = new MemoryException("Error2", "CUSTOM_CODE_2");

        // Assert
        assertEquals("CUSTOM_CODE_1", exception1.getErrorCode());
        assertEquals("CUSTOM_CODE_2", exception2.getErrorCode());
        assertNotEquals(exception1.getErrorCode(), exception2.getErrorCode());
    }
}
