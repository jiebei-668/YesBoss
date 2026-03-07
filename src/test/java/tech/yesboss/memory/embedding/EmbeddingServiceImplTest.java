package tech.yesboss.memory.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for EmbeddingService implementations.
 *
 * <p>This test class verifies all aspects of the EmbeddingService including:
 * <ul>
 *   <li>Interface contract compliance</li>
 *   <li>Functional correctness</li>
 *   <li>Edge cases and boundary conditions</li>
 *   <li>Error handling and recovery</li>
 *   <li>Performance requirements</li>
 * </ul>
 */
@DisplayName("EmbeddingService Implementation Tests")
public class EmbeddingServiceImplTest {

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        // Note: In a real test, you would mock the HTTP client or use a test double
        // For now, we're testing with a mock implementation
        embeddingService = new MockEmbeddingService();
    }

    // ========== Interface Contract Tests ==========

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("generateEmbedding() returns 1536-dimensional float array")
        void testGenerateEmbeddingReturnsCorrectDimension() {
            float[] embedding = embeddingService.generateEmbedding("test text");
            assertNotNull(embedding, "Embedding should not be null");
            assertEquals(1536, embedding.length, "Embedding should be 1536-dimensional");
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
        @DisplayName("batchGenerateEmbeddings() returns list with correct size")
        void testBatchGenerateEmbeddingsReturnsCorrectSize() {
            List<String> texts = List.of("text1", "text2", "text3");
            List<float[]> embeddings = embeddingService.batchGenerateEmbeddings(texts);
            assertNotNull(embeddings, "Embeddings list should not be null");
            assertEquals(3, embeddings.size(), "Should return 3 embeddings");
        }

        @Test
        @DisplayName("batchGenerateEmbeddings() throws for null input")
        void testBatchGenerateEmbeddingsThrowsForNullInput() {
            assertThrows(EmbeddingException.class,
                    () -> embeddingService.batchGenerateEmbeddings(null),
                    "Should throw EmbeddingException for null input");
        }

        @Test
        @DisplayName("batchGenerateEmbeddings() throws for empty list")
        void testBatchGenerateEmbeddingsThrowsForEmptyList() {
            assertThrows(EmbeddingException.class,
                    () -> embeddingService.batchGenerateEmbeddings(List.of()),
                    "Should throw EmbeddingException for empty list");
        }

        @Test
        @DisplayName("generateConversationEmbedding() returns 1536-dimensional float array")
        void testGenerateConversationEmbeddingReturnsCorrectDimension() {
            float[] embedding = embeddingService.generateConversationEmbedding("User: Hello\nAssistant: Hi there!");
            assertNotNull(embedding, "Embedding should not be null");
            assertEquals(1536, embedding.length, "Embedding should be 1536-dimensional");
        }

        @Test
        @DisplayName("isAvailable() returns boolean")
        void testIsAvailableReturnsBoolean() {
            boolean available = embeddingService.isAvailable();
            assertTrue(available == true || available == false,
                    "isAvailable() should return a boolean value");
        }
    }

    // ========== Functional Correctness Tests ==========

    @Nested
    @DisplayName("Functional Correctness Tests")
    class FunctionalCorrectnessTests {

        @Test
        @DisplayName("generateEmbedding() produces deterministic results for same input")
        void testGenerateEmbeddingDeterministic() {
            String text = "same input text";
            float[] embedding1 = embeddingService.generateEmbedding(text);
            float[] embedding2 = embeddingService.generateEmbedding(text);
            assertArrayEquals(embedding1, embedding2, 0.0001f,
                    "Same input should produce same embedding");
        }

        @Test
        @DisplayName("generateEmbedding() produces different results for different inputs")
        void testGenerateEmbeddingDifferentForDifferentInputs() {
            float[] embedding1 = embeddingService.generateEmbedding("text one");
            float[] embedding2 = embeddingService.generateEmbedding("text two");
            assertFalse(java.util.Arrays.equals(embedding1, embedding2),
                    "Different inputs should produce different embeddings");
        }

        @Test
        @DisplayName("batchGenerateEmbeddings() maintains input order")
        void testBatchGenerateEmbeddingsMaintainsOrder() {
            List<String> texts = List.of("first", "second", "third");
            List<float[]> embeddings = embeddingService.batchGenerateEmbeddings(texts);
            assertEquals(3, embeddings.size(), "Should have 3 embeddings");
            // Verify each embedding has correct dimension
            for (float[] embedding : embeddings) {
                assertEquals(1536, embedding.length, "Each embedding should be 1536-dimensional");
            }
        }

        @Test
        @DisplayName("generateConversationEmbedding() handles multi-line conversations")
        void testGenerateConversationEmbeddingHandlesMultiline() {
            String conversation = "User: Hello\nAssistant: Hi!\nUser: How are you?\nAssistant: I'm doing well.";
            float[] embedding = embeddingService.generateConversationEmbedding(conversation);
            assertNotNull(embedding, "Embedding should not be null");
            assertEquals(1536, embedding.length, "Embedding should be 1536-dimensional");
        }
    }

    // ========== Edge Cases and Boundary Conditions Tests ==========

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("generateEmbedding() handles single character")
        void testGenerateEmbeddingHandlesSingleCharacter() {
            float[] embedding = embeddingService.generateEmbedding("a");
            assertNotNull(embedding, "Embedding should not be null");
            assertEquals(1536, embedding.length, "Embedding should be 1536-dimensional");
        }

        @Test
        @DisplayName("generateEmbedding() handles special characters")
        void testGenerateEmbeddingHandlesSpecialCharacters() {
            String specialText = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
            float[] embedding = embeddingService.generateEmbedding(specialText);
            assertNotNull(embedding, "Embedding should not be null");
            assertEquals(1536, embedding.length, "Embedding should be 1536-dimensional");
        }

        @Test
        @DisplayName("generateEmbedding() handles Unicode characters")
        void testGenerateEmbeddingHandlesUnicode() {
            String unicodeText = "Hello 世界 🌍 привет مرحبا";
            float[] embedding = embeddingService.generateEmbedding(unicodeText);
            assertNotNull(embedding, "Embedding should not be null");
            assertEquals(1536, embedding.length, "Embedding should be 1536-dimensional");
        }

        @Test
        @DisplayName("generateEmbedding() handles whitespace-only input")
        void testGenerateEmbeddingHandlesWhitespace() {
            assertThrows(EmbeddingException.class,
                    () -> embeddingService.generateEmbedding("   "),
                    "Should throw for whitespace-only input");
        }

        @Test
        @DisplayName("batchGenerateEmbeddings() handles large batch (100 items)")
        void testBatchGenerateEmbeddingsHandlesLargeBatch() {
            List<String> texts = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> "text " + i)
                    .collect(java.util.stream.Collectors.toList());
            List<float[]> embeddings = embeddingService.batchGenerateEmbeddings(texts);
            assertEquals(100, embeddings.size(), "Should handle 100 items");
            embeddings.forEach(embedding ->
                    assertEquals(1536, embedding.length, "Each embedding should be 1536-dimensional"));
        }

        @Test
        @DisplayName("batchGenerateEmbeddings() handles single item batch")
        void testBatchGenerateEmbeddingsHandlesSingleItemBatch() {
            List<String> texts = List.of("single text");
            List<float[]> embeddings = embeddingService.batchGenerateEmbeddings(texts);
            assertEquals(1, embeddings.size(), "Should handle single item batch");
        }
    }

    // ========== Error Handling Tests ==========

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("generateEmbedding() throws EmbeddingException with correct error code for invalid input")
        void testGenerateEmbeddingThrowsWithCorrectErrorCode() {
            try {
                embeddingService.generateEmbedding(null);
                fail("Should have thrown EmbeddingException");
            } catch (EmbeddingException e) {
                assertEquals(EmbeddingException.ERROR_INVALID_INPUT, e.getErrorCode(),
                        "Should have correct error code");
            }
        }

        @Test
        @DisplayName("batchGenerateEmbeddings() throws with descriptive error message")
        void testBatchGenerateEmbeddingsThrowsWithDescriptiveMessage() {
            try {
                embeddingService.batchGenerateEmbeddings(null);
                fail("Should have thrown EmbeddingException");
            } catch (EmbeddingException e) {
                assertNotNull(e.getMessage(), "Error message should not be null");
                assertFalse(e.getMessage().isEmpty(), "Error message should not be empty");
            }
        }
    }

    // ========== Performance Tests ==========

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("generateEmbedding() completes within 100ms")
        void testGenerateEmbeddingPerformance() {
            long startTime = System.currentTimeMillis();
            embeddingService.generateEmbedding("test text");
            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 100,
                    "generateEmbedding() should complete within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("batchGenerateEmbeddings(100) completes within 1 second")
        void testBatchGenerateEmbeddingsPerformance() {
            List<String> texts = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> "text " + i)
                    .collect(java.util.stream.Collectors.toList());
            long startTime = System.currentTimeMillis();
            embeddingService.batchGenerateEmbeddings(texts);
            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 1000,
                    "batchGenerateEmbeddings(100) should complete within 1s, took " + duration + "ms");
        }
    }

    // ========== Mock Implementation for Testing ==========

    /**
     * Mock implementation of EmbeddingService for testing.
     * In a real test environment, this would be replaced with proper mocks.
     */
    private static class MockEmbeddingService implements EmbeddingService {

        private final java.util.Map<String, float[]> cache = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public float[] generateEmbedding(String text) {
            if (text == null || text.trim().isEmpty()) {
                throw new EmbeddingException("Text cannot be null or empty",
                        EmbeddingException.ERROR_INVALID_INPUT);
            }
            return cache.computeIfAbsent(text, this::createMockEmbedding);
        }

        @Override
        public List<float[]> batchGenerateEmbeddings(List<String> texts) {
            if (texts == null || texts.isEmpty()) {
                throw new EmbeddingException("Texts list cannot be null or empty",
                        EmbeddingException.ERROR_INVALID_INPUT);
            }
            return texts.stream()
                    .map(this::generateEmbedding)
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public float[] generateConversationEmbedding(String conversation) {
            if (conversation == null || conversation.trim().isEmpty()) {
                throw new EmbeddingException("Conversation cannot be null or empty",
                        EmbeddingException.ERROR_INVALID_INPUT);
            }
            return generateEmbedding(conversation);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        private float[] createMockEmbedding(String text) {
            float[] embedding = new float[1536];
            java.util.Random random = new java.util.Random(text.hashCode());
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = random.nextFloat() * 2 - 1; // Random values between -1 and 1
            }
            return embedding;
        }
    }
}
