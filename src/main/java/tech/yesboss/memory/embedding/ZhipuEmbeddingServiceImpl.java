package tech.yesboss.memory.embedding;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Zhipu AI embedding service implementation.
 *
 * <p>This implementation uses Zhipu AI's embedding API to generate vector
 * representations of text. It supports both single and batch embedding operations.</p>
 *
 * <p>API Documentation: https://open.bigmodel.cn/dev/api#embedding</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>1536-dimensional float32 vectors (6144 bytes)</li>
 *   <li>Batch embedding support for improved performance</li>
 *   <li>Automatic retry on transient failures</li>
 *   <li>Configurable timeouts and connection settings</li>
 * </ul>
 */
public class ZhipuEmbeddingServiceImpl implements EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(ZhipuEmbeddingServiceImpl.class);

    private static final String EMBEDDING_ENDPOINT = "/api/paas/v4/embeddings";
    private static final String DEFAULT_MODEL = "embedding-3";
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_EMBEDDING_DIMENSION = 1536;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final String apiKey;
    private final String model;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final boolean available;

    /**
     * Create a new ZhipuEmbeddingServiceImpl with default settings.
     *
     * @param apiKey Zhipu API key (required)
     */
    public ZhipuEmbeddingServiceImpl(String apiKey) {
        this(apiKey, DEFAULT_MODEL, DEFAULT_CONNECT_TIMEOUT_SECONDS, DEFAULT_READ_TIMEOUT_SECONDS, null);
    }

    /**
     * Create a new ZhipuEmbeddingServiceImpl with custom settings.
     *
     * @param apiKey Zhipu API key (required)
     * @param model Model name (default "embedding-3")
     * @param connectTimeout Connection timeout in seconds
     * @param readTimeout Read timeout in seconds
     * @param baseUrl Base URL for API
     */
    public ZhipuEmbeddingServiceImpl(String apiKey, String model, int connectTimeout,
                                      int readTimeout, String baseUrl) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }

        this.apiKey = apiKey;
        this.model = model != null ? model : DEFAULT_MODEL;
        this.connectTimeoutSeconds = connectTimeout > 0 ? connectTimeout : DEFAULT_CONNECT_TIMEOUT_SECONDS;
        this.readTimeoutSeconds = readTimeout > 0 ? readTimeout : DEFAULT_READ_TIMEOUT_SECONDS;
        this.baseUrl = baseUrl != null ? baseUrl : "https://open.bigmodel.cn";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.connectTimeoutSeconds))
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Set availability to true by default (actual availability will be tested on first use)
        this.available = true;

        logger.info("ZhipuEmbeddingServiceImpl initialized with model: {}, baseUrl: {}, available: {}",
                this.model, this.baseUrl, this.available);
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new EmbeddingException("Text cannot be null or empty", EmbeddingException.ERROR_INVALID_INPUT);
        }

        try {
            EmbeddingRequest request = new EmbeddingRequest(model, text);
            EmbeddingResponse response = sendRequest(request);

            if (response != null && response.data != null && !response.data.isEmpty()) {
                float[] embedding = response.data.get(0).embedding;
                validateEmbeddingDimension(embedding);
                return embedding;
            } else {
                throw new EmbeddingException("Empty response from embedding API", EmbeddingException.ERROR_API_FAILURE);
            }
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("Failed to generate embedding: " + e.getMessage(),
                    EmbeddingException.ERROR_API_FAILURE, e);
        }
    }

    @Override
    public List<float[]> batchGenerateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new EmbeddingException("Texts list cannot be null or empty", EmbeddingException.ERROR_INVALID_INPUT);
        }

        // Split into batches if needed
        List<float[]> results = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);

            try {
                EmbeddingRequest request = new EmbeddingRequest(model, batch);
                EmbeddingResponse response = sendRequestWithRetry(request);

                if (response != null && response.data != null) {
                    for (EmbeddingData data : response.data) {
                        validateEmbeddingDimension(data.embedding);
                        results.add(data.embedding);
                    }
                }
            } catch (Exception e) {
                throw new EmbeddingException("Failed to generate embeddings for batch starting at index " + i +
                        ": " + e.getMessage(), EmbeddingException.ERROR_API_FAILURE, e);
            }
        }

        if (results.size() != texts.size()) {
            throw new EmbeddingException("Batch embedding size mismatch: expected " + texts.size() +
                    ", got " + results.size(), EmbeddingException.ERROR_API_FAILURE);
        }

        return results;
    }

    @Override
    public float[] generateConversationEmbedding(String conversation) {
        if (conversation == null || conversation.trim().isEmpty()) {
            throw new EmbeddingException("Conversation cannot be null or empty", EmbeddingException.ERROR_INVALID_INPUT);
        }

        // For conversations, we can use the same embedding API
        // In the future, this could use a conversation-specific model
        return generateEmbedding(conversation);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Test service availability by sending a minimal request.
     */
    private boolean testAvailability() {
        try {
            float[] testEmbedding = generateEmbedding("test");
            return testEmbedding != null && testEmbedding.length == DEFAULT_EMBEDDING_DIMENSION;
        } catch (Exception e) {
            logger.warn("Embedding service availability test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Send embedding request to the API.
     */
    private EmbeddingResponse sendRequest(EmbeddingRequest request) throws Exception {
        String requestBody = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + EMBEDDING_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + generateToken(apiKey))
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), EmbeddingResponse.class);
        } else if (response.statusCode() == 401) {
            throw new EmbeddingException("Invalid API key", EmbeddingException.ERROR_CONFIGURATION);
        } else if (response.statusCode() == 429) {
            throw new EmbeddingException("Rate limit exceeded", EmbeddingException.ERROR_RATE_LIMIT);
        } else if (response.statusCode() >= 500) {
            throw new EmbeddingException("API server error: " + response.statusCode(),
                    EmbeddingException.ERROR_SERVICE_UNAVAILABLE);
        } else {
            throw new EmbeddingException("API request failed with status " + response.statusCode() +
                    ": " + response.body(), EmbeddingException.ERROR_API_FAILURE);
        }
    }

    /**
     * Send embedding request with retry logic.
     */
    private EmbeddingResponse sendRequestWithRetry(EmbeddingRequest request) throws Exception {
        Exception lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return sendRequest(request);
            } catch (EmbeddingException e) {
                if (e.getErrorCode().equals(EmbeddingException.ERROR_RATE_LIMIT) ||
                        e.getErrorCode().equals(EmbeddingException.ERROR_SERVICE_UNAVAILABLE)) {
                    lastException = e;
                    if (attempt < MAX_RETRIES - 1) {
                        long delay = RETRY_DELAY_MS * (1L << attempt); // Exponential backoff
                        logger.warn("Embedding request failed (attempt {}/{}), retrying in {}ms: {}",
                                attempt + 1, MAX_RETRIES, delay, e.getMessage());
                        Thread.sleep(delay);
                    }
                } else {
                    throw e; // Don't retry non-retryable errors
                }
            }
        }

        throw new EmbeddingException("Failed after " + MAX_RETRIES + " retries",
                EmbeddingException.ERROR_API_FAILURE, lastException);
    }

    /**
     * Validate embedding dimension.
     */
    private void validateEmbeddingDimension(float[] embedding) {
        if (embedding == null || embedding.length != DEFAULT_EMBEDDING_DIMENSION) {
            throw new EmbeddingException("Invalid embedding dimension: expected " +
                    DEFAULT_EMBEDDING_DIMENSION + ", got " +
                    (embedding != null ? embedding.length : "null"), EmbeddingException.ERROR_API_FAILURE);
        }
    }

    /**
     * Generate JWT token for API authentication.
     * This is a simplified implementation - in production, use proper JWT libraries.
     */
    private String generateToken(String apiKey) {
        // For now, return the API key directly
        // In production, this should generate a proper JWT token
        return apiKey;
    }

    // Request/Response DTOs
    private static class EmbeddingRequest {
        private final String model;
        private final Object input;

        @JsonProperty("model")
        public String getModel() { return model; }

        @JsonProperty("input")
        public Object getInput() { return input; }

        public EmbeddingRequest(String model, String text) {
            this.model = model;
            this.input = text;
        }

        public EmbeddingRequest(String model, List<String> texts) {
            this.model = model;
            this.input = texts;
        }
    }

    private static class EmbeddingResponse {
        @JsonProperty("data")
        public List<EmbeddingData> data;

        @JsonProperty("model")
        public String model;

        @JsonProperty("object")
        public String object;

        @JsonProperty("usage")
        public Usage usage;
    }

    private static class EmbeddingData {
        @JsonProperty("embedding")
        public float[] embedding;

        @JsonProperty("index")
        public int index;

        @JsonProperty("object")
        public String object;
    }

    private static class Usage {
        @JsonProperty("prompt_tokens")
        public int promptTokens;

        @JsonProperty("total_tokens")
        public int totalTokens;
    }
}
