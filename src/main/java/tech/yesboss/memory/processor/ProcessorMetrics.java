package tech.yesboss.memory.processor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics tracker for ContentProcessor operations
 *
 * <p>Tracks:
 * <ul>
 *   <li>Operation counts (total, success, failure)</li>
 *   <li>Processing time (min, max, average)</li>
 *   <li>Error rates by error type</li>
 *   <li>LLM call counts and latencies</li>
 * </ul>
 */
public class ProcessorMetrics {

    private final boolean enabled;
    private final boolean trackPerformance;
    private final boolean trackErrors;

    // Operation counters
    private final LongAdder segmentConversationCount;
    private final LongAdder segmentConversationSuccess;
    private final LongAdder segmentConversationFailure;

    private final LongAdder generateAbstractCount;
    private final LongAdder generateAbstractSuccess;
    private final LongAdder generateAbstractFailure;

    private final LongAdder extractMemoriesCount;
    private final LongAdder extractMemoriesSuccess;
    private final LongAdder extractMemoriesFailure;

    private final LongAdder identifyPreferencesCount;
    private final LongAdder identifyPreferencesSuccess;
    private final LongAdder identifyPreferencesFailure;

    private final LongAdder updatePreferenceCount;
    private final LongAdder updatePreferenceSuccess;
    private final LongAdder updatePreferenceFailure;

    // Performance metrics (in milliseconds)
    private final AtomicLong segmentConversationMinTime;
    private final AtomicLong segmentConversationMaxTime;
    private final LongAdder segmentConversationTotalTime;

    private final AtomicLong generateAbstractMinTime;
    private final AtomicLong generateAbstractMaxTime;
    private final LongAdder generateAbstractTotalTime;

    // Error tracking
    private final Map<String, LongAdder> errorCounts;

    // LLM call tracking
    private final LongAdder llmCallCount;
    private final LongAdder llmRetryCount;
    private final LongAdder llmTotalTime;

    /**
     * Create metrics tracker
     *
     * @param enabled Enable metrics collection
     * @param trackPerformance Track performance metrics
     * @param trackErrors Track error metrics
     */
    public ProcessorMetrics(boolean enabled, boolean trackPerformance, boolean trackErrors) {
        this.enabled = enabled;
        this.trackPerformance = trackPerformance;
        this.trackErrors = trackErrors;

        // Initialize counters
        this.segmentConversationCount = new LongAdder();
        this.segmentConversationSuccess = new LongAdder();
        this.segmentConversationFailure = new LongAdder();

        this.generateAbstractCount = new LongAdder();
        this.generateAbstractSuccess = new LongAdder();
        this.generateAbstractFailure = new LongAdder();

        this.extractMemoriesCount = new LongAdder();
        this.extractMemoriesSuccess = new LongAdder();
        this.extractMemoriesFailure = new LongAdder();

        this.identifyPreferencesCount = new LongAdder();
        this.identifyPreferencesSuccess = new LongAdder();
        this.identifyPreferencesFailure = new LongAdder();

        this.updatePreferenceCount = new LongAdder();
        this.updatePreferenceSuccess = new LongAdder();
        this.updatePreferenceFailure = new LongAdder();

        // Initialize performance metrics
        this.segmentConversationMinTime = new AtomicLong(Long.MAX_VALUE);
        this.segmentConversationMaxTime = new AtomicLong(0);
        this.segmentConversationTotalTime = new LongAdder();

        this.generateAbstractMinTime = new AtomicLong(Long.MAX_VALUE);
        this.generateAbstractMaxTime = new AtomicLong(0);
        this.generateAbstractTotalTime = new LongAdder();

        // Initialize error tracking
        this.errorCounts = new ConcurrentHashMap<>();

        // Initialize LLM tracking
        this.llmCallCount = new LongAdder();
        this.llmRetryCount = new LongAdder();
        this.llmTotalTime = new LongAdder();
    }

    // Segment conversation metrics
    public void recordSegmentConversationStart() {
        if (!enabled) return;
        segmentConversationCount.increment();
    }

    public void recordSegmentConversationSuccess(long durationMs) {
        if (!enabled) return;
        segmentConversationSuccess.increment();
        if (trackPerformance) {
            recordPerformanceTime(segmentConversationMinTime, segmentConversationMaxTime,
                    segmentConversationTotalTime, durationMs);
        }
    }

    public void recordSegmentConversationFailure(String errorCode) {
        if (!enabled) return;
        segmentConversationFailure.increment();
        if (trackErrors) {
            recordError("segmentConversation", errorCode);
        }
    }

    // Generate abstract metrics
    public void recordGenerateAbstractStart() {
        if (!enabled) return;
        generateAbstractCount.increment();
    }

    public void recordGenerateAbstractSuccess(long durationMs) {
        if (!enabled) return;
        generateAbstractSuccess.increment();
        if (trackPerformance) {
            recordPerformanceTime(generateAbstractMinTime, generateAbstractMaxTime,
                    generateAbstractTotalTime, durationMs);
        }
    }

    public void recordGenerateAbstractFailure(String errorCode) {
        if (!enabled) return;
        generateAbstractFailure.increment();
        if (trackErrors) {
            recordError("generateAbstract", errorCode);
        }
    }

    // Extract memories metrics
    public void recordExtractMemoriesStart() {
        if (!enabled) return;
        extractMemoriesCount.increment();
    }

    public void recordExtractMemoriesSuccess() {
        if (!enabled) return;
        extractMemoriesSuccess.increment();
    }

    public void recordExtractMemoriesFailure(String errorCode) {
        if (!enabled) return;
        extractMemoriesFailure.increment();
        if (trackErrors) {
            recordError("extractMemories", errorCode);
        }
    }

    // Identify preferences metrics
    public void recordIdentifyPreferencesStart() {
        if (!enabled) return;
        identifyPreferencesCount.increment();
    }

    public void recordIdentifyPreferencesSuccess() {
        if (!enabled) return;
        identifyPreferencesSuccess.increment();
    }

    public void recordIdentifyPreferencesFailure(String errorCode) {
        if (!enabled) return;
        identifyPreferencesFailure.increment();
        if (trackErrors) {
            recordError("identifyPreferences", errorCode);
        }
    }

    // Update preference metrics
    public void recordUpdatePreferenceStart() {
        if (!enabled) return;
        updatePreferenceCount.increment();
    }

    public void recordUpdatePreferenceSuccess() {
        if (!enabled) return;
        updatePreferenceSuccess.increment();
    }

    public void recordUpdatePreferenceFailure(String errorCode) {
        if (!enabled) return;
        updatePreferenceFailure.increment();
        if (trackErrors) {
            recordError("updatePreference", errorCode);
        }
    }

    // LLM call metrics
    public void recordLlmCall(long durationMs) {
        if (!enabled || !trackPerformance) return;
        llmCallCount.increment();
        llmTotalTime.add(durationMs);
    }

    public void recordLlmRetry() {
        if (!enabled) return;
        llmRetryCount.increment();
    }

    // Helper methods
    private void recordPerformanceTime(AtomicLong minTime, AtomicLong maxTime,
                                      LongAdder totalTime, long durationMs) {
        // Update min
        long currentMin = minTime.get();
        while (durationMs < currentMin && !minTime.compareAndSet(currentMin, durationMs)) {
            currentMin = minTime.get();
        }

        // Update max
        long currentMax = maxTime.get();
        while (durationMs > currentMax && !maxTime.compareAndSet(currentMax, durationMs)) {
            currentMax = maxTime.get();
        }

        // Add to total
        totalTime.add(durationMs);
    }

    private void recordError(String operation, String errorCode) {
        String key = operation + ":" + errorCode;
        errorCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    // Getters
    public long getSegmentConversationCount() {
        return segmentConversationCount.sum();
    }

    public long getSegmentConversationSuccess() {
        return segmentConversationSuccess.sum();
    }

    public long getSegmentConversationFailure() {
        return segmentConversationFailure.sum();
    }

    public double getSegmentConversationSuccessRate() {
        long total = getSegmentConversationCount();
        return total == 0 ? 0.0 : (double) getSegmentConversationSuccess() / total;
    }

    public long getGenerateAbstractCount() {
        return generateAbstractCount.sum();
    }

    public long getGenerateAbstractSuccess() {
        return generateAbstractSuccess.sum();
    }

    public long getGenerateAbstractFailure() {
        return generateAbstractFailure.sum();
    }

    public double getGenerateAbstractSuccessRate() {
        long total = getGenerateAbstractCount();
        return total == 0 ? 0.0 : (double) getGenerateAbstractSuccess() / total;
    }

    public long getExtractMemoriesCount() {
        return extractMemoriesCount.sum();
    }

    public long getExtractMemoriesSuccess() {
        return extractMemoriesSuccess.sum();
    }

    public long getExtractMemoriesFailure() {
        return extractMemoriesFailure.sum();
    }

    public long getIdentifyPreferencesCount() {
        return identifyPreferencesCount.sum();
    }

    public long getIdentifyPreferencesSuccess() {
        return identifyPreferencesSuccess.sum();
    }

    public long getIdentifyPreferencesFailure() {
        return identifyPreferencesFailure.sum();
    }

    public long getUpdatePreferenceCount() {
        return updatePreferenceCount.sum();
    }

    public long getUpdatePreferenceSuccess() {
        return updatePreferenceSuccess.sum();
    }

    public long getUpdatePreferenceFailure() {
        return updatePreferenceFailure.sum();
    }

    public long getSegmentConversationMinTime() {
        long min = segmentConversationMinTime.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }

    public long getSegmentConversationMaxTime() {
        return segmentConversationMaxTime.get();
    }

    public double getSegmentConversationAvgTime() {
        long count = segmentConversationSuccess.sum();
        long total = segmentConversationTotalTime.sum();
        return count == 0 ? 0.0 : (double) total / count;
    }

    public long getGenerateAbstractMinTime() {
        long min = generateAbstractMinTime.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }

    public long getGenerateAbstractMaxTime() {
        return generateAbstractMaxTime.get();
    }

    public double getGenerateAbstractAvgTime() {
        long count = generateAbstractSuccess.sum();
        long total = generateAbstractTotalTime.sum();
        return count == 0 ? 0.0 : (double) total / count;
    }

    public long getLlmCallCount() {
        return llmCallCount.sum();
    }

    public long getLlmRetryCount() {
        return llmRetryCount.sum();
    }

    public double getLlmAvgTime() {
        long count = llmCallCount.sum();
        long total = llmTotalTime.sum();
        return count == 0 ? 0.0 : (double) total / count;
    }

    public Map<String, Long> getErrorCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        errorCounts.forEach((key, adder) -> result.put(key, adder.sum()));
        return result;
    }

    /**
     * Get metrics summary
     *
     * @return Summary string
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("ProcessorMetrics{\n");
        sb.append("  enabled=").append(enabled).append("\n");
        sb.append("  segmentConversation: count=").append(getSegmentConversationCount())
                .append(", successRate=").append(String.format("%.2f%%", getSegmentConversationSuccessRate() * 100))
                .append(", avgTime=").append(String.format("%.2fms", getSegmentConversationAvgTime())).append("\n");
        sb.append("  generateAbstract: count=").append(getGenerateAbstractCount())
                .append(", successRate=").append(String.format("%.2f%%", getGenerateAbstractSuccessRate() * 100))
                .append(", avgTime=").append(String.format("%.2fms", getGenerateAbstractAvgTime())).append("\n");
        sb.append("  llm: calls=").append(getLlmCallCount())
                .append(", retries=").append(getLlmRetryCount())
                .append(", avgTime=").append(String.format("%.2fms", getLlmAvgTime())).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Reset all metrics
     */
    public void reset() {
        segmentConversationCount.reset();
        segmentConversationSuccess.reset();
        segmentConversationFailure.reset();

        generateAbstractCount.reset();
        generateAbstractSuccess.reset();
        generateAbstractFailure.reset();

        extractMemoriesCount.reset();
        extractMemoriesSuccess.reset();
        extractMemoriesFailure.reset();

        identifyPreferencesCount.reset();
        identifyPreferencesSuccess.reset();
        identifyPreferencesFailure.reset();

        updatePreferenceCount.reset();
        updatePreferenceSuccess.reset();
        updatePreferenceFailure.reset();

        segmentConversationMinTime.set(Long.MAX_VALUE);
        segmentConversationMaxTime.set(0);
        segmentConversationTotalTime.reset();

        generateAbstractMinTime.set(Long.MAX_VALUE);
        generateAbstractMaxTime.set(0);
        generateAbstractTotalTime.reset();

        errorCounts.clear();

        llmCallCount.reset();
        llmRetryCount.reset();
        llmTotalTime.reset();
    }
}
