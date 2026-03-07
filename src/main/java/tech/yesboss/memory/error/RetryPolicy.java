package tech.yesboss.memory.error;

/**
 * Retry Policy Configuration
 *
 * <p>Defines retry behavior for failed operations.</p>
 *
 * <p><b>Retry Strategies:</b></p>
 * <ul>
 *   <li>FIXED_DELAY: Constant delay between retries</li>
 *   <li>EXPONENTIAL_BACKOFF: Increasing delay between retries</li>
 *   <li>LINEAR_BACKOFF: Linearly increasing delay</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * RetryPolicy policy = RetryPolicy.builder()
 *     .maxAttempts(3)
 *     .strategy(RetryStrategy.EXPONENTIAL_BACKOFF)
 *     .initialDelayMs(100)
 *     .maxDelayMs(5000)
 *     .build();
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class RetryPolicy {

    /**
     * Retry strategy enumeration
     */
    public enum RetryStrategy {
        FIXED_DELAY,         // Fixed delay between retries
        EXPONENTIAL_BACKOFF, // Exponential backoff (delay * 2^attempt)
        LINEAR_BACKOFF       // Linear backoff (delay + increment * attempt)
    }

    private final int maxAttempts;
    private final RetryStrategy strategy;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double backoffMultiplier;
    private final long jitterMs;
    private final boolean retryOnTimeout;
    private final boolean retryOnSpecificExceptions;

    /**
     * Private constructor - use builder
     */
    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.strategy = builder.strategy;
        this.initialDelayMs = builder.initialDelayMs;
        this.maxDelayMs = builder.maxDelayMs;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.jitterMs = builder.jitterMs;
        this.retryOnTimeout = builder.retryOnTimeout;
        this.retryOnSpecificExceptions = builder.retryOnSpecificExceptions;
    }

    /**
     * Create a new builder.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create default retry policy.
     *
     * @return Default policy (3 attempts, exponential backoff)
     */
    public static RetryPolicy defaults() {
        return builder().build();
    }

    /**
     * Create fixed delay retry policy.
     *
     * @param maxAttempts Maximum number of attempts
     * @param delayMs Delay between retries in milliseconds
     * @return Retry policy
     */
    public static RetryPolicy fixedDelay(int maxAttempts, long delayMs) {
        return builder()
                .maxAttempts(maxAttempts)
                .strategy(RetryStrategy.FIXED_DELAY)
                .initialDelayMs(delayMs)
                .build();
    }

    /**
     * Create exponential backoff retry policy.
     *
     * @param maxAttempts Maximum number of attempts
     * @param initialDelayMs Initial delay in milliseconds
     * @param maxDelayMs Maximum delay in milliseconds
     * @return Retry policy
     */
    public static RetryPolicy exponentialBackoff(int maxAttempts, long initialDelayMs, long maxDelayMs) {
        return builder()
                .maxAttempts(maxAttempts)
                .strategy(RetryStrategy.EXPONENTIAL_BACKOFF)
                .initialDelayMs(initialDelayMs)
                .maxDelayMs(maxDelayMs)
                .build();
    }

    /**
     * Calculate delay for given attempt.
     *
     * @param attempt Attempt number (0-indexed)
     * @return Delay in milliseconds
     */
    public long calculateDelay(int attempt) {
        long delay;

        switch (strategy) {
            case FIXED_DELAY:
                delay = initialDelayMs;
                break;
            case EXPONENTIAL_BACKOFF:
                delay = (long) (initialDelayMs * Math.pow(backoffMultiplier, attempt));
                break;
            case LINEAR_BACKOFF:
                delay = initialDelayMs + (attempt * 100); // 100ms increment
                break;
            default:
                delay = initialDelayMs;
        }

        // Apply max delay limit
        delay = Math.min(delay, maxDelayMs);

        // Add jitter if configured
        if (jitterMs > 0) {
            delay += (long) (Math.random() * jitterMs);
        }

        return Math.max(0, delay);
    }

    // Getters
    public int getMaxAttempts() {
        return maxAttempts;
    }

    public RetryStrategy getStrategy() {
        return strategy;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public long getJitterMs() {
        return jitterMs;
    }

    public boolean isRetryOnTimeout() {
        return retryOnTimeout;
    }

    public boolean isRetryOnSpecificExceptions() {
        return retryOnSpecificExceptions;
    }

    /**
     * Builder for RetryPolicy
     */
    public static class Builder {
        private int maxAttempts = 3;
        private RetryStrategy strategy = RetryStrategy.EXPONENTIAL_BACKOFF;
        private long initialDelayMs = 100;
        private long maxDelayMs = 5000;
        private double backoffMultiplier = 2.0;
        private long jitterMs = 0;
        private boolean retryOnTimeout = true;
        private boolean retryOnSpecificExceptions = true;

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder strategy(RetryStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder initialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        public Builder maxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
            return this;
        }

        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        public Builder jitterMs(long jitterMs) {
            this.jitterMs = jitterMs;
            return this;
        }

        public Builder retryOnTimeout(boolean retryOnTimeout) {
            this.retryOnTimeout = retryOnTimeout;
            return this;
        }

        public Builder retryOnSpecificExceptions(boolean retryOnSpecificExceptions) {
            this.retryOnSpecificExceptions = retryOnSpecificExceptions;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }

    @Override
    public String toString() {
        return "RetryPolicy{" +
                "maxAttempts=" + maxAttempts +
                ", strategy=" + strategy +
                ", initialDelayMs=" + initialDelayMs +
                ", maxDelayMs=" + maxDelayMs +
                ", backoffMultiplier=" + backoffMultiplier +
                '}';
    }
}
