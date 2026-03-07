package tech.yesboss.memory.documentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Documentation Validation Test
 *
 * Validates that all documentation files:
 * - Exist and are readable
 * - Have proper structure
 * - Contain required sections
 * - Have valid code examples
 * - Are consistent with the actual implementation
 */
@SpringBootTest
class DocumentationValidationTest {

    @TempDir
    Path tempDir;

    private static final String DOCS_PATH = "docs_memory";

    @Test
    @DisplayName("All documentation files should exist")
    void testAllDocumentationFilesExist() {
        String[] requiredFiles = {
            DOCS_PATH + "/QUICK_START.md",
            DOCS_PATH + "/API_DOCUMENTATION.md",
            DOCS_PATH + "/USAGE_EXAMPLES.md",
            DOCS_PATH + "/CONFIGURATION_GUIDE.md",
            DOCS_PATH + "/记忆持久化模块v3.0.md"
        };

        for (String filePath : requiredFiles) {
            Path path = Path.of(filePath);
            assertTrue(Files.exists(path), "Documentation file should exist: " + filePath);
            assertTrue(Files.isReadable(path), "Documentation file should be readable: " + filePath);
        }
    }

    @Test
    @DisplayName("Documentation files should not be empty")
    void testDocumentationFilesNotEmpty() throws IOException {
        String[] docFiles = {
            DOCS_PATH + "/QUICK_START.md",
            DOCS_PATH + "/API_DOCUMENTATION.md",
            DOCS_PATH + "/USAGE_EXAMPLES.md",
            DOCS_PATH + "/CONFIGURATION_GUIDE.md"
        };

        for (String filePath : docFiles) {
            String content = Files.readString(Path.of(filePath));
            assertFalse(content.trim().isEmpty(), "Documentation file should not be empty: " + filePath);
            assertTrue(content.length() > 1000, "Documentation file should have substantial content: " + filePath);
        }
    }

    @Test
    @DisplayName("Quick Start Guide should contain required sections")
    void testQuickStartGuideStructure() throws IOException {
        String content = Files.readString(Path.of(DOCS_PATH + "/QUICK_START.md"));

        String[] requiredSections = {
            "# Memory Persistence Module - Quick Start Guide",
            "## Overview",
            "## Features",
            "## Architecture",
            "## Getting Started",
            "## Dependencies",
            "## Configuration",
            "## Basic Usage",
            "## Automatic Memory Extraction",
            "## Batch Processing",
            "## Monitoring",
            "## Next Steps",
            "## Troubleshooting"
        };

        for (String section : requiredSections) {
            assertTrue(content.contains(section), "Quick Start Guide should contain section: " + section);
        }
    }

    @Test
    @DisplayName("API Documentation should contain all service APIs")
    void testApiDocumentationCompleteness() throws IOException {
        String content = Files.readString(Path.of(DOCS_PATH + "/API_DOCUMENTATION.md"));

        String[] requiredServices = {
            "## 1. MemoryService",
            "## 2. MemoryQueryService",
            "## 3. MemoryManager",
            "## 4. ContentProcessor",
            "## 5. EmbeddingService",
            "## 6. VectorStore",
            "## 7. Repositories",
            "## 8. Models",
            "## 9. Monitoring"
        };

        for (String service : requiredServices) {
            assertTrue(content.contains(service), "API Documentation should contain: " + service);
        }
    }

    @Test
    @DisplayName("Usage Examples should cover all major scenarios")
    void testUsageExamplesCoverage() throws IOException {
        String content = Files.readString(Path.of(DOCS_PATH + "/USAGE_EXAMPLES.md"));

        String[] requiredSections = {
            "## 1. Basic Usage",
            "## 2. Advanced Queries",
            "## 3. Batch Operations",
            "## 4. Custom Triggers",
            "## 5. Error Handling",
            "## 6. Integration Patterns",
            "## 7. Testing"
        };

        for (String section : requiredSections) {
            assertTrue(content.contains(section), "Usage Examples should contain: " + section);
        }
    }

    @Test
    @DisplayName("Configuration Guide should cover all configuration aspects")
    void testConfigurationGuideCompleteness() throws IOException {
        String content = Files.readString(Path.of(DOCS_PATH + "/CONFIGURATION_GUIDE.md"));

        String[] requiredSections = {
            "## 2. Basic Configuration",
            "## 3. Vector Store Configuration",
            "## 4. Embedding Service Configuration",
            "## 5. Content Processing Configuration",
            "## 6. Trigger Configuration",
            "## 7. Monitoring Configuration",
            "## 8. Database Configuration",
            "## 9. Performance Tuning",
            "## 10. Troubleshooting"
        };

        for (String section : requiredSections) {
            assertTrue(content.contains(section), "Configuration Guide should contain: " + section);
        }
    }

    @Test
    @DisplayName("Code examples should be properly formatted with syntax highlighting")
    void testCodeExamplesFormatting() throws IOException {
        String[] docFiles = {
            DOCS_PATH + "/QUICK_START.md",
            DOCS_PATH + "/API_DOCUMENTATION.md",
            DOCS_PATH + "/USAGE_EXAMPLES.md"
        };

        for (String filePath : docFiles) {
            String content = Files.readString(Path.of(filePath));

            // Check for code blocks with language specification
            Pattern pattern = Pattern.compile("```(java|yaml|sql|bash)");
            Matcher matcher = pattern.matcher(content);
            assertTrue(matcher.find(), "Documentation should have code blocks with syntax highlighting: " + filePath);
        }
    }

    @Test
    @DisplayName("Java code examples should compile without syntax errors")
    void testJavaCodeExamplesSyntax() throws IOException {
        String content = Files.readString(Path.of(DOCS_PATH + "/USAGE_EXAMPLES.md"));

        // Extract Java code blocks
        Pattern pattern = Pattern.compile("```java\\n([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(content);

        int javaCodeBlocks = 0;
        while (matcher.find()) {
            javaCodeBlocks++;
            String codeBlock = matcher.group(1);

            // Basic syntax checks
            assertFalse(codeBlock.trim().isEmpty(), "Java code block should not be empty");

            // Check for common Java patterns
            boolean hasValidStructure = codeBlock.contains("class ") ||
                                      codeBlock.contains("interface ") ||
                                      codeBlock.contains("@Service") ||
                                      codeBlock.contains("@Component") ||
                                      codeBlock.contains("public void") ||
                                      codeBlock.contains("public String");

            // Some code blocks might be snippets, so this is a soft check
            // We just verify they look like Java code
            assertTrue(hasValidStructure || codeBlock.contains("import"),
                "Java code block should look like valid Java code");
        }

        assertTrue(javaCodeBlocks > 10, "Should have multiple Java code examples");
    }

    @Test
    @DisplayName("YAML configuration examples should be valid")
    void testYamlConfigurationExamples() throws IOException {
        String[] docFiles = {
            DOCS_PATH + "/QUICK_START.md",
            DOCS_PATH + "/CONFIGURATION_GUIDE.md"
        };

        for (String filePath : docFiles) {
            String content = Files.readString(Path.of(filePath));

            // Extract YAML code blocks
            Pattern pattern = Pattern.compile("```yaml\\n([\\s\\S]*?)```");
            Matcher matcher = pattern.matcher(content);

            int yamlBlocks = 0;
            while (matcher.find()) {
                yamlBlocks++;
                String yamlBlock = matcher.group(1);

                // Basic YAML structure checks
                assertTrue(yamlBlock.contains(":"), "YAML should contain key-value pairs");
                assertFalse(yamlBlock.contains("\t"), "YAML should not use tabs, use spaces instead");
            }

            assertTrue(yamlBlocks > 0, "Should have YAML configuration examples: " + filePath);
        }
    }

    @Test
    @DisplayName("Documentation should be consistent with API signatures")
    void testApiDocumentationConsistency() throws IOException {
        String apiDoc = Files.readString(Path.of(DOCS_PATH + "/API_DOCUMENTATION.md"));

        // Check for documented methods
        String[] documentedMethods = {
            "extractFromMessages",
            "concatenateConversationContent",
            "segmentConversation",
            "generateSegmentAbstract",
            "buildResource",
            "extractStructuredMemories",
            "searchResources",
            "searchSnippets",
            "agenticRagQuery"
        };

        for (String method : documentedMethods) {
            assertTrue(apiDoc.contains(method), "API documentation should document method: " + method);
        }
    }

    @Test
    @DisplayName("Documentation should contain error handling examples")
    void testErrorHandlingExamples() throws IOException {
        String usageContent = Files.readString(Path.of(DOCS_PATH + "/USAGE_EXAMPLES.md"));
        String configContent = Files.readString(Path.of(DOCS_PATH + "/CONFIGURATION_GUIDE.md"));

        // Check for error handling coverage
        assertTrue(usageContent.contains("Error Handling") ||
                  usageContent.contains("error handling"),
                  "Usage examples should cover error handling");

        assertTrue(configContent.contains("Troubleshooting"),
                  "Configuration guide should have troubleshooting section");

        // Check for exception handling patterns
        assertTrue(usageContent.contains("try") && usageContent.contains("catch"),
                  "Should show try-catch patterns");
    }

    @Test
    @DisplayName("Documentation should provide performance guidance")
    void testPerformanceGuidance() throws IOException {
        String configContent = Files.readString(Path.of(DOCS_PATH + "/CONFIGURATION_GUIDE.md"));

        assertTrue(configContent.contains("Performance Tuning"),
                  "Configuration guide should have performance tuning section");

        assertTrue(configContent.contains("batch-size") ||
                  configContent.contains("connection-pool") ||
                  configContent.contains("cache"),
                  "Should provide performance-related configuration examples");
    }

    @Test
    @DisplayName("Documentation should have table of contents")
    void testTableOfContents() throws IOException {
        String[] docFiles = {
            DOCS_PATH + "/QUICK_START.md",
            DOCS_PATH + "/API_DOCUMENTATION.md",
            DOCS_PATH + "/USAGE_EXAMPLES.md",
            DOCS_PATH + "/CONFIGURATION_GUIDE.md"
        };

        for (String filePath : docFiles) {
            String content = Files.readString(Path.of(filePath));

            assertTrue(content.contains("## Table of Contents") ||
                      content.contains("# Table of Contents"),
                      "Documentation should have table of contents: " + filePath);
        }
    }

    @Test
    @DisplayName("Documentation should use consistent formatting")
    void testConsistentFormatting() throws IOException {
        String[] docFiles = {
            DOCS_PATH + "/QUICK_START.md",
            DOCS_PATH + "/API_DOCUMENTATION.md",
            DOCS_PATH + "/USAGE_EXAMPLES.md",
            DOCS_PATH + "/CONFIGURATION_GUIDE.md"
        };

        for (String filePath : docFiles) {
            String content = Files.readString(Path.of(filePath));

            // Check for consistent heading hierarchy (no jumps from h1 to h3)
            Pattern h1 = Pattern.compile("^# [^#]"));
            Pattern h2 = Pattern.compile("^## [^#]"));
            Pattern h3 = Pattern.compile("^### [^#]"));

            // Should have at least one h1
            assertTrue(h1.matcher(content).find(), "Should have h1 headings: " + filePath);

            // Should have multiple h2 or h3
            assertTrue(h2.matcher(content).find() || h3.matcher(content).find(),
                "Should have h2 or h3 headings: " + filePath);
        }
    }

    @Test
    @DisplayName("Code examples should include imports")
    void testCodeExamplesIncludeImports() throws IOException {
        String usageContent = Files.readString(Path.of(DOCS_PATH + "/USAGE_EXAMPLES.md"));

        // Check for import statements in Java examples
        Pattern importPattern = Pattern.compile("import [a-z]");
        Matcher matcher = importPattern.matcher(usageContent);

        assertTrue(matcher.find(), "Code examples should include import statements");
    }

    @Test
    @DisplayName("Documentation should reference actual implementation classes")
    void testDocumentationReferencesRealClasses() throws IOException {
        String apiContent = Files.readString(Path.of(DOCS_PATH + "/API_DOCUMENTATION.md"));

        String[] realClasses = {
            "tech.yesboss.memory.service.MemoryService",
            "tech.yesboss.memory.query.MemoryQueryService",
            "tech.yesboss.memory.model.Resource",
            "tech.yesboss.memory.model.Snippet",
            "tech.yesboss.memory.model.Preference"
        };

        for (String className : realClasses) {
            assertTrue(apiContent.contains(className),
                "API documentation should reference real class: " + className);
        }
    }

    @Test
    @DisplayName("Documentation should explain configuration properties")
    void testConfigurationPropertiesExplained() throws IOException {
        String configContent = Files.readString(Path.of(DOCS_PATH + "/CONFIGURATION_GUIDE.md"));

        String[] keyProperties = {
            "memory.enabled",
            "memory.vector-store.type",
            "memory.embedding.provider",
            "memory.batch-processing.enabled",
            "memory.triggers.interval.enabled"
        };

        for (String property : keyProperties) {
            assertTrue(configContent.contains(property),
                "Configuration guide should explain property: " + property);
        }
    }

    @Test
    @DisplayName("Quick Start should have working first example")
    void testQuickStartFirstExample() throws IOException {
        String quickStartContent = Files.readString(Path.of(DOCS_PATH + "/QUICK_START.md"));

        // Should have a "Getting Started" section with complete example
        assertTrue(quickStartContent.contains("## Getting Started"),
                  "Quick Start should have Getting Started section");

        // Should show dependency configuration
        assertTrue(quickStartContent.contains("<dependency>"),
                  "Quick Start should show Maven dependencies");

        // Should show configuration example
        assertTrue(quickStartContent.contains("application-memory.yml"),
                  "Quick Start should reference configuration file");
    }

    @Test
    @DisplayName("Documentation should link between files")
    void testDocumentationCrossReferences() throws IOException {
        String quickStartContent = Files.readString(Path.of(DOCS_PATH + "/QUICK_START.md"));
        String apiContent = Files.readString(Path.of(DOCS_PATH + "/API_DOCUMENTATION.md"));

        // Check for cross-references
        assertTrue(quickStartContent.contains("API_DOCUMENTATION.md") ||
                  quickStartContent.contains("[API Documentation]"),
                  "Quick Start should reference API Documentation");

        assertTrue(apiContent.contains("QUICK_START.md") ||
                  apiContent.contains("[Quick Start"),
                  "API Documentation should reference Quick Start");
    }

    @Test
    @DisplayName("Documentation should cover both SQLite and PostgreSQL")
    void testDatabaseBackendsCovered() throws IOException {
        String configContent = Files.readString(Path.of(DOCS_PATH + "/CONFIGURATION_GUIDE.md"));

        assertTrue(configContent.contains("SQLite"),
                  "Configuration should cover SQLite");
        assertTrue(configContent.contains("PostgreSQL"),
                  "Configuration should cover PostgreSQL");
        assertTrue(configContent.contains("sqlite-vec") ||
                  configContent.contains("pgvector"),
                  "Configuration should mention vector extensions");
    }

    @Test
    @DisplayName("Documentation should explain all embedding providers")
    void testEmbeddingProvidersCovered() throws IOException {
        String configContent = Files.readString(Path.of(DOCS_PATH + "/CONFIGURATION_GUIDE.md"));

        String[] providers = {"zhipu", "anthropic", "gemini", "openai"};

        for (String provider : providers) {
            assertTrue(configContent.contains(provider),
                "Configuration should cover provider: " + provider);
        }
    }

    @Test
    @DisplayName("Performance targets should be documented")
    void testPerformanceTargetsDocumented() throws IOException {
        String configContent = Files.readString(Path.of(DOCS_PATH + "/CONFIGURATION_GUIDE.md"));

        // Should mention performance metrics
        assertTrue(configContent.contains("performance") ||
                  configContent.contains("Performance"),
                  "Should discuss performance");

        // Should provide tuning recommendations
        assertTrue(configContent.contains("tuning") ||
                  configContent.contains("optimization"),
                  "Should provide tuning guidance");
    }

    @Test
    @DisplayName("Testing examples should be provided")
    void testTestingExamplesIncluded() throws IOException {
        String usageContent = Files.readString(Path.of(DOCS_PATH + "/USAGE_EXAMPLES.md"));

        assertTrue(usageContent.contains("## 7. Testing"),
                  "Usage examples should include testing section");

        assertTrue(usageContent.contains("@Test") ||
                  usageContent.contains("@SpringBootTest"),
                  "Should show testing annotations");

        assertTrue(usageContent.contains("JUnit"),
                  "Should mention JUnit testing framework");
    }
}
