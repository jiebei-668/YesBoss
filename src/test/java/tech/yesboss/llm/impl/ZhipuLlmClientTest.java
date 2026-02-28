package tech.yesboss.llm.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.yesboss.domain.message.UnifiedMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ZhipuLlmClient.
 *
 * <p>Tests are marked as @Disabled by default and require ZHIPU_API_KEY
 * environment variable to run. These are integration tests that make real
 * API calls to Zhipu GLM.</p>
 */
class ZhipuLlmClientTest {

    private static final String TEST_API_KEY = System.getenv("ZHIPU_API_KEY");
    private ZhipuLlmClient client;

    @BeforeEach
    void setUp() {
        // Skip tests if API key is not available
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }
        // Create client with test API key
        client = new ZhipuLlmClient(TEST_API_KEY, "glm-4-flash");
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testConstructorWithValidParameters() {
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        ZhipuLlmClient testClient = new ZhipuLlmClient(
                TEST_API_KEY,
                "glm-4-flash",
                2048,
                0.5,
                10,
                60,
                "https://open.bigmodel.cn"
        );

        assertNotNull(testClient);
        assertEquals("glm-4-flash", testClient.getModel());
        assertEquals(2048, testClient.getMaxTokens());
        assertEquals(0.5, testClient.getTemperature());
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testConstructorWithNullApiKeyThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ZhipuLlmClient(null)
        );

        assertTrue(exception.getMessage().contains("API key"));
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testConstructorWithEmptyApiKeyThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ZhipuLlmClient("")
        );

        assertTrue(exception.getMessage().contains("API key"));
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testConstructorWithNullModelThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ZhipuLlmClient(TEST_API_KEY, null)
        );

        assertTrue(exception.getMessage().contains("Model"));
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testConstructorWithInvalidMaxTokensThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ZhipuLlmClient(TEST_API_KEY, "glm-4-flash", 0, 0.7, 10, 60, "https://open.bigmodel.cn")
        );

        assertTrue(exception.getMessage().contains("Max tokens"));
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testConstructorWithInvalidTemperatureThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ZhipuLlmClient(TEST_API_KEY, "glm-4-flash", 4096, 3.0, 10, 60, "https://open.bigmodel.cn")
        );

        assertTrue(exception.getMessage().contains("Temperature"));
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testChatWithSimpleMessage() {
        if (client == null) {
            return;
        }

        List<UnifiedMessage> messages = List.of(
                UnifiedMessage.ofText(UnifiedMessage.Role.USER, "Hello, how are you?")
        );

        UnifiedMessage response = client.chat(messages, null);

        assertNotNull(response);
        assertNotNull(response.content());
        assertFalse(response.content().isEmpty());
        assertEquals(UnifiedMessage.Role.ASSISTANT, response.role());
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testChatWithSystemPrompt() {
        if (client == null) {
            return;
        }

        List<UnifiedMessage> messages = List.of(
                UnifiedMessage.ofText(UnifiedMessage.Role.USER, "What is 2+2?")
        );

        String systemPrompt = "You are a helpful math tutor. Provide brief, clear answers.";
        UnifiedMessage response = client.chat(messages, systemPrompt);

        assertNotNull(response);
        assertNotNull(response.content());
        assertFalse(response.content().isEmpty());
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testChatWithMultipleMessages() {
        if (client == null) {
            return;
        }

        List<UnifiedMessage> messages = List.of(
                UnifiedMessage.ofText(UnifiedMessage.Role.USER, "My name is Alice."),
                UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Nice to meet you, Alice!"),
                UnifiedMessage.ofText(UnifiedMessage.Role.USER, "What's my name?")
        );

        UnifiedMessage response = client.chat(messages, null);

        assertNotNull(response);
        assertNotNull(response.content());
        assertTrue(response.content().toLowerCase().contains("alice"));
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testChatWithNullMessagesThrowsException() {
        if (client == null) {
            return;
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> client.chat(null, null)
        );
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testChatWithEmptyMessagesThrowsException() {
        if (client == null) {
            return;
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> client.chat(List.of(), null)
        );
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testSummarizeWithShortContent() {
        if (client == null) {
            return;
        }

        String content = "Zhipu AI is a Chinese artificial intelligence company " +
                "that develops large language models and AI services.";

        String summary = client.summarize(content);

        assertNotNull(summary);
        assertFalse(summary.isEmpty());
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testSummarizeWithLongContent() {
        if (client == null) {
            return;
        }

        StringBuilder longContent = new StringBuilder();
        longContent.append("Artificial intelligence (AI) is intelligence demonstrated by machines, ");
        longContent.append("as opposed to the natural intelligence displayed by humans or animals. ");
        longContent.append("Leading AI textbooks define the field as the study of intelligent agents: ");
        longContent.append("any system that perceives its environment and takes actions that maximize ");
        longContent.append("its chance of achieving its goals. Some popular accounts use the term ");
        longContent.append("\"artificial intelligence\" to describe machines that mimic \"cognitive\" ");
        longContent.append("functions that humans associate with the human mind, such as \"learning\" ");
        longContent.append("and \"problem solving\". AI applications include advanced web search engines, ");
        longContent.append("recommendation systems (used by YouTube, Amazon and Netflix), understanding ");
        longContent.append("human speech (such as Siri and Alexa), self-driving cars (e.g. Tesla), ");
        longContent.append("and competing at the highest level in strategic game systems (such as chess and Go).");

        String summary = client.summarize(longContent.toString());

        assertNotNull(summary);
        assertFalse(summary.isEmpty());
        assertTrue(summary.length() < longContent.length());
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testSummarizeWithNullContentThrowsException() {
        if (client == null) {
            return;
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> client.summarize(null)
        );
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testSummarizeWithEmptyContentThrowsException() {
        if (client == null) {
            return;
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> client.summarize("")
        );
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testConstructorWithDefaultParameters() {
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        ZhipuLlmClient testClient = new ZhipuLlmClient(TEST_API_KEY);

        assertNotNull(testClient);
        assertEquals("glm-4-flash", testClient.getModel());
        assertEquals(4096, testClient.getMaxTokens());
        assertEquals(0.7, testClient.getTemperature());
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testGetApiKeyReturnsApiKey() {
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        ZhipuLlmClient testClient = new ZhipuLlmClient(TEST_API_KEY);

        assertEquals(TEST_API_KEY, testClient.getApiKey());
    }

    @Test
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testChatWithToolResults() {
        if (client == null) {
            return;
        }

        List<UnifiedMessage> messages = List.of(
                UnifiedMessage.ofText(UnifiedMessage.Role.USER, "Calculate 15 + 27"),
                UnifiedMessage.ofToolResult("call_123", "The result is 42", false)
        );

        UnifiedMessage response = client.chat(messages, null);

        assertNotNull(response);
        assertNotNull(response.content());
    }
}
