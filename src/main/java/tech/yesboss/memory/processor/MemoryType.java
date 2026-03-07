package tech.yesboss.memory.processor;

/**
 * Memory type enumeration for structured memory classification.
 *
 * <p>Defines the types of structured memories that can be extracted
 * from conversation resources and stored as Snippets.</p>
 */
public enum MemoryType {
    /**
     * User profile information - personal characteristics, preferences, demographics
     */
    PROFILE("人物档案", "User profile and personal characteristics"),

    /**
     * Significant events - important occurrences, milestones, activities
     */
    EVENT("事件", "Significant events and occurrences"),

    /**
     * Domain knowledge - facts, information, expertise areas
     */
    KNOWLEDGE("知识", "Domain knowledge and factual information"),

    /**
     * Behavioral patterns - habits, routines, interaction styles
     */
    BEHAVIOR("行为模式", "Behavioral patterns and habits"),

    /**
     * User skills - capabilities, competencies, learned abilities
     */
    SKILL("技能", "User skills and capabilities"),

    /**
     * Tool usage - preferences for tools, software, platforms
     */
    TOOL("工具使用", "Tool preferences and usage patterns");

    private final String displayName;
    private final String description;

    MemoryType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Get the display name in Chinese.
     *
     * @return Display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the description in English.
     *
     * @return Description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get MemoryType from display name.
     *
     * @param displayName Display name to match
     * @return Matching MemoryType or null
     */
    public static MemoryType fromDisplayName(String displayName) {
        for (MemoryType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Get MemoryType from name (case-insensitive).
     *
     * @param name Name to match
     * @return Matching MemoryType or null
     */
    public static MemoryType fromName(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
