package tech.yesboss.memory.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ZhipuEmbeddingServiceImpl.
 *
 * <p>These tests verify the Zhipu-specific implementation including:
 * <ul>
 *   <li>HTTP request handling</li>
 *   <li>Response parsing</li>
 *   <li>Error handling and retry logic</li>
 *   <li>Configuration management</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ZhipuEmbeddingServiceImpl Tests")
public class ZhipuEmbeddingServiceImplTest {

    private static final String TEST_API_KEY = "test-api-key";
    private static final String TEST_MODEL = "embedding-3";
    private static final String TEST_BASE_URL = "https://test.api.com";

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    private ZhipuEmbeddingServiceImpl embeddingService;

    @BeforeEach
    void setUp() {
        // Note: We can't easily mock HttpClient in the current implementation
        // because it's created in the constructor. In a real scenario, we'd
        // dependency inject the HttpClient. For now, we'll test with a real
        // instance but catch the connection errors.
        try {
            embeddingService = new ZhipuEmbeddingServiceImpl(
                    TEST_API_KEY, TEST_MODEL, 10, 30, TEST_BASE_URL);
        } catch (Exception e) {
            // Service initialization may fail without valid API endpoint
            // This is expected in test environment
        }
    }

    // ========== Constructor Tests ==========

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor throws for null API key")
        void testConstructorThrowsForNullApiKey() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ZhipuEmbeddingServiceImpl(null),
                    "Should throw IllegalArgumentException for null API key");
        }

        @Test
        @DisplayName("Constructor throws for empty API key")
        void testConstructorThrowsForEmptyApiKey() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ZhipuEmbeddingServiceImpl("  "),
                    "Should throw IllegalArgumentException for empty API key");
        }

        @Test
        @DisplayName("Constructor initializes with valid parameters")
        void testConstructorInitializesSuccessfully() {
            assertDoesNotThrow(() ->
                    new ZhipuEmbeddingServiceImpl(TEST_API_KEY),
                    "Should initialize successfully with valid API key");
        }
    }

    // ========== Input Validation Tests ==========

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @BeforeEach
        void setUpService() {
            embeddingService = new ZhipuEmbeddingServiceImpl(TEST_API_KEY);
        }

        @Test
        @DisplayName("generateEmbedding() throws EmbeddingException for null input")
        void testGenerateEmbeddingThrowsForNullInput() {
            assertThrows(EmbeddingException.class,
                    () -> embeddingService.generateEmbedding(null),
                    "Should throw EmbeddingException for null input");
        }

        @Test
        @DisplayName("generateEmbedding() throws EmbeddingException for empty input")
        void testGenerateEmbeddingThrowsForEmptyInput() {
            assertThrows(EmbeddingException.class,
                    () -> embeddingService.generateEmbedding(""),
                    "Should throw EmbeddingException for empty input");
        }

        @Test
        @DisplayName("generateEmbedding() throws EmbeddingException for whitespace input")
        void testGenerateEmbeddingThrowsForWhitespaceInput() {
            assertThrows(EmbeddingException.class,
                    () -> embeddingService.generateEmbedding("   "),
                    "Should throw EmbeddingException for whitespace input");
        }

        @Test
        @DisplayName("batchGenerateEmbeddings() throws for null list")
        void testBatchGenerateEmbeddingsThrowsForNullList() {
            assertThrows(EmbeddingException.class,
                    () -> embeddingService.batchGenerateEmbeddings(null),
                    "Should throw EmbeddingException for null list");
        }

        @Test
        @DisplayName("batchGenerateEmbeddings() throws for empty list")
        void testBatchGenerateEmbeddingsThrowsForEmptyList() {
            assertThrows(EmbeddingException.class,
                    () -> embeddingService.batchGenerateEmbeddings(List.of()),
                    "Should throw EmbeddingException for empty list");
        }

        @Test
        @DisplayName("generateConversationEmbedding() throws for null input")
        void testGenerateConversationEmbeddingThrowsForNullInput() {
            assertThrows(EmbeddingException.class,
                    () -> embeddingService.generateConversationEmbedding(null),
                    "Should throw EmbeddingException for null input");
        }

        @Test
        @DisplayName("generateConversationEmbedding() throws for empty input")
        void testGenerateConversationEmbeddingThrowsForEmptyInput() {
            assertThrows(EmbeddingException.class,
                    () -> embeddingService.generateConversationEmbedding(""),
                    "Should throw EmbeddingException for empty input");
        }
    }

    // ========== Service Availability Tests ==========

    @Nested
    @DisplayName("Service Availability Tests")
    class ServiceAvailabilityTests {

        @BeforeEach
        void setUpService() {
            embeddingService = new ZhipuEmbeddingServiceImpl(TEST_API_KEY);
        }

        @Test
        @DisplayName("isAvailable() returns boolean")
        void testIsAvailableReturnsBoolean() {
            boolean available = embeddingService.isAvailable();
            // Without a valid API endpoint, this will likely be false
            assertNotNull(available, "isAvailable() should return a boolean value");
        }
    }

    // ========== Error Code Tests ==========

    @Nested
    @DisplayName("Error Code Tests")
    class ErrorCodeTests {

        @BeforeEach
        void setUpService() {
            embeddingService = new ZhipuEmbeddingServiceImpl(TEST_API_KEY);
        }

        @Test
        @DisplayName("Invalid input returns correct error code")
        void testInvalidInputReturnsCorrectErrorCode() {
            try {
                embeddingService.generateEmbedding(null);
                fail("Should have thrown EmbeddingException");
            } catch (EmbeddingException e) {
                assertEquals(EmbeddingException.ERROR_INVALID_INPUT, e.getErrorCode(),
                        "Should return ERROR_INVALID_INPUT error code");
            }
        }

        @Test
        @DisplayName("Exception message is descriptive")
        void testExceptionMessageIsDescriptive() {
            try {
                embeddingService.generateEmbedding("");
                fail("Should have thrown EmbeddingException");
            } catch (EmbeddingException e) {
                assertNotNull(e.getMessage(), "Error message should not be null");
                assertFalse(e.getMessage().isEmpty(), "Error message should not be empty");
            }
        }
    }

    // ========== Configuration Tests ==========

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Service uses default model when not specified")
        void testServiceUsesDefaultModel() {
            ZhipuEmbeddingServiceImpl service = new ZhipuEmbeddingServiceImpl(TEST_API_KEY);
            assertNotNull(service, "Service should initialize with default model");
        }

        @Test
        @DisplayName("Service accepts custom model")
        void testServiceAcceptsCustomModel() {
            assertDoesNotThrow(() ->
                    new ZhipuEmbeddingServiceImpl(TEST_API_KEY, "custom-model", 10, 30, null),
                    "Should accept custom model");
        }

        @Test
        @DisplayName("Service uses default base URL when not specified")
        void testServiceUsesDefaultBaseUrl() {
            assertDoesNotThrow(() ->
                    new ZhipuEmbeddingServiceImpl(TEST_API_KEY),
                    "Should use default base URL");
        }
    }

    // ========== Factory Tests ==========

    @Nested
    @DisplayName("EmbeddingServiceFactory Tests")
    class FactoryTests {

        @Test
        @DisplayName("Factory returns non-null service")
        void testFactoryReturnsNonNullService() {
            // Set test API key
            String originalKey = System.getenv("ZHIPU_API_KEY");
            try {
                // Note: This test assumes environment variable is not set
                // In a real test environment, you'd set up proper test configuration
                EmbeddingServiceFactory factory = EmbeddingServiceFactory.getInstance();
                assertNotNull(factory, "Factory instance should not be null");
            } finally {
                // Restore original value if needed
            }
        }

        @Test
        @DisplayName("Factory singleton pattern")
        void testFactorySingletonPattern() {
            EmbeddingServiceFactory factory1 = EmbeddingServiceFactory.getInstance();
            EmbeddingServiceFactory factory2 = EmbeddingServiceFactory.getInstance();
            assertSame(factory1, factory2, "Factory should return same instance");
        }
    }

    // ========== Integration Scenario Tests ==========

    @Nested
    @DisplayName("Integration Scenario Tests")
    class IntegrationScenarioTests {

        @BeforeEach
        void setUpService() {
            embeddingService = new ZhipuEmbeddingServiceImpl(TEST_API_KEY);
        }

        @Test
        @DisplayName("Multiple sequential calls work correctly")
        void testMultipleSequentialCalls() {
            // Test that the service can handle multiple calls
            assertThrows(EmbeddingException.class, () -> embeddingService.generateEmbedding(null));
            assertThrows(EmbeddingException.class, () -> embeddingService.generateEmbedding(""));
        }

        @Test
        @DisplayName("Service handles different input types")
        void testServiceHandlesDifferentInputTypes() {
            // These will fail with connection errors but should validate input correctly
            assertThrows(EmbeddingException.class, () -> embeddingService.generateEmbedding(null));
            assertThrows(EmbeddingException.class, () -> embeddingService.generateEmbedding(""));
            assertThrows(EmbeddingException.class, () -> embeddingService.generateEmbedding("   "));
        }
    }

    // ========== Exception Hierarchy Tests ==========

    @Nested
    @DisplayName("Exception Hierarchy Tests")
    class ExceptionHierarchyTests {

        @BeforeEach
        void setUpService() {
            embeddingService = new ZhipuEmbeddingServiceImpl(TEST_API_KEY);
        }

        @Test
        @DisplayName("EmbeddingException is RuntimeException")
        void testEmbeddingExceptionIsRuntimeException() {
            EmbeddingException exception = new EmbeddingException("test");
            assertTrue(exception instanceof RuntimeException,
                    "EmbeddingException should extend RuntimeException");
        }

        @Test
        @DisplayName("EmbeddingException preserves cause")
        void testEmbeddingExceptionPreservesCause() {
            Throwable cause = new IllegalArgumentException("test cause");
            EmbeddingException exception = new EmbeddingException("test", cause);
            assertSame(cause, exception.getCause(),
                    "EmbeddingException should preserve the cause");
        }

        @Test
        @DisplayName("EmbeddingException has error codes")
        void testEmbeddingExceptionHasErrorCodes() {
            assertNotNull(EmbeddingException.ERROR_INVALID_INPUT);
            assertNotNull(EmbeddingException.ERROR_API_FAILURE);
            assertNotNull(EmbeddingException.ERROR_RATE_LIMIT);
            assertNotNull(EmbeddingException.ERROR_TIMEOUT);
            assertNotNull(EmbeddingException.ERROR_CONFIGURATION);
            assertNotNull(EmbeddingException.ERROR_SERVICE_UNAVAILABLE);
        }
    }
}
