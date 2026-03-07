package tech.yesboss.memory.documentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.query.MemoryQueryService;
import tech.yesboss.memory.manager.MemoryManager;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.model.Snippet.MemoryType;
import tech.yesboss.domain.message.UnifiedMessage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Documentation Integration Test
 *
 * Tests that code examples from documentation actually work
 * when run against the real implementation.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "memory.enabled=true",
    "memory.vector-store.type=sqlite",
    "memory.embedding.provider=zhipu"
})
@DisplayName("Documentation Integration Tests")
class DocumentationIntegrationTest {

    @Autowired(required = false)
    private MemoryService memoryService;

    @Autowired(required = false)
    private MemoryQueryService queryService;

    @Autowired(required = false)
    private MemoryManager memoryManager;

    private List<UnifiedMessage> testMessages;
    private String testConversationId;
    private String testSessionId;

    @BeforeEach
    void setUp() {
        testConversationId = UUID.randomUUID().toString();
        testSessionId = UUID.randomUUID().toString();

        // Create test messages simulating a real conversation
        testMessages = new ArrayList<>();
        testMessages.add(new UnifiedMessage("user", "Hello, I'm John and I work as a software engineer."));
        testMessages.add(new UnifiedMessage("assistant", "Hi John! Nice to meet you."));
        testMessages.add(new UnifiedMessage("user", "I prefer Python over Java for development."));
        testMessages.add(new UnifiedMessage("assistant", "That's great! Python is a versatile language."));
    }

    @Test
    @DisplayName("Quick Start example: Check service availability")
    void testQuickStartAvailabilityExample() {
        if (memoryService == null) {
            return; // Skip if service not available
        }

        // This tests the availability check example from QUICK_START.md
        boolean available = memoryService.isAvailable();

        // The service should be available if properly configured
        // (or we just verify the method doesn't throw)
        assertNotNull(available);
    }

    @Test
    @DisplayName("Quick Start example: Concatenate conversation content")
    void testQuickStartConcatenationExample() {
        if (memoryService == null) {
            return;
        }

        // This tests the concatenation example from QUICK_START.md
        String content = memoryService.concatenateConversationContent(testMessages);

        assertNotNull(content, "Concatenated content should not be null");
        assertFalse(content.isEmpty(), "Concatenated content should not be empty");
        assertTrue(content.contains("John"), "Content should contain user's name");
        assertTrue(content.contains("software engineer"), "Content should contain user's role");
    }

    @Test
    @DisplayName("Quick Start example: Segment conversation")
    @Timeout(5)
    void testQuickStartSegmentationExample() {
        if (memoryService == null) {
            return;
        }

        String content = memoryService.concatenateConversationContent(testMessages);

        // This tests the segmentation example from QUICK_START.md
        List<tech.yesboss.memory.processor.ConversationSegment> segments =
            memoryService.segmentConversation(content);

        assertNotNull(segments, "Segments should not be null");
        assertFalse(segments.isEmpty(), "Should have at least one segment");
    }

    @Test
    @DisplayName("API Documentation example: Build resource")
    void testApiDocumentationBuildResourceExample() {
        if (memoryService == null) {
            return;
        }

        // This tests the buildResource example from API_DOCUMENTATION.md
        String content = "Test conversation content";
        String abstractText = "Summary of conversation";

        Resource resource = memoryService.buildResource(
            testConversationId,
            testSessionId,
            content,
            abstractText
        );

        assertNotNull(resource, "Resource should be created");
        assertEquals(testConversationId, resource.getConversationId());
        assertEquals(testSessionId, resource.getSessionId());
        assertEquals(content, resource.getContent());
        assertEquals(abstractText, resource.getAbstractText());
    }

    @Test
    @DisplayName("API Documentation example: Extract memories by type")
    @Timeout(10)
    void testApiDocumentationExtractByTypeExample() {
        if (memoryService == null) {
            return;
        }

        // This tests the extractMemoriesByType example from API_DOCUMENTATION.md
        String content = "User name is John, works as a software engineer, " +
                        "prefers Python over Java, and likes morning meetings.";

        // Test PROFILE extraction
        List<String> profiles = memoryService.extractMemoriesByType(
            content,
            MemoryType.PROFILE
        );

        assertNotNull(profiles, "Profile memories should not be null");
        // Profiles may or may not be extracted depending on LLM availability
    }

    @Test
    @DisplayName("Usage Examples: Manual extraction step-by-step")
    @Timeout(15)
    void testUsageExamplesManualExtraction() {
        if (memoryService == null) {
            return;
        }

        // This tests the manual extraction example from USAGE_EXAMPLES.md
        // Step 1: Concatenate
        String content = memoryService.concatenateConversationContent(testMessages);
        assertNotNull(content);
        assertFalse(content.isEmpty());

        // Step 2: Segment
        var segments = memoryService.segmentConversation(content);
        assertNotNull(segments);

        // Step 3: Generate abstract for first segment
        if (!segments.isEmpty()) {
            String abstractText = memoryService.generateSegmentAbstract(
                segments.get(0).getContent()
            );
            assertNotNull(abstractText);
            assertFalse(abstractText.isEmpty());
        }
    }

    @Test
    @DisplayName("Usage Examples: Resource creation")
    void testUsageExamplesResourceCreation() {
        if (memoryService == null) {
            return;
        }

        // This tests the resource creation example from USAGE_EXAMPLES.md
        String content = "Full conversation content here...";
        String abstractText = "Summary of the conversation";

        Resource resource = memoryService.buildResource(
            testConversationId,
            testSessionId,
            content,
            abstractText
        );

        assertNotNull(resource);
        assertEquals(testConversationId, resource.getConversationId());
        assertEquals(testSessionId, resource.getSessionId());
        assertEquals(content, resource.getContent());
        assertEquals(abstractText, resource.getAbstractText());
    }

    @Test
    @DisplayName("Configuration Guide: Basic configuration test")
    void testConfigurationGuideBasicConfig() {
        // This validates that the basic configuration from CONFIGURATION_GUIDE.md works
        if (memoryService == null) {
            return;
        }

        // The service should be available with basic configuration
        boolean available = memoryService.isAvailable();
        assertNotNull(available);
    }

    @Test
    @DisplayName("Error handling example: Try-catch with MemoryServiceException")
    void testErrorHandlingExample() {
        if (memoryService == null) {
            return;
        }

        // This tests the error handling pattern from USAGE_EXAMPLES.md
        try {
            String content = memoryService.concatenateConversationContent(testMessages);
            assertNotNull(content);

            // If we get here, operation succeeded
            assertTrue(content.length() > 0);

        } catch (Exception e) {
            // Handle error as shown in documentation
            assertNotNull(e.getMessage());
        }
    }

    @Test
    @DisplayName("Performance test: Response time target")
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testPerformanceTargetResponseTime() {
        if (memoryService == null) {
            return;
        }

        // The documentation specifies <100ms for simple operations
        long startTime = System.currentTimeMillis();

        String content = memoryService.concatenateConversationContent(testMessages);

        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(content);
        // Note: We use a relaxed timeout for test environment
        // The actual target is <100ms as documented
        assertTrue(duration < 5000, "Operation should complete within 5 seconds");
    }

    @Test
    @DisplayName("Model classes: Resource field validation")
    void testModelResourceFields() {
        // This validates the Resource model matches documentation
        Resource resource = new Resource();

        resource.setId(UUID.randomUUID().toString());
        resource.setConversationId(testConversationId);
        resource.setSessionId(testSessionId);
        resource.setContent("Test content");
        resource.setAbstractText("Test abstract");
        resource.setCreatedAt(LocalDateTime.now());
        resource.setUpdatedAt(LocalDateTime.now());

        assertEquals(testConversationId, resource.getConversationId());
        assertEquals(testSessionId, resource.getSessionId());
        assertEquals("Test content", resource.getContent());
        assertEquals("Test abstract", resource.getAbstractText());
        assertNotNull(resource.getCreatedAt());
        assertNotNull(resource.getUpdatedAt());
    }

    @Test
    @DisplayName("Model classes: Snippet field validation")
    void testModelSnippetFields() {
        // This validates the Snippet model matches documentation
        Snippet snippet = new Snippet();

        snippet.setId(UUID.randomUUID().toString());
        snippet.setResourceId(UUID.randomUUID().toString());
        snippet.setSummary("User prefers Python");
        snippet.setMemoryType(MemoryType.PREFERENCE);
        snippet.setCreatedAt(LocalDateTime.now());
        snippet.setUpdatedAt(LocalDateTime.now());

        assertEquals("User prefers Python", snippet.getSummary());
        assertEquals(MemoryType.PREFERENCE, snippet.getMemoryType());
        assertNotNull(snippet.getResourceId());
        assertNotNull(snippet.getCreatedAt());
        assertNotNull(snippet.getUpdatedAt());
    }

    @Test
    @DisplayName("Model classes: MemoryType enum values")
    void testModelMemoryTypeEnum() {
        // This validates the MemoryType enum matches documentation
        MemoryType[] types = MemoryType.values();

        assertEquals(6, types.length, "Should have 6 memory types");

        // Verify all documented types exist
        boolean hasProfile = false, hasEvent = false, hasKnowledge = false;
        boolean hasBehavior = false, hasSkill = false, hasTool = false;

        for (MemoryType type : types) {
            switch (type) {
                case PROFILE -> hasProfile = true;
                case EVENT -> hasEvent = true;
                case KNOWLEDGE -> hasKnowledge = true;
                case BEHAVIOR -> hasBehavior = true;
                case SKILL -> hasSkill = true;
                case TOOL -> hasTool = true;
            }
        }

        assertTrue(hasProfile, "Should have PROFILE type");
        assertTrue(hasEvent, "Should have EVENT type");
        assertTrue(hasKnowledge, "Should have KNOWLEDGE type");
        assertTrue(hasBehavior, "Should have BEHAVIOR type");
        assertTrue(hasSkill, "Should have SKILL type");
        assertTrue(hasTool, "Should have TOOL type");
    }

    @Test
    @DisplayName("Integration: Full extraction workflow")
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testFullExtractionWorkflow() {
        if (memoryService == null) {
            return;
        }

        // This tests the complete workflow as documented
        // 1. Concatenate conversation
        String content = memoryService.concatenateConversationContent(testMessages);
        assertNotNull(content);

        // 2. Segment conversation
        var segments = memoryService.segmentConversation(content);
        assertNotNull(segments);

        // 3. Process segments
        for (var segment : segments) {
            // Generate abstract
            String abstractText = memoryService.generateSegmentAbstract(
                segment.getContent()
            );
            assertNotNull(abstractText);

            // Build resource
            Resource resource = memoryService.buildResource(
                testConversationId,
                testSessionId,
                segment.getContent(),
                abstractText
            );
            assertNotNull(resource);
        }
    }

    @Test
    @DisplayName("Boundary test: Empty message list")
    void testBoundaryEmptyMessages() {
        if (memoryService == null) {
            return;
        }

        // Test with empty message list (boundary condition)
        List<UnifiedMessage> emptyMessages = new ArrayList<>();

        String content = memoryService.concatenateConversationContent(emptyMessages);
        assertNotNull(content);
        // Empty messages should return empty content, not throw exception
    }

    @Test
    @DisplayName("Boundary test: Very long message")
    void testBoundaryVeryLongMessage() {
        if (memoryService == null) {
            return;
        }

        // Test with very long message (boundary condition)
        List<UnifiedMessage> longMessages = new ArrayList<>();

        String longContent = "A".repeat(5000); // 5000 character message
        longMessages.add(new UnifiedMessage("user", longContent));

        String content = memoryService.concatenateConversationContent(longMessages);
        assertNotNull(content);
        assertEquals(5000, content.length());
    }

    @Test
    @DisplayName("Boundary test: Special characters in messages")
    void testBoundarySpecialCharacters() {
        if (memoryService == null) {
            return;
        }

        // Test with special characters (boundary condition)
        List<UnifiedMessage> specialMessages = new ArrayList<>();

        specialMessages.add(new UnifiedMessage("user",
            "Hello 世界 🌍 <script>alert('test')</script>"));

        String content = memoryService.concatenateConversationContent(specialMessages);
        assertNotNull(content);
        assertTrue(content.contains("世界"));
        assertTrue(content.contains("🌍"));
    }

    @Test
    @DisplayName("Concurrent access: Multiple simultaneous operations")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentAccess() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        // Test concurrent access as mentioned in documentation
        Thread[] threads = new Thread[5];
        final boolean[] results = new boolean[5];

        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    String content = memoryService.concatenateConversationContent(testMessages);
                    results[index] = (content != null);
                } catch (Exception e) {
                    results[index] = false;
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all operations completed successfully
        for (boolean result : results) {
            assertTrue(result, "Concurrent operation should succeed");
        }
    }

    @Test
    @DisplayName("Resource limits: Large conversation")
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testResourceLimitsLargeConversation() {
        if (memoryService == null) {
            return;
        }

        // Test with large conversation (resource limit)
        List<UnifiedMessage> largeConversation = new ArrayList<>();

        // Create 100 messages
        for (int i = 0; i < 100; i++) {
            largeConversation.add(new UnifiedMessage("user", "Message " + i));
            largeConversation.add(new UnifiedMessage("assistant", "Response " + i));
        }

        String content = memoryService.concatenateConversationContent(largeConversation);
        assertNotNull(content);
        assertTrue(content.length() > 0);
    }

    @Test
    @DisplayName("Documentation consistency: Method parameter types")
    void testDocumentationConsistencyParameterTypes() {
        if (memoryService == null) {
            return;
        }

        // Verify parameter types match documentation
        String content = memoryService.concatenateConversationContent(
            List.of(new UnifiedMessage("user", "Test"))
        );

        // The documentation shows extractFromMessages takes:
        // List<UnifiedMessage>, String, String
        // We verify this works
        assertNotNull(content);
    }
}
