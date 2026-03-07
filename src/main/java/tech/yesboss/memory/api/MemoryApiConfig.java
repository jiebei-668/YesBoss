package tech.yesboss.memory.api;

import tech.yesboss.memory.config.MemoryConfig;

/**
 * Memory API Configuration
 *
 * <p>Configuration properties for the Memory API layer, loaded from MemoryConfig.</p>
 *
 * <p>Default values:</p>
 * <ul>
 *   <li>defaultTopK: 10</li>
 *   <li>maxTopK: 100</li>
 *   <li>queryTimeoutMs: 10000 (10 seconds)</li>
 *   <li>maxRetryAttempts: 3</li>
 * </ul>
 */
public class MemoryApiConfig {

    private final int defaultTopK;
    private final int maxTopK;
    private final long queryTimeoutMs;
    private final int maxRetryAttempts;

    public MemoryApiConfig(MemoryConfig memoryConfig) {
        // Load from system properties or use defaults
        this.defaultTopK = Integer.parseInt(System.getProperty(
            "memory.api.defaultTopK", "10"));
        this.maxTopK = Integer.parseInt(System.getProperty(
            "memory.api.maxTopK", "100"));
        this.queryTimeoutMs = Long.parseLong(System.getProperty(
            "memory.api.queryTimeoutMs", "10000"));
        this.maxRetryAttempts = Integer.parseInt(System.getProperty(
            "memory.api.maxRetryAttempts", "3"));
    }

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public int getMaxTopK() {
        return maxTopK;
    }

    public long getQueryTimeoutMs() {
        return queryTimeoutMs;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }
}
