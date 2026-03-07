package tech.yesboss.memory.e2e;

import org.junit.jupiter.api.*;
import tech.yesboss.config.ConfigurationManager;
import tech.yesboss.config.YesBossConfig;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.domain.message.UnifiedMessage.Role;
import tech.yesboss.domain.message.UnifiedMessage.PayloadFormat;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.repository.PreferenceRepository;
import tech.yesboss.memory.repository.ResourceRepository;
import tech.yesboss.memory.repository.SnippetRepository;
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.service.MemoryServiceImpl;
import tech.yesboss.memory.manager.MemoryManagerImpl;
import tech.yesboss.memory.processor.ZhipuContentProcessorImpl;
import tech.yesboss.memory.processor.ContentProcessor;
import tech.yesboss.memory.embedding.EmbeddingServiceFactory;
import tech.yesboss.memory.trigger.TriggerService;
import tech.yesboss.memory.trigger.TriggerServiceImpl;
import tech.yesboss.persistence.db.DatabaseInitializer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Memory Extraction Test
 *
 * <p>This test verifies the complete end-to-end flow from conversation input
 * to memory storage, including:</p>
 *
 * <ul>
 *   <li><b>Real Scenario Simulation:</b> Simulates real user conversations with
 *       multiple messages covering different topics</li>
 *   <li><b>Complete Flow Validation:</b> Verifies the entire chain from message
 *       input through extraction to database storage</li>
 *   <li><b>Business Logic Verification:</b> Validates that the business logic
 *       correctly processes and stores memories</li>
 *   <li><b>Performance Testing:</b> Ensures end-to-end response time < 2s</li>
 *   <li><b>Data Persistence Verification:</b> Confirms data is correctly stored,
 *       queryable, and consistent</li>
 * </ul>
 *
 * <p><b>Test Frameworks:</b> JUnit 5</p>
 * <p><b>Reference:</b> docs_memory/时序图3.0/01-自动触发记忆提取流程.md</p>
 *
 * <p><b>Note:</b> This test uses real components (not mocks) to verify the
 * actual system behavior. For faster testing with mocks, see
 * {@link tech.yesboss.memory.integration.MemoryExtractionIntegrationTest}</p>
 */
@DisplayName("End-to-End Memory Extraction Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndMemoryExtractionTest {

    private static YesBossConfig testConfig;
    private static DatabaseInitializer databaseInitializer;

    private MemoryService memoryService;
    private TriggerService triggerService;
    private ResourceRepository resourceRepository;
    private SnippetRepository snippetRepository;
    private PreferenceRepository preferenceRepository;

    private String testConversationId;
    private String testSessionId;

    // ==================== Setup and Teardown ====================

    @BeforeAll
    static void setUpOnce() throws Exception {
        System.out.println("========================================");
        System.out.println("Setting up End-to-End Memory Extraction Test");
        System.out.println("========================================");

        try {
            // Load test configuration
            testConfig = ConfigurationManager.getInstance().getConfig();
            // Use in-memory database for testing
            testConfig.getDatabase().getSqlite().setPath(":memory:");

            // Initialize database
            databaseInitializer = new DatabaseInitializer(testConfig);
            databaseInitializer.initialize();

            System.out.println("Database initialized successfully");
        } catch (Exception e) {
            System.err.println("Warning: Could not fully initialize: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("========================================");
    }

    @AfterAll
    static void tearDownOnce() {
        System.out.println("========================================");
        System.out.println("Tearing down End-to-End Memory Extraction Test");
        System.out.println("========================================");

        if (databaseInitializer != null) {
            databaseInitializer.close();
        }

        System.out.println("Teardown completed");
        System.out.println("========================================");
    }

    @BeforeEach
    void setUp() {
        System.out.println("\n--- Setting up test case ---");

        try {
            // Initialize repositories
            resourceRepository = new ResourceRepository(testConfig);
            snippetRepository = new SnippetRepository(testConfig);
            preferenceRepository = new PreferenceRepository(testConfig);

            // Initialize services
            MemoryManagerImpl memoryManager = new MemoryManagerImpl(
                    testConfig,
                    resourceRepository,
                    snippetRepository,
                    preferenceRepository
            );

            EmbeddingService embeddingService = EmbeddingServiceFactory.getInstance(testConfig);
            ContentProcessorImpl contentProcessor = new ContentProcessorImpl(testConfig);

            memoryService = new MemoryServiceImpl(
                    contentProcessor,
                    memoryManager,
                    resourceRepository,
                    snippetRepository,
                    preferenceRepository,
                    embeddingService
            );

            triggerService = new TriggerServiceImpl(testConfig, memoryService);

        } catch (Exception e) {
            System.err.println("Warning: Could not initialize all components: " + e.getMessage());
            e.printStackTrace();
        }

        // Generate unique IDs for this test
        testConversationId = "test-conv-" + System.currentTimeMillis();
        testSessionId = "test-session-" + System.currentTimeMillis();

        System.out.println("Test setup completed");
        System.out.println("Conversation ID: " + testConversationId);
        System.out.println("Session ID: " + testSessionId);
    }

    @AfterEach
    void tearDown() {
        System.out.println("\n--- Tearing down test case ---");

        // Clean up test data
        if (resourceRepository != null) {
            try {
                List<Resource> resources = resourceRepository.findByConversationId(testConversationId);
                for (Resource resource : resources) {
                    // Delete associated snippets first
                    if (snippetRepository != null) {
                        List<Snippet> snippets = snippetRepository.findByResourceId(resource.getId());
                        for (Snippet snippet : snippets) {
                            snippetRepository.delete(snippet);
                        }
                    }
                    resourceRepository.delete(resource);
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not clean up test data: " + e.getMessage());
            }
        }

        System.out.println("Test teardown completed");
    }

    // ==================== Test 1: Basic End-to-End Flow ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: Basic end-to-end memory extraction flow")
    void testBasicEndToEndFlow() {
        System.out.println("\n========================================");
        System.out.println("Test 1: Basic End-to-End Flow");
        System.out.println("========================================");

        // Step 1: Prepare test messages (real user scenario)
        List<UnifiedMessage> messages = createRealUserScenarioMessages();
        System.out.println("Created " + messages.size() + " test messages");

        // Step 2: Execute memory extraction
        long startTime = System.currentTimeMillis();
        List<Resource> resources = memoryService.extractFromMessages(
                messages,
                testConversationId,
                testSessionId
        );
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Memory extraction completed in " + duration + "ms");
        System.out.println("Extracted " + resources.size() + " resources");

        // Step 3: Verify results
        assertNotNull(resources, "Resources should not be null");
        assertFalse(resources.isEmpty(), "At least one resource should be extracted");
        assertTrue(duration < 2000, "End-to-end extraction should complete within 2s, took: " + duration + "ms");

        // Step 4: Verify data persistence
        List<Resource> savedResources = resourceRepository.findByConversationId(testConversationId);
        assertEquals(resources.size(), savedResources.size(),
                "All extracted resources should be persisted to database");

        // Step 5: Verify resource data integrity
        for (Resource resource : savedResources) {
            assertNotNull(resource.getId(), "Resource ID should not be null");
            assertEquals(testConversationId, resource.getConversationId(),
                    "Resource should have correct conversation ID");
            assertEquals(testSessionId, resource.getSessionId(),
                    "Resource should have correct session ID");
            assertNotNull(resource.getContent(), "Resource content should not be null");
            assertNotNull(resource.getAbstract(), "Resource abstract should not be null");
            assertTrue(resource.getTimestamp() > 0, "Resource timestamp should be positive");
        }

        System.out.println("✓ Basic end-to-end flow test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 2: Complete Chain Validation ====================

    @Test
    @Order(2)
    @DisplayName("Test 2: Complete chain from message to storage")
    void testCompleteChainValidation() {
        System.out.println("\n========================================");
        System.out.println("Test 2: Complete Chain Validation");
        System.out.println("========================================");

        // Step 1: Create multi-turn conversation
        List<UnifiedMessage> messages = createMultiTurnConversation();
        System.out.println("Created multi-turn conversation with " + messages.size() + " messages");

        // Step 2: Process through complete chain
        long chainStartTime = System.currentTimeMillis();

        // 2a. Extract memories
        List<Resource> resources = memoryService.extractFromMessages(
                messages,
                testConversationId,
                testSessionId
        );
        long extractionTime = System.currentTimeMillis() - chainStartTime;
        System.out.println("Memory extraction completed in " + extractionTime + "ms");

        // 2b. Verify resources are stored
        List<Resource> storedResources = resourceRepository.findByConversationId(testConversationId);
        long storageTime = System.currentTimeMillis() - chainStartTime - extractionTime;
        System.out.println("Storage verification completed in " + storageTime + "ms");

        long totalChainTime = System.currentTimeMillis() - chainStartTime;
        System.out.println("Total chain time: " + totalChainTime + "ms");

        // Step 3: Validate chain integrity
        assertNotNull(resources, "Extraction should produce resources");
        assertEquals(resources.size(), storedResources.size(),
                "All extracted resources should be stored");

        // Step 4: Verify snippets are created and linked
        int totalSnippets = 0;
        for (Resource resource : storedResources) {
            List<Snippet> snippets = snippetRepository.findByResourceId(resource.getId());
            totalSnippets += snippets.size();

            // Verify snippet-resource relationship
            for (Snippet snippet : snippets) {
                assertEquals(resource.getId(), snippet.getResourceId(),
                        "Snippet should be linked to correct resource");
                assertNotNull(snippet.getSummary(), "Snippet summary should not be null");
                assertNotNull(snippet.getMemoryType(), "Snippet memory type should not be null");
            }
        }
        System.out.println("Found " + totalSnippets + " snippets linked to resources");

        // Step 5: Verify data queryability
        List<Resource> queriedResources = resourceRepository.findBySessionId(testSessionId);
        assertFalse(queriedResources.isEmpty(), "Resources should be queryable by session ID");

        // Step 6: Verify performance
        assertTrue(totalChainTime < 2000,
                "Complete chain should complete within 2s, took: " + totalChainTime + "ms");

        System.out.println("✓ Complete chain validation test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 3: Business Logic Correctness ====================

    @Test
    @Order(3)
    @DisplayName("Test 3: Business logic correctness verification")
    void testBusinessLogicCorrectness() {
        System.out.println("\n========================================");
        System.out.println("Test 3: Business Logic Correctness");
        System.out.println("========================================");

        // Step 1: Create conversation with specific topics
        List<UnifiedMessage> messages = createTopicBasedConversation();
        System.out.println("Created conversation with specific topics");

        // Step 2: Extract memories
        List<Resource> resources = memoryService.extractFromMessages(
                messages,
                testConversationId,
                testSessionId
        );

        // Step 3: Verify business logic
        assertNotNull(resources, "Resources should be extracted");
        assertFalse(resources.isEmpty(), "At least one resource should be created");

        // Verify each resource follows business rules
        for (Resource resource : resources) {
            // Rule 1: Content should not exceed maximum length
            assertTrue(resource.getContent().length() <= 10000,
                    "Resource content should not exceed 10000 characters");

            // Rule 2: Abstract should be a concise summary
            assertNotNull(resource.getAbstract(), "Abstract should not be null");
            assertTrue(resource.getAbstract().length() <= 500,
                    "Abstract should be concise (<= 500 characters)");

            // Rule 3: Timestamp should be set correctly
            assertTrue(resource.getTimestamp() > 0, "Timestamp should be positive");
            assertTrue(resource.getTimestamp() <= System.currentTimeMillis(),
                    "Timestamp should not be in the future");

            // Rule 4: IDs should be valid
            assertNotNull(resource.getId(), "Resource ID should not be null");
            assertFalse(resource.getId().isEmpty(), "Resource ID should not be empty");
        }

        // Step 4: Verify snippet extraction logic
        List<Snippet> allSnippets = new ArrayList<>();
        for (Resource resource : resources) {
            List<Snippet> snippets = snippetRepository.findByResourceId(resource.getId());
            allSnippets.addAll(snippets);

            // Verify snippet business rules
            for (Snippet snippet : snippets) {
                assertNotNull(snippet.getMemoryType(), "Snippet memory type should not be null");
                assertNotNull(snippet.getSummary(), "Snippet summary should not be null");
                assertTrue(snippet.getSummary().length() <= 1000,
                        "Snippet summary should not exceed 1000 characters");
            }
        }
        System.out.println("Verified " + allSnippets.size() + " snippets with business rules");

        System.out.println("✓ Business logic correctness test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 4: Performance Validation ====================

    @Test
    @Order(4)
    @DisplayName("Test 4: End-to-end performance validation")
    void testEndToEndPerformance() {
        System.out.println("\n========================================");
        System.out.println("Test 4: End-to-End Performance");
        System.out.println("========================================");

        // Step 1: Prepare large conversation
        List<UnifiedMessage> messages = createLargeConversation(50);
        System.out.println("Created large conversation with " + messages.size() + " messages");

        // Step 2: Measure extraction performance
        long startTime = System.currentTimeMillis();
        List<Resource> resources = memoryService.extractFromMessages(
                messages,
                testConversationId,
                testSessionId
        );
        long extractionTime = System.currentTimeMillis() - startTime;

        System.out.println("Extraction time: " + extractionTime + "ms");
        System.out.println("Resources extracted: " + resources.size());

        // Step 3: Verify performance requirements
        assertTrue(extractionTime < 2000,
                "End-to-end extraction should complete within 2s, took: " + extractionTime + "ms");

        // Step 4: Measure query performance
        startTime = System.currentTimeMillis();
        List<Resource> queriedResources = resourceRepository.findByConversationId(testConversationId);
        long queryTime = System.currentTimeMillis() - startTime;

        System.out.println("Query time: " + queryTime + "ms");
        assertTrue(queryTime < 100,
                "Query should complete within 100ms, took: " + queryTime + "ms");

        // Step 5: Measure throughput
        double throughput = (double) messages.size() / (extractionTime / 1000.0);
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " messages/second");
        assertTrue(throughput > 10,
                "Throughput should be > 10 messages/second, got: " + String.format("%.2f", throughput));

        System.out.println("✓ Performance validation test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 5: Data Persistence Verification ====================

    @Test
    @Order(5)
    @DisplayName("Test 5: Data persistence and consistency")
    void testDataPersistenceAndConsistency() {
        System.out.println("\n========================================");
        System.out.println("Test 5: Data Persistence and Consistency");
        System.out.println("========================================");

        // Step 1: Extract memories
        List<UnifiedMessage> messages = createRealUserScenarioMessages();
        List<Resource> resources = memoryService.extractFromMessages(
                messages,
                testConversationId,
                testSessionId
        );

        // Step 2: Verify storage - check data is correctly stored
        List<Resource> storedResources = resourceRepository.findByConversationId(testConversationId);
        assertEquals(resources.size(), storedResources.size(),
                "All resources should be stored");

        // Step 3: Verify data correctness
        for (Resource original : resources) {
            Resource stored = storedResources.stream()
                    .filter(r -> r.getId().equals(original.getId()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(stored, "Resource should be retrievable from database");
            assertEquals(original.getContent(), stored.getContent(),
                    "Content should be stored correctly");
            assertEquals(original.getAbstract(), stored.getAbstract(),
                    "Abstract should be stored correctly");
            assertEquals(original.getConversationId(), stored.getConversationId(),
                    "Conversation ID should match");
        }

        // Step 4: Verify snippet persistence
        int totalExpectedSnippets = 0;
        for (Resource resource : storedResources) {
            List<Snippet> snippets = snippetRepository.findByResourceId(resource.getId());
            totalExpectedSnippets += snippets.size();

            for (Snippet snippet : snippets) {
                // Verify snippet data is correctly stored
                assertNotNull(snippet.getId(), "Snippet ID should not be null");
                assertNotNull(snippet.getResourceId(), "Snippet resource ID should not be null");
                assertNotNull(snippet.getSummary(), "Snippet summary should not be null");
                assertNotNull(snippet.getMemoryType(), "Snippet memory type should not be null");

                // Verify data can be queried back
                Snippet retrievedSnippet = snippetRepository.findById(snippet.getId()).orElse(null);
                assertNotNull(retrievedSnippet, "Snippet should be queryable from database");
                assertEquals(snippet.getSummary(), retrievedSnippet.getSummary(),
                        "Snippet summary should match");
            }
        }
        System.out.println("Verified " + totalExpectedSnippets + " snippets are persisted");

        // Step 5: Verify data consistency
        // Count resources by conversation
        List<Resource> byConversation = resourceRepository.findByConversationId(testConversationId);
        // Count resources by session
        List<Resource> bySession = resourceRepository.findBySessionId(testSessionId);

        assertEquals(byConversation.size(), bySession.size(),
                "Resource counts should be consistent across different query methods");

        System.out.println("✓ Data persistence and consistency test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 6: Concurrent Scenario ====================

    @Test
    @Order(6)
    @DisplayName("Test 6: Concurrent memory extraction scenarios")
    void testConcurrentExtraction() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("Test 6: Concurrent Extraction Scenarios");
        System.out.println("========================================");

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Step 1: Submit concurrent extraction requests
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    String convId = "test-conv-concurrent-" + threadId + "-" + System.currentTimeMillis();
                    String sessionId = "test-session-concurrent-" + threadId;

                    List<UnifiedMessage> messages = createRealUserScenarioMessages();
                    List<Resource> resources = memoryService.extractFromMessages(
                            messages,
                            convId,
                            sessionId
                    );

                    if (resources != null && !resources.isEmpty()) {
                        // Verify data is stored correctly
                        List<Resource> stored = resourceRepository.findByConversationId(convId);
                        if (!stored.isEmpty()) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Error in concurrent extraction: " + e.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Step 2: Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS),
                "All concurrent operations should complete within 30 seconds");
        long duration = System.currentTimeMillis() - startTime;

        executor.shutdown();

        // Step 3: Verify results
        System.out.println("Concurrent extractions completed in " + duration + "ms");
        System.out.println("Successful: " + successCount.get());
        System.out.println("Failed: " + failureCount.get());

        assertEquals(threadCount, successCount.get(),
                "All concurrent extractions should succeed");
        assertEquals(0, failureCount.get(),
                "No concurrent extractions should fail");

        System.out.println("✓ Concurrent extraction test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 7: Trigger Service Integration ====================

    @Test
    @Order(7)
    @DisplayName("Test 7: Trigger service integration")
    void testTriggerServiceIntegration() {
        System.out.println("\n========================================");
        System.out.println("Test 7: Trigger Service Integration");
        System.out.println("========================================");

        // Step 1: Verify trigger service is available
        assertNotNull(triggerService, "TriggerService should be initialized");

        // Step 2: Verify trigger service availability
        boolean isAvailable = triggerService.isAvailable();
        System.out.println("Trigger service available: " + isAvailable);

        assertTrue(isAvailable, "Trigger service should be available");

        System.out.println("✓ Trigger service integration test PASSED");
        System.out.println("========================================");
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a realistic user scenario conversation.
     */
    private List<UnifiedMessage> createRealUserScenarioMessages() {
        List<UnifiedMessage> messages = new ArrayList<>();

        // User asks about code optimization
        messages.add(createMessage("user",
                "Can you help me optimize this Java code? It's running too slowly."));

        // Assistant responds with suggestions
        messages.add(createMessage("assistant",
                "I'd be happy to help! Could you share the code snippet that's causing performance issues?"));

        // User provides code
        messages.add(createMessage("user",
                "Here's the code: [code snippet]. The loop is taking too long to process large datasets."));

        // Assistant provides optimization
        messages.add(createMessage("assistant",
                "I see the issue. You can optimize this by using parallel streams and proper data structures. " +
                        "Let me show you how..."));

        // User confirms understanding
        messages.add(createMessage("user",
                "That makes sense! I'll implement those changes. Thanks for the help!"));

        return messages;
    }

    /**
     * Creates a multi-turn conversation with various topics.
     */
    private List<UnifiedMessage> createMultiTurnConversation() {
        List<UnifiedMessage> messages = new ArrayList<>();

        // Turn 1: Greeting and initial question
        messages.add(createMessage("user", "Hi! I'm working on a Spring Boot project."));
        messages.add(createMessage("assistant", "Hello! I'd be happy to help with your Spring Boot project."));

        // Turn 2: Technical discussion
        messages.add(createMessage("user", "I need to implement a memory management feature."));
        messages.add(createMessage("assistant", "That's interesting! What specific aspects of memory management are you working on?"));

        // Turn 3: Detailed requirements
        messages.add(createMessage("user", "I need to store conversation history and extract key information."));
        messages.add(createMessage("assistant", "You'll want to look into vector databases and embedding models for that."));

        // Turn 4: Implementation guidance
        messages.add(createMessage("user", "Can you recommend any specific technologies?"));
        messages.add(createMessage("assistant", "For Java, you might consider using SQLite with sqlite-vec or PostgreSQL with pgvector."));

        return messages;
    }

    /**
     * Creates a conversation focused on specific topics.
     */
    private List<UnifiedMessage> createTopicBasedConversation() {
        List<UnifiedMessage> messages = new ArrayList<>();

        messages.add(createMessage("user", "I'm learning about machine learning."));
        messages.add(createMessage("assistant", "That's a great field! What aspect interests you most?"));

        messages.add(createMessage("user", "I'm particularly interested in neural networks and deep learning."));
        messages.add(createMessage("assistant", "Neural networks are fascinating! Are you planning to work with natural language processing?"));

        messages.add(createMessage("user", "Yes, specifically in building conversational AI systems."));
        messages.add(createMessage("assistant", "Excellent choice! You'll need to understand embeddings and transformers for that."));

        return messages;
    }

    /**
     * Creates a large conversation for performance testing.
     */
    private List<UnifiedMessage> createLargeConversation(int messageCount) {
        List<UnifiedMessage> messages = new ArrayList<>();

        for (int i = 0; i < messageCount; i++) {
            if (i % 2 == 0) {
                messages.add(createMessage("user",
                        "This is message number " + i + " from the user. " +
                                "I'm discussing a technical topic about software development and best practices."));
            } else {
                messages.add(createMessage("assistant",
                        "Thank you for message " + i + ". " +
                        "I'm providing helpful assistance and guidance on the topic you've raised."));
            }
        }

        return messages;
    }

    /**
     * Creates a mock UnifiedMessage for testing.
     */
    private UnifiedMessage createMessage(String role, String content) {
        Role messageRole = "user".equals(role) ? Role.USER : Role.ASSISTANT;
        return new UnifiedMessage(messageRole, content, PayloadFormat.TEXT);
    }
}
