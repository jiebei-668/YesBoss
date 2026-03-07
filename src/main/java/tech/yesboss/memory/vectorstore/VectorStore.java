package tech.yesboss.memory.vectorstore;

import java.util.List;

/**
 * VectorStore abstraction layer for vector storage operations.
 *
 * Supports multiple backends:
 * - SQLite with sqlite-vec extension
 * - PostgreSQL with pgvector extension
 *
 * All vector operations use 1536-dimensional float32 vectors (6144 bytes).
 * Similarity calculation uses cosine similarity.
 */
public interface VectorStore {

    /**
     * Insert a vector into the store.
     *
     * @param vectorId Unique identifier for the vector
     * @param vector Vector data (1536-dimensional float32 array)
     * @return true if insertion successful, false otherwise
     * @throws VectorStoreException if insertion fails
     */
    boolean insert(String vectorId, float[] vector);

    /**
     * Insert multiple vectors in batch.
     *
     * @param vectors Map of vector IDs to vector data
     * @return Number of successfully inserted vectors
     * @throws VectorStoreException if batch insertion fails
     */
    int insertBatch(java.util.Map<String, float[]> vectors);

    /**
     * Update an existing vector.
     *
     * @param vectorId Unique identifier for the vector
     * @param vector New vector data (1536-dimensional float32 array)
     * @return true if update successful, false if vector not found
     * @throws VectorStoreException if update fails
     */
    boolean update(String vectorId, float[] vector);

    /**
     * Search for similar vectors.
     *
     * @param queryVector Query vector (1536-dimensional float32 array)
     * @param topK Number of results to return
     * @return List of search results sorted by similarity (highest first)
     * @throws VectorStoreException if search fails
     */
    List<SearchResult> search(float[] queryVector, int topK);

    /**
     * Delete a vector from the store.
     *
     * @param vectorId Unique identifier for the vector
     * @return true if deletion successful, false if vector not found
     * @throws VectorStoreException if deletion fails
     */
    boolean delete(String vectorId);

    /**
     * Delete multiple vectors in batch.
     *
     * @param vectorIds List of vector IDs to delete
     * @return Number of successfully deleted vectors
     * @throws VectorStoreException if batch deletion fails
     */
    int deleteBatch(List<String> vectorIds);

    /**
     * Check if a vector exists in the store.
     *
     * @param vectorId Unique identifier for the vector
     * @return true if vector exists, false otherwise
     */
    boolean exists(String vectorId);

    /**
     * Get the total number of vectors in the store.
     *
     * @return Total vector count
     */
    long count();

    /**
     * Clear all vectors from the store.
     *
     * @throws VectorStoreException if clearing fails
     */
    void clear();

    /**
     * Close the vector store and release resources.
     */
    void close();

    /**
     * Search result containing vector ID and similarity score.
     */
    record SearchResult(
        String vectorId,
        float score,
        java.util.Map<String, Object> metadata
    ) {
        public SearchResult {
            if (vectorId == null || vectorId.isEmpty()) {
                throw new IllegalArgumentException("vectorId cannot be null or empty");
            }
            if (score < 0 || score > 1) {
                throw new IllegalArgumentException("score must be between 0 and 1");
            }
        }

        public SearchResult(String vectorId, float score) {
            this(vectorId, score, java.util.Map.of());
        }
    }
}
