package tech.yesboss.memory.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Snippet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for Preference Update Mechanism
 */
@DisplayName("Preference Update Mechanism Tests")
public class PreferenceUpdateTest {

    @Mock
    private LlmClient mockLlmClient;

    private ZhipuContentProcessorImpl processor;
    private MemoryProcessorConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new ZhipuContentProcessorImpl();
        processor.setLlmClient(mockLlmClient);
        config = MemoryProcessorConfig.getInstance();
    }

    @Nested
    @DisplayName("1. Test Data Preparation")
    class TestDataPreparation {

        @Test
        @DisplayName("Create normal preference with summary")
        void testCreateNormalPreference() {
            Preference pref = new Preference();
            pref.setId("pref1");
            pref.setName("Programming");
            pref.setSummary("User likes programming in Java and Python");

            assertNotNull(pref);
            assertEquals("pref1", pref.getId());
            assertEquals("Programming", pref.getName());
            assertNotNull(pref.getSummary());
        }

        @Test
        @DisplayName("Create preference list with multiple preferences")
        void testCreatePreferenceList() {
            List<Preference> preferences = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Preference pref = new Preference();
                pref.setId("pref" + i);
                pref.setName("Topic " + i);
                pref.setSummary("Summary " + i);
                preferences.add(pref);
            }

            assertEquals(5, preferences.size());
        }

        @Test
        @DisplayName("Create snippet for preference update")
        void testCreateSnippetForUpdate() {
            Snippet snippet = new Snippet();
            snippet.setId("snippet1");
            snippet.setSummary("User enjoys learning new programming languages");
            snippet.setMemoryType(Snippet.MemoryType.KNOWLEDGE);

            assertNotNull(snippet);
            assertEquals("snippet1", snippet.getId());
            assertNotNull(snippet.getSummary());
        }

        @Test
        @DisplayName("Create empty preference summary")
        void testCreateEmptySummary() {
            String summary = "";
            assertNotNull(summary);
            assertTrue(summary.isEmpty());
        }

        @Test
        @DisplayName("Create very long preference summary")
        void testCreateLongSummary() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("This is a very long preference summary with lots of details. ");
            }
            String summary = sb.toString();

            assertNotNull(summary);
            assertTrue(summary.length() > 10000);
        }
    }

    @Nested
    @DisplayName("2. Interface Contract Validation")
    class InterfaceContractValidation {

        @Test
        @DisplayName("updatePreferenceSummary returns String")
        void testUpdatePreferenceSummaryReturnType() {
            String existingSummary = "User likes programming";
            List<Snippet> newSnippets = new ArrayList<>();
            newSnippets.add(new Snippet());

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Updated summary");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            String result = processor.updatePreferenceSummary(existingSummary, newSnippets);

            assertNotNull(result, "Result should not be null");
            assertInstanceOf(String.class, result, "Result should be a String");
        }

        @Test
        @DisplayName("identifyPreferencesForSnippet returns List<String>")
        void testIdentifyPreferencesForSnippetReturnType() {
            String snippetSummary = "User prefers dark mode";
            List<Preference> preferences = List.of(new Preference());

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"pref1\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> result = processor.identifyPreferencesForSnippet(snippetSummary, preferences);

            assertNotNull(result, "Result should not be null");
            assertInstanceOf(List.class, result, "Result should be a List");
        }

        @Test
        @DisplayName("updatePreferenceSummary accepts String and List<Snippet> parameters")
        void testUpdatePreferenceSummaryParameterTypes() {
            String existingSummary = "Existing summary";
            List<Snippet> newSnippets = new ArrayList<>();
            newSnippets.add(new Snippet());

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Updated");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            assertDoesNotThrow(() ->
                processor.updatePreferenceSummary(existingSummary, newSnippets));
        }
    }

    @Nested
    @DisplayName("3. Normal Functionality Scenarios")
    class NormalFunctionalityScenarios {

        @Test
        @DisplayName("Update preference summary with new snippets")
        void testUpdatePreferenceSummaryNormal() {
            String existingSummary = "User likes programming in Java";
            Snippet newSnippet = new Snippet();
            newSnippet.setSummary("User also likes Python");
            List<Snippet> newSnippets = List.of(newSnippet);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("User likes programming in Java and Python");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            String updated = processor.updatePreferenceSummary(existingSummary, newSnippets);

            assertNotNull(updated);
            assertTrue(updated.contains("Java"));
            assertTrue(updated.contains("Python"));
        }

        @Test
        @DisplayName("Identify preferences for snippet - single match")
        void testIdentifyPreferencesSingleMatch() {
            String snippetSummary = "User prefers dark mode in applications";
            List<Preference> preferences = new ArrayList<>();
            Preference pref1 = new Preference();
            pref1.setId("ui-pref");
            pref1.setName("UI Preferences");
            pref1.setSummary("User likes dark mode and clean interfaces");
            preferences.add(pref1);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"ui-pref\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> matchedIds = processor.identifyPreferencesForSnippet(snippetSummary, preferences);

            assertNotNull(matchedIds);
            assertEquals(1, matchedIds.size());
            assertEquals("ui-pref", matchedIds.get(0));
        }

        @Test
        @DisplayName("Handle empty snippet list in update")
        void testUpdatePreferenceSummaryEmptySnippets() {
            String existingSummary = "User likes programming";
            List<Snippet> emptySnippets = List.of();

            // Implementation validates that snippet list is not empty
            assertThrows(ContentProcessingException.class, () ->
                processor.updatePreferenceSummary(existingSummary, emptySnippets));
        }
    }

    @Nested
    @DisplayName("4. Boundary Conditions")
    class BoundaryConditions {

        @ParameterizedTest
        @NullSource
        @DisplayName("Handle null existing summary in update")
        void testUpdateNullSummary(String nullSummary) {
            List<Snippet> newSnippets = new ArrayList<>();
            newSnippets.add(new Snippet());

            assertThrows(ContentProcessingException.class, () ->
                processor.updatePreferenceSummary(nullSummary, newSnippets));
        }

        @ParameterizedTest
        @EmptySource
        @DisplayName("Handle empty existing summary in update")
        void testUpdateEmptySummary(String emptySummary) {
            List<Snippet> newSnippets = new ArrayList<>();
            newSnippets.add(new Snippet());

            assertThrows(ContentProcessingException.class, () ->
                processor.updatePreferenceSummary(emptySummary, newSnippets));
        }

        @ParameterizedTest
        @EmptySource
        @DisplayName("Handle empty snippet summary in identification")
        void testIdentifyEmptySnippet(String emptySummary) {
            List<Preference> preferences = List.of(new Preference());

            // Implementation validates that snippet summary is not empty
            assertThrows(ContentProcessingException.class, () ->
                processor.identifyPreferencesForSnippet(emptySummary, preferences));
        }
    }

    @Nested
    @DisplayName("5. Exception Handling")
    class ExceptionHandling {

        @Test
        @DisplayName("Throw ContentProcessingException on LLM failure in update")
        void testUpdateLLMFailure() {
            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("LLM API error"));

            List<Snippet> newSnippets = new ArrayList<>();
            newSnippets.add(new Snippet());

            assertThrows(ContentProcessingException.class, () ->
                processor.updatePreferenceSummary("summary", newSnippets));
        }

        @Test
        @DisplayName("Retry on transient LLM failures in update")
        void testUpdateRetryMechanism() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Updated summary");

            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("First attempt failed"))
                .thenReturn(mockResponse);

            List<Snippet> newSnippets = new ArrayList<>();
            newSnippets.add(new Snippet());

            String updated = processor.updatePreferenceSummary("summary", newSnippets);

            assertNotNull(updated);
            verify(mockLlmClient, times(2)).chat(anyList(), anyString());
        }
    }

    @Nested
    @DisplayName("6. Performance Benchmarks")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    class PerformanceBenchmarks {

        @Test
        @DisplayName("Complete preference update within 100ms")
        void testUpdatePerformance() {
            String existingSummary = "User likes programming";
            List<Snippet> newSnippets = new ArrayList<>();
            newSnippets.add(new Snippet());

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Updated summary");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            long startTime = System.currentTimeMillis();
            processor.updatePreferenceSummary(existingSummary, newSnippets);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took: " + duration + "ms");
        }

        @Test
        @DisplayName("Memory usage remains under 512MB during processing")
        void testMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

            for (int i = 0; i < 100; i++) {
                UnifiedMessage mockResponse = mock(UnifiedMessage.class);
                when(mockResponse.content()).thenReturn("Updated " + i);
                when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

                List<Snippet> newSnippets = new ArrayList<>();
                newSnippets.add(new Snippet());
                processor.updatePreferenceSummary("Summary " + i, newSnippets);
            }

            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = (memoryAfter - memoryBefore) / (1024 * 1024);

            assertTrue(memoryUsed < 512, "Memory usage should be under 512MB, used: " + memoryUsed + "MB");
        }
    }

    @Nested
    @DisplayName("7. Concurrency Scenarios")
    class ConcurrencyScenarios {

        @Test
        @DisplayName("Handle 10 concurrent preference updates")
        void testConcurrentUpdates() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Updated summary");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        List<Snippet> newSnippets = new ArrayList<>();
                        newSnippets.add(new Snippet());
                        processor.updatePreferenceSummary("Summary", newSnippets);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within timeout");
            assertEquals(threadCount, successCount.get(), "All requests should succeed");

            executor.shutdown();
        }
    }
}
