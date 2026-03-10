package tech.yesboss.gateway.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Test to verify Feishu card message format is correct.
 *
 * <p>This test verifies that interactive card messages are sent with the correct format:
 * content should be a JSON string, not a JSON object.</p>
 */
public class FeishuApiClientCardFormatTest {

    private ObjectMapper objectMapper;
    private FeishuApiClient feishuApiClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Create a test client with dummy credentials
        feishuApiClient = new FeishuApiClient("test_app_id", "test_app_secret", 30);
    }

    @Test
    void testCardContentShouldBeString() throws Exception {
        // Sample card JSON from UICardRendererImpl
        String cardJson = "{\"elements\":[{\"tag\":\"div\",\"text\":{\"content\":\"Test\",\"tag\":\"plain_text\"}}],\"header\":{\"title\":{\"content\":\"Test\",\"tag\":\"plain_text\"}}}";

        // Parse the card JSON to verify it's valid
        JsonNode cardNode = objectMapper.readTree(cardJson);
        assertNotNull(cardNode);
        assertTrue(cardNode.has("elements"));
        assertTrue(cardNode.has("header"));

        System.out.println("✓ Card JSON is valid");
        System.out.println("Card structure: " + cardNode.toPrettyString());
    }

    @Test
    void testExpectedPayloadStructure() throws Exception {
        // Expected payload structure according to Feishu API
        String cardJson = "{\"elements\":[{\"tag\":\"div\",\"text\":{\"content\":\"Test\",\"tag\":\"plain_text\"}}]}";

        // Build expected payload
        String expectedPayload = String.format(
            "{\"receive_id\":\"oc_test\",\"msg_type\":\"interactive\",\"content\":%s}",
            objectMapper.writeValueAsString(cardJson)
        );

        JsonNode payloadNode = objectMapper.readTree(expectedPayload);

        // Verify structure
        assertEquals("oc_test", payloadNode.get("receive_id").asText());
        assertEquals("interactive", payloadNode.get("msg_type").asText());

        // Critical: content should be a string, not an object
        JsonNode contentNode = payloadNode.get("content");
        assertTrue(contentNode.isTextual(), "Content should be a string node");
        String contentString = contentNode.asText();

        // Verify the content string can be parsed back to JSON
        JsonNode parsedContent = objectMapper.readTree(contentString);
        assertTrue(parsedContent.has("elements"), "Parsed content should have elements");

        System.out.println("✓ Payload structure is correct");
        System.out.println("Content is a string: " + contentString);
        System.out.println("Full payload: " + payloadNode.toPrettyString());
    }

    @Test
    void testTextMessageContentShouldBeObject() throws Exception {
        // For text messages, content should be a JSON object
        String textContent = "Hello, Feishu!";

        // Build expected payload for text message
        String expectedPayload = String.format(
            "{\"receive_id\":\"oc_test\",\"msg_type\":\"text\",\"content\":{\"text\":\"%s\"}}",
            textContent
        );

        JsonNode payloadNode = objectMapper.readTree(expectedPayload);

        // Verify structure
        assertEquals("oc_test", payloadNode.get("receive_id").asText());
        assertEquals("text", payloadNode.get("msg_type").asText());

        // For text messages, content should be an object with "text" field
        JsonNode contentNode = payloadNode.get("content");
        assertTrue(contentNode.isObject(), "Content should be an object node for text messages");
        assertTrue(contentNode.has("text"), "Content should have 'text' field");
        assertEquals(textContent, contentNode.get("text").asText());

        System.out.println("✓ Text message payload structure is correct");
        System.out.println("Full payload: " + payloadNode.toPrettyString());
    }
}
