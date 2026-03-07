package tech.yesboss.memory.error;

/**
 * Circuit Breaker Configuration
 *
 * <p>Defines circuit breaker behavior for protecting against cascading failures.</p>
 *
 * <p><b>Circuit Breaker States:</b></p>
 * <ul>
 *   <li>CLOSED: Normal operation, requests pass through</li>
 *   <li>OPEN: Circuit open, requests rejected</li>
 *   <li>HALF_OPEN: Testing if service has recovered</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * CircuitBreakerConfig config = CircuitBreakerConfig.builder()
 *     .failureThreshold(5)
 *     .successThreshold(3)
 *     .timeoutMs(60000)
 *     .build();
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class CircuitBreakerConfig {

    private final int failureThreshold;
    private final int successThreshold;
    private final long timeoutMs;
    private final long halfOpenTimeoutMs;
    private final boolean enabled;
    private final double failureRateThreshold;
    private final int minimumRequests;
    private final int slidingWindowSize;

    /**
     * Private constructor - use builder
     */
    private CircuitBreakerConfig(Builder builder) {
        this.failureThreshold = builder.failureThreshold;
        this.successThreshold = builder.successThreshold;
        this.timeoutMs = builder.timeoutMs;
        this.halfOpenTimeoutMs = builder.halfOpenTimeoutMs;
        this.enabled = builder.enabled;
        this.failureRateThreshold = builder.failureRateThreshold;
        this.minimumRequests = builder.minimumRequests;
        this.slidingWindowSize = builder.slidingWindowSize;
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
     * Create default circuit breaker configuration.
     *
     * @return Default configuration
     */
    public static CircuitBreakerConfig defaults() {
        return builder().build();
    }

    /**
     * Create disabled circuit breaker (no protection).
     *
     * @return Disabled configuration
     */
    public static CircuitBreakerConfig disabled() {
        return builder().enabled(false).build();
    }

    // Getters
    public int getFailureThreshold() {
        return failureThreshold;
    }

    public int getSuccessThreshold() {
        return successThreshold;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public long getHalfOpenTimeoutMs() {
        return halfOpenTimeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public int getMinimumRequests() {
        return minimumRequests;
    }

    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    /**
     * Builder for CircuitBreakerConfig
     */
    public static class Builder {
        private int failureThreshold = 5;
        private int successThreshold = 3;
        private long timeoutMs = 60000; // 1 minute
        private long halfOpenTimeoutMs = 30000; // 30 seconds
        private boolean enabled = true;
        private double failureRateThreshold = 0.5; // 50%
        private int minimumRequests = 10;
        private int slidingWindowSize = 100;

        public Builder failureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        public Builder successThreshold(int successThreshold) {
            this.successThreshold = successThreshold;
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder halfOpenTimeoutMs(long halfOpenTimeoutMs) {
            this.halfOpenTimeoutMs = halfOpenTimeoutMs;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder failureRateThreshold(double failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
            return this;
        }

        public Builder minimumRequests(int minimumRequests) {
            this.minimumRequests = minimumRequests;
            return this;
        }

        public Builder slidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
            return this;
        }

        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(this);
        }
    }

    @Override
    public String toString() {
        return "CircuitBreakerConfig{" +
                "failureThreshold=" + failureThreshold +
                ", successThreshold=" + successThreshold +
                ", timeoutMs=" + timeoutMs +
                ", halfOpenTimeoutMs=" + halfOpenTimeoutMs +
                ", enabled=" + enabled +
                ", failureRateThreshold=" + failureRateThreshold +
                ", minimumRequests=" + minimumRequests +
                ", slidingWindowSize=" + slidingWindowSize +
                '}';
    }
}
