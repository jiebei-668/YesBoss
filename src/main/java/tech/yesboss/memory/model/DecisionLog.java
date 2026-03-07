package tech.yesboss.memory.model;

import java.time.LocalDateTime;

/**
 * LLM决策日志
 *
 * 记录AgenticRAG检索过程中LLM的每层决策
 */
public class DecisionLog {

    /**
     * 检索层级
     */
    private AgenticRagResult.RetrievalLevel tier;

    /**
     * 决策类型
     */
    private DecisionType decision;

    /**
     * 决策理由
     */
    private String reasoning;

    /**
     * 候选结果数量
     */
    private int candidateCount;

    /**
     * 相似度阈值
     */
    private double similarityThreshold;

    /**
     * 平均相似度分数
     */
    private double averageSimilarity;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 决策类型枚举
     */
    public enum DecisionType {
        SUFFICIENT,    // 当前层结果已足够，无需继续检索
        CONTINUE,      // 需要继续下一层检索
        NO_RESULTS     // 当前层无结果，继续检索
    }

    public DecisionLog() {
        this.timestamp = LocalDateTime.now();
    }

    public DecisionLog(AgenticRagResult.RetrievalLevel tier, DecisionType decision, String reasoning) {
        this();
        this.tier = tier;
        this.decision = decision;
        this.reasoning = reasoning;
    }

    // Getters and Setters

    public AgenticRagResult.RetrievalLevel getTier() {
        return tier;
    }

    public void setTier(AgenticRagResult.RetrievalLevel tier) {
        this.tier = tier;
    }

    public DecisionType getDecision() {
        return decision;
    }

    public void setDecision(DecisionType decision) {
        this.decision = decision;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(int candidateCount) {
        this.candidateCount = candidateCount;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public double getAverageSimilarity() {
        return averageSimilarity;
    }

    public void setAverageSimilarity(double averageSimilarity) {
        this.averageSimilarity = averageSimilarity;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "DecisionLog{" +
                "tier=" + tier +
                ", decision=" + decision +
                ", reasoning='" + reasoning + '\'' +
                ", candidateCount=" + candidateCount +
                ", similarityThreshold=" + similarityThreshold +
                ", averageSimilarity=" + averageSimilarity +
                ", timestamp=" + timestamp +
                '}';
    }
}
