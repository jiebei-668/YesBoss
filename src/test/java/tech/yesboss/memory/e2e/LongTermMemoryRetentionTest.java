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
import tech.yesboss.memory.repository.PreferenceRepositoryImpl;
import tech.yesboss.memory.repository.ResourceRepositoryImpl;
import tech.yesboss.memory.repository.SnippetRepositoryImpl;
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.service.MemoryServiceImpl;
import tech.yesboss.memory.manager.MemoryManagerImpl;
import tech.yesboss.memory.processor.ContentProcessor;
import tech.yesboss.memory.processor.ZhipuContentProcessorImpl;
import tech.yesboss.memory.embedding.EmbeddingService;
import tech.yesboss.memory.embedding.EmbeddingServiceFactory;
import tech.yesboss.persistence.db.DatabaseInitializer;
import tech.yesboss.persistence.datasource.SimpleDataSource;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Long-Term Memory Retention Test
 *
 * <p>This test verifies the long-term retention and update capabilities of the memory system,
 * including:</p>
 *
 * <ul>
 *   <li><b>Memory Persistence Over Time:</b> Verifies that memories persist correctly over
 *       extended periods</li>
 *   <li><b>Memory Updates:</b> Validates that memories can be updated correctly without
 *       data loss</li>
 *   <li><b>Data Consistency:</b> Ensures data consistency across multiple operations and
 *       time periods</li>
 *   <li><b>Query Performance:</b> Verifies that query performance remains acceptable even
 *       with large amounts of stored data</li>
 *   <li><b>Memory Recall:</b> Tests that old memories can be retrieved and are accurate</li>
 * </ul>
 *
 * <p><b>Test Frameworks:</b> JUnit 5</p>
 * <p><b>Reference:</b> docs_memory/记忆持久化模块v3.0.md</p>
 */
@DisplayName("Long-Term Memory Retention Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LongTermMemoryRetentionTest {

    private static YesBossConfig testConfig;
    private static SimpleDataSource dataSource;

    private MemoryService memoryService;
    private ResourceRepositoryImpl resourceRepository;
    private SnippetRepositoryImpl snippetRepository;
    private PreferenceRepositoryImpl preferenceRepository;

    private String testConversationId;
    private String testSessionId;

    // ==================== Setup and Teardown ====================

    @BeforeAll
    static void setUpOnce() throws Exception {
        System.out.println("========================================");
        System.out.println("Setting up Long-Term Memory Retention Test");
        System.out.println("========================================");

        try {
            // Load test configuration
            testConfig = ConfigurationManager.getInstance().getConfig();
            // Use in-memory database for testing
            testConfig.getDatabase().getSqlite().setPath(":memory:");

        // Initialize SimpleDataSource
        dataSource = new SimpleDataSource("jdbc:sqlite::memory:");

            // Initialize database
            databaseInitializer = new DatabaseInitializer(testConfig);

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
        System.out.println("Tearing down Long-Term Memory Retention Test");
        System.out.println("========================================");

        if (dataSource != null) {
            dataSource.close();
        }

        System.out.println("Teardown completed");
        System.out.println("========================================");
    }

    @BeforeEach
    void setUp() {
        System.out.println("\n--- Setting up test case ---");

        try {
            // Initialize repositories
            resourceRepository = new ResourceRepositoryImpl(dataSource);
            snippetRepository = new SnippetRepositoryImpl(dataSource);
            preferenceRepository = new PreferenceRepositoryImpl(dataSource);

            // Initialize services
            MemoryManagerImpl memoryManager = new MemoryManagerImpl(
                    testConfig,
                    resourceRepository,
                    snippetRepository,
                    preferenceRepository
            );

            EmbeddingService embeddingService = EmbeddingServiceFactory.getInstance(testConfig);
            ContentProcessor contentProcessor = new ZhipuContentProcessorImpl(testConfig);

            memoryService = new MemoryServiceImpl(
                    contentProcessor,
                    memoryManager,
                    resourceRepository,
                    snippetRepository,
                    preferenceRepository,
                    embeddingService
            );

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

    // ==================== Test 1: Basic Memory Persistence ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: Memory persists over time")
    void testMemoryPersistsOverTime() {
        System.out.println("\n========================================");
        System.out.println("Test 1: Memory Persists Over Time");
        System.out.println("========================================");

        // Step 1: Create and store memories
        List<UnifiedMessage> messages = createInitialConversation();
        List<Resource> initialResources = memoryService.extractFromMessages(
                messages,
                testConversationId,
                testSessionId
        );

        assertNotNull(initialResources, "Initial resources should not be null");
        assertFalse(initialResources.isEmpty(), "Should create initial resources");

        System.out.println("Created " + initialResources.size() + " initial resources");

        // Step 2: Simulate time passing (in a real system, this would be actual time)
        // For testing, we'll just verify that data persists across multiple operations

        // Step 3: Retrieve memories after "time has passed"
        List<Resource> retrievedResources = resourceRepository.findByConversationId(testConversationId);

        assertEquals(initialResources.size(), retrievedResources.size(),
                "Same number of resources should be retrieved");

        System.out.println("Retrieved " + retrievedResources.size() + " resources");

        // Step 4: Verify data integrity
        for (int i = 0; i < initialResources.size(); i++) {
            Resource initial = initialResources.get(i);
            Resource retrieved = retrievedResources.stream()
                    .filter(r -> r.getId().equals(initial.getId()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(retrieved, "Resource should be retrievable");
            assertEquals(initial.getContent(), retrieved.getContent(),
                    "Content should be preserved");
            assertEquals(initial.getAbstract(), retrieved.getAbstract(),
                    "Abstract should be preserved");
            assertEquals(initial.getConversationId(), retrieved.getConversationId(),
                    "Conversation ID should be preserved");
        }

        System.out.println("✓ Memory persistence test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 2: Memory Update Verification ====================

    @Test
    @Order(2)
    @DisplayName("Test 2: Memory can be updated correctly")
    void testMemoryCanBeUpdated() {
        System.out.println("\n========================================");
        System.out.println("Test 2: Memory Can Be Updated");
        System.out.println("========================================");

        // Step 1: Create initial memory
        List<UnifiedMessage> initialMessages = createInitialConversation();
        List<Resource> initialResources = memoryService.extractFromMessages(
                initialMessages,
                testConversationId,
                testSessionId
        );

        assertFalse(initialResources.isEmpty(), "Should create initial resources");

        Resource originalResource = initialResources.get(0);
        String originalContent = originalResource.getContent();
        String originalAbstract = originalResource.getAbstract();

        System.out.println("Original resource created");
        System.out.println("Content length: " + originalContent.length());
        System.out.println("Abstract: " + originalAbstract);

        // Step 2: Add more messages to the conversation (simulating update)
        List<UnifiedMessage> additionalMessages = createAdditionalConversation();
        List<Resource> updatedResources = memoryService.extractFromMessages(
                additionalMessages,
                testConversationId,
                testSessionId + "-update"
        );

        System.out.println("Added " + updatedResources.size() + " additional resources");

        // Step 3: Verify original memory is still intact
        List<Resource> allResources = resourceRepository.findByConversationId(testConversationId);

        assertTrue(allResources.size() >= initialResources.size(),
                "Should have at least the original resources");

        // Verify the original resource still exists and is unchanged
        Resource retrievedOriginal = allResources.stream()
                .filter(r -> r.getId().equals(originalResource.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(retrievedOriginal, "Original resource should still exist");
        assertEquals(originalContent, retrievedOriginal.getContent(),
                "Original content should be unchanged");
        assertEquals(originalAbstract, retrievedOriginal.getAbstract(),
                "Original abstract should be unchanged");

        System.out.println("✓ Memory update test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 3: Data Consistency Over Time ====================

    @Test
    @Order(3)
    @DisplayName("Test 3: Data consistency over multiple operations")
    void testDataConsistencyOverTime() {
        System.out.println("\n========================================");
        System.out.println("Test 3: Data Consistency Over Time");
        System.out.println("========================================");

        List<String> resourceIds = new ArrayList<>();

        // Step 1: Create multiple batches of memories over "time"
        for (int batch = 0; batch < 3; batch++) {
            String batchSessionId = testSessionId + "-batch-" + batch;
            List<UnifiedMessage> batchMessages = createBatchConversation(batch);

            List<Resource> batchResources = memoryService.extractFromMessages(
                    batchMessages,
                    testConversationId,
                    batchSessionId
            );

            System.out.println("Batch " + batch + ": Created " + batchResources.size() + " resources");

            // Store resource IDs for verification
            for (Resource resource : batchResources) {
                resourceIds.add(resource.getId());
            }
        }

        System.out.println("Total resources created: " + resourceIds.size());

        // Step 2: Verify all resources are still accessible
        List<Resource> allResources = resourceRepository.findByConversationId(testConversationId);

        assertEquals(resourceIds.size(), allResources.size(),
                "All resources should be accessible");

        // Step 3: Verify each resource's data integrity
        int verifiedCount = 0;
        for (String resourceId : resourceIds) {
            Resource resource = allResources.stream()
                    .filter(r -> r.getId().equals(resourceId))
                    .findFirst()
                    .orElse(null);

            assertNotNull(resource, "Resource " + resourceId + " should be accessible");
            assertNotNull(resource.getContent(), "Resource content should not be null");
            assertNotNull(resource.getAbstract(), "Resource abstract should not be null");
            assertNotNull(resource.getConversationId(), "Conversation ID should not be null");

            verifiedCount++;
        }

        System.out.println("Verified " + verifiedCount + " resources for data integrity");

        // Step 4: Verify snippets are still linked correctly
        int snippetCount = 0;
        for (Resource resource : allResources) {
            List<Snippet> snippets = snippetRepository.findByResourceId(resource.getId());
            snippetCount += snippets.size();

            for (Snippet snippet : snippets) {
                assertEquals(resource.getId(), snippet.getResourceId(),
                        "Snippet should be linked to correct resource");
            }
        }

        System.out.println("Verified " + snippetCount + " snippet-resource links");

        System.out.println("✓ Data consistency test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 4: Long-Term Query Performance ====================

    @Test
    @Order(4)
    @DisplayName("Test 4: Query performance with large dataset")
    void testQueryPerformanceWithLargeDataset() {
        System.out.println("\n========================================");
        System.out.println("Test 4: Long-Term Query Performance");
        System.out.println("========================================");

        // Step 1: Create a large dataset (simulating long-term storage)
        int batchSize = 10;
        int totalBatches = 5;
        int totalResources = 0;

        long creationStartTime = System.currentTimeMillis();

        for (int batch = 0; batch < totalBatches; batch++) {
            String batchConvId = testConversationId + "-batch-" + batch;
            String batchSessionId = testSessionId + "-batch-" + batch;

            for (int i = 0; i < batchSize; i++) {
                List<UnifiedMessage> batchMessages = createSingleConversation();
                List<Resource> resources = memoryService.extractFromMessages(
                        batchMessages,
                        batchConvId,
                        batchSessionId + "-" + i
                );
                totalResources += resources.size();
            }
        }

        long creationTime = System.currentTimeMillis() - creationStartTime;
        System.out.println("Created " + totalResources + " resources in " + creationTime + "ms");

        // Step 2: Test query performance
        long queryStartTime = System.currentTimeMillis();
        List<Resource> queriedResources = resourceRepository.findByConversationId(
                testConversationId + "-batch-0"
        );
        long queryTime = System.currentTimeMillis() - queryStartTime;

        System.out.println("Query time: " + queryTime + "ms");
        System.out.println("Resources found: " + queriedResources.size());

        // Query should be fast even with large dataset
        assertTrue(queryTime < 200,
                "Query should complete within 200ms, took: " + queryTime + "ms");

        // Step 3: Test snippet query performance
        long snippetQueryStartTime = System.currentTimeMillis();
        int totalSnippets = 0;
        for (Resource resource : queriedResources) {
            List<Snippet> snippets = snippetRepository.findByResourceId(resource.getId());
            totalSnippets += snippets.size();
        }
        long snippetQueryTime = System.currentTimeMillis() - snippetQueryStartTime;

        System.out.println("Snippet query time: " + snippetQueryTime + "ms");
        System.out.println("Total snippets: " + totalSnippets);

        assertTrue(snippetQueryTime < 500,
                "Snippet queries should complete within 500ms, took: " + snippetQueryTime + "ms");

        System.out.println("✓ Query performance test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 5: Memory Recall Accuracy ====================

    @Test
    @Order(5)
    @DisplayName("Test 5: Old memories are recalled accurately")
    void testOldMemoriesRecalledAccurately() {
        System.out.println("\n========================================");
        System.out.println("Test 5: Old Memory Recall Accuracy");
        System.out.println("========================================");

        // Step 1: Create memories with specific content
        List<UnifiedMessage> messages = createSpecificContentConversation();
        List<Resource> originalResources = memoryService.extractFromMessages(
                messages,
                testConversationId,
                testSessionId
        );

        assertFalse(originalResources.isEmpty(), "Should create resources");

        System.out.println("Created " + originalResources.size() + " resources with specific content");

        // Store original data for comparison
        List<String> originalContents = new ArrayList<>();
        List<String> originalAbstracts = new ArrayList<>();
        for (Resource resource : originalResources) {
            originalContents.add(resource.getContent());
            originalAbstracts.add(resource.getAbstract());
        }

        // Step 2: Simulate time passing and other operations
        // Create some unrelated resources
        String unrelatedConvId = testConversationId + "-unrelated";
        for (int i = 0; i < 5; i++) {
            List<UnifiedMessage> unrelatedMessages = createSingleConversation();
            memoryService.extractFromMessages(
                    unrelatedMessages,
                    unrelatedConvId,
                    testSessionId + "-unrelated-" + i
            );
        }

        System.out.println("Created unrelated resources");

        // Step 3: Recall original memories and verify accuracy
        List<Resource> recalledResources = resourceRepository.findByConversationId(testConversationId);

        assertEquals(originalResources.size(), recalledResources.size(),
                "Should recall all original resources");

        System.out.println("Recalled " + recalledResources.size() + " resources");

        // Verify content accuracy
        for (int i = 0; i < originalResources.size(); i++) {
            Resource original = originalResources.get(i);
            Resource recalled = recalledResources.stream()
                    .filter(r -> r.getId().equals(original.getId()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(recalled, "Original resource should be recallable");
            assertEquals(originalContents.get(i), recalled.getContent(),
                    "Content should be accurately recalled");
            assertEquals(originalAbstracts.get(i), recalled.getAbstract(),
                    "Abstract should be accurately recalled");
        }

        System.out.println("✓ Memory recall accuracy test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 6: Concurrent Access to Long-Term Memory ====================

    @Test
    @Order(6)
    @DisplayName("Test 6: Concurrent access to long-term memory")
    void testConcurrentAccessToLongTermMemory() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("Test 6: Concurrent Access to Long-Term Memory");
        System.out.println("========================================");

        // Step 1: Create initial long-term memory
        List<UnifiedMessage> initialMessages = createInitialConversation();
        memoryService.extractFromMessages(
                initialMessages,
                testConversationId,
                testSessionId
        );

        System.out.println("Created initial long-term memory");

        // Step 2: Simulate concurrent access and updates
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger readSuccessCount = new AtomicInteger(0);
        AtomicInteger writeSuccessCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Read operation: Access existing memory
                    List<Resource> resources = resourceRepository.findByConversationId(testConversationId);
                    if (!resources.isEmpty()) {
                        readSuccessCount.incrementAndGet();
                    }

                    // Write operation: Add new memory
                    String newConvId = testConversationId + "-thread-" + threadId;
                    String newSessionId = testSessionId + "-thread-" + threadId;
                    List<UnifiedMessage> newMessages = createSingleConversation();
                    List<Resource> newResources = memoryService.extractFromMessages(
                            newMessages,
                            newConvId,
                            newSessionId
                    );

                    if (!newResources.isEmpty()) {
                        writeSuccessCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Error in concurrent access: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Step 3: Wait for completion and verify
        assertTrue(latch.await(30, TimeUnit.SECONDS),
                "All operations should complete within 30 seconds");

        executor.shutdown();

        System.out.println("Read operations successful: " + readSuccessCount.get());
        System.out.println("Write operations successful: " + writeSuccessCount.get());

        assertEquals(threadCount, readSuccessCount.get(),
                "All read operations should succeed");
        assertEquals(threadCount, writeSuccessCount.get(),
                "All write operations should succeed");

        // Step 4: Verify data integrity after concurrent operations
        List<Resource> originalResources = resourceRepository.findByConversationId(testConversationId);
        assertFalse(originalResources.isEmpty(),
                "Original data should remain intact");

        System.out.println("✓ Concurrent access test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 7: Memory Aging and Archival ====================

    @Test
    @Order(7)
    @DisplayName("Test 7: Memory aging and archival")
    void testMemoryAgingAndArchival() {
        System.out.println("\n========================================");
        System.out.println("Test 7: Memory Aging and Archival");
        System.out.println("========================================");

        // Step 1: Create memories at different "times"
        List<String> oldResourceIds = new ArrayList<>();
        List<String> newResourceIds = new ArrayList<>();

        // Old memories
        for (int i = 0; i < 3; i++) {
            String oldConvId = testConversationId + "-old-" + i;
            String oldSessionId = testSessionId + "-old-" + i;
            List<UnifiedMessage> oldMessages = createSingleConversation();
            List<Resource> oldResources = memoryService.extractFromMessages(
                    oldMessages,
                    oldConvId,
                    oldSessionId
            );

            for (Resource resource : oldResources) {
                oldResourceIds.add(resource.getId());
            }
        }

        System.out.println("Created " + oldResourceIds.size() + " old memories");

        // New memories
        for (int i = 0; i < 3; i++) {
            String newConvId = testConversationId + "-new-" + i;
            String newSessionId = testSessionId + "-new-" + i;
            List<UnifiedMessage> newMessages = createSingleConversation();
            List<Resource> newResources = memoryService.extractFromMessages(
                    newMessages,
                    newConvId,
                    newSessionId
            );

            for (Resource resource : newResources) {
                newResourceIds.add(resource.getId());
            }
        }

        System.out.println("Created " + newResourceIds.size() + " new memories");

        // Step 2: Verify all memories are accessible regardless of "age"
        int oldAccessibleCount = 0;
        for (String resourceId : oldResourceIds) {
            Resource resource = resourceRepository.findById(resourceId).orElse(null);
            if (resource != null) {
                oldAccessibleCount++;
            }
        }

        int newAccessibleCount = 0;
        for (String resourceId : newResourceIds) {
            Resource resource = resourceRepository.findById(resourceId).orElse(null);
            if (resource != null) {
                newAccessibleCount++;
            }
        }

        System.out.println("Old memories accessible: " + oldAccessibleCount + "/" + oldResourceIds.size());
        System.out.println("New memories accessible: " + newAccessibleCount + "/" + newResourceIds.size());

        assertEquals(oldResourceIds.size(), oldAccessibleCount,
                "All old memories should be accessible");
        assertEquals(newResourceIds.size(), newAccessibleCount,
                "All new memories should be accessible");

        // Step 3: Verify query performance is consistent
        long oldQueryStart = System.currentTimeMillis();
        for (String resourceId : oldResourceIds) {
            resourceRepository.findById(resourceId);
        }
        long oldQueryTime = System.currentTimeMillis() - oldQueryStart;

        long newQueryStart = System.currentTimeMillis();
        for (String resourceId : newResourceIds) {
            resourceRepository.findById(resourceId);
        }
        long newQueryTime = System.currentTimeMillis() - newQueryStart;

        System.out.println("Old memories query time: " + oldQueryTime + "ms");
        System.out.println("New memories query time: " + newQueryTime + "ms");

        // Query times should be similar
        assertTrue(Math.abs(oldQueryTime - newQueryTime) < oldQueryTime * 0.5,
                "Query times should be consistent regardless of memory age");

        System.out.println("✓ Memory aging and archival test PASSED");
        System.out.println("========================================");
    }

    // ==================== Helper Methods ====================

    private List<UnifiedMessage> createInitialConversation() {
        List<UnifiedMessage> messages = new ArrayList<>();

        messages.add(createMessage("user",
                "I'm working on a machine learning project and need help with data preprocessing."));
        messages.add(createMessage("assistant",
                "I can help with that! What kind of data are you working with and what preprocessing steps have you tried so far?"));
        messages.add(createMessage("user",
                "I'm working with customer reviews. I've tried basic cleaning but need help with more advanced techniques."));
        messages.add(createMessage("assistant",
                "Great! For text data like customer reviews, you'll want to consider tokenization, stop word removal, and possibly lemmatization."));

        return messages;
    }

    private List<UnifiedMessage> createAdditionalConversation() {
        List<UnifiedMessage> messages = new ArrayList<>();

        messages.add(createMessage("user",
                "Following up on the preprocessing - can you explain more about tokenization?"));
        messages.add(createMessage("assistant",
                "Tokenization breaks text into individual words or tokens. It's a fundamental step in NLP pipelines."));
        messages.add(createMessage("user",
                "That makes sense. What tools do you recommend for this?"));
        messages.add(createMessage("assistant",
                "Popular choices include NLTK, spaCy, and Hugging Face transformers, depending on your needs."));

        return messages;
    }

    private List<UnifiedMessage> createBatchConversation(int batch) {
        List<UnifiedMessage> messages = new ArrayList<>();

        messages.add(createMessage("user",
                "Batch " + batch + ": Discussing technical topic number " + batch + " in detail."));
        messages.add(createMessage("assistant",
                "I understand you're interested in topic " + batch + ". Let me provide detailed information on that."));

        return messages;
    }

    private List<UnifiedMessage> createSingleConversation() {
        List<UnifiedMessage> messages = new ArrayList<>();

        messages.add(createMessage("user",
                "Quick question about software architecture patterns."));
        messages.add(createMessage("assistant",
                "Of course! Which architectural pattern are you interested in learning about?"));

        return messages;
    }

    private List<UnifiedMessage> createSpecificContentConversation() {
        List<UnifiedMessage> messages = new ArrayList<>();

        messages.add(createMessage("user",
                "I need to remember that my favorite programming language is Python and I prefer using Flask for web development."));
        messages.add(createMessage("assistant",
                "I've noted that you prefer Python and Flask for web development. Is there a specific reason for these preferences?"));
        messages.add(createMessage("user",
                "Yes, Python's simplicity and Flask's minimal design align well with my development style."));

        return messages;
    }

    private UnifiedMessage createMessage(String role, String content) {
        Role messageRole = "user".equals(role) ? Role.USER : Role.ASSISTANT;
        return new UnifiedMessage(messageRole, content, PayloadFormat.TEXT);
    }
}
