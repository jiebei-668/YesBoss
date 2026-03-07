package tech.yesboss.memory.embedding;

import java.util.List;

/**
 * Embedding service for generating vector representations of text.
 *
 * <p>This service provides text-to-vector conversion capabilities for semantic search
 * and similarity matching. It supports single text embedding, batch embedding, and
 * conversation-specific embedding.</p>
 *
 * <p>All embeddings are 1536-dimensional float32 arrays (6144 bytes), compatible
 * with OpenAI's text-embedding-3-small model specifications.</p>
 *
 * @see tech.yesboss.memory.vectorstore.VectorStore
 */
public interface EmbeddingService {

    /**
     * Generate embedding vector for a single text.
     *
     * @param text The text content to embed
     * @return 1536-dimensional float32 vector representation
     * @throws EmbeddingException if text is null/empty or embedding generation fails
     */
    float[] generateEmbedding(String text);

    /**
     * Generate embedding vectors for multiple texts in batch.
     *
     * @param texts List of text contents to embed
     * @return List of 1536-dimensional float32 vectors in the same order as input
     * @throws EmbeddingException if texts list is null/empty or embedding generation fails
     */
    List<float[]> batchGenerateEmbeddings(List<String> texts);

    /**
     * Generate embedding vector for conversation content.
     * Uses conversation-optimized model parameters for better context understanding.
     *
     * @param conversation The conversation content to embed
     * @return 1536-dimensional float32 vector representation
     * @throws EmbeddingException if conversation is null/empty or embedding generation fails
     */
    float[] generateConversationEmbedding(String conversation);

    /**
     * Check if the embedding service is available and properly configured.
     *
     * @return true if service is operational, false otherwise
     */
    boolean isAvailable();
}
