package tech.yesboss.gateway.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.yesboss.gateway.ui.impl.UICardRendererImpl;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 UICardRendererImpl 的卡片渲染功能
 */
class UICardRendererImplTest {

    private UICardRenderer renderer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        renderer = new UICardRendererImpl(objectMapper);
    }

    @Test
    void testConstructorWithObjectMapper() {
        assertNotNull(renderer);
        assertDoesNotThrow(() -> new UICardRendererImpl(objectMapper));
    }

    @Test
    void testConstructorDefault() {
        assertDoesNotThrow(() -> new UICardRendererImpl());
    }

    @Test
    void testRenderProgressBarWithBasicProgress() {
        JsonNode card = renderer.renderProgressBar(10, 5, "正在执行数据库迁移");

        assertNotNull(card);
        assertEquals("progress", card.get("card_type").asText());
        assertEquals(10, card.get("total_tasks").asInt());
        assertEquals(5, card.get("completed_tasks").asInt());
        assertEquals(50, card.get("progress_percent").asInt());
        assertEquals("正在执行数据库迁移", card.get("current_task").asText());
    }

    @Test
    void testRenderProgressBarAtZero() {
        JsonNode card = renderer.renderProgressBar(10, 0, "准备中...");

        assertNotNull(card);
        assertEquals(0, card.get("progress_percent").asInt());
    }

    @Test
    void testRenderProgressBarAtComplete() {
        JsonNode card = renderer.renderProgressBar(10, 10, "任务完成");

        assertNotNull(card);
        assertEquals(100, card.get("progress_percent").asInt());
    }

    @Test
    void testRenderProgressBarContainsFeishuCard() {
        JsonNode card = renderer.renderProgressBar(5, 2, "正在处理");

        JsonNode feishuCard = card.get("feishu_card");
        assertNotNull(feishuCard);
        assertEquals("interactive", feishuCard.get("msg_type").asText());

        JsonNode content = feishuCard.get("content");
        assertNotNull(content);
        assertTrue(content.has("elements"));
    }

    @Test
    void testRenderProgressBarContainsSlackAttachments() {
        JsonNode card = renderer.renderProgressBar(5, 2, "正在处理");

        JsonNode slackAttachments = card.get("slack_attachments");
        assertNotNull(slackAttachments);
        assertTrue(slackAttachments.isArray());
        assertTrue(slackAttachments.size() > 0);

        JsonNode firstAttachment = slackAttachments.get(0);
        assertNotNull(firstAttachment);
        assertTrue(firstAttachment.has("title"));
        assertTrue(firstAttachment.has("text"));
        assertTrue(firstAttachment.has("color"));
    }

    @Test
    void testRenderSuspensionCardWithAllParameters() {
        JsonNode card = renderer.renderSuspensionCard(
                "session-123",
                "tool-call-456",
                "rm -rf /important/data",
                "DeleteTool"
        );

        assertNotNull(card);
        assertEquals("suspension", card.get("card_type").asText());
        assertEquals("session-123", card.get("session_id").asText());
        assertEquals("tool-call-456", card.get("tool_call_id").asText());
        assertEquals("rm -rf /important/data", card.get("intercepted_command").asText());
        assertEquals("DeleteTool", card.get("tool_name").asText());
    }

    @Test
    void testRenderSuspensionCardWithNullParameters() {
        JsonNode card = renderer.renderSuspensionCard(null, null, null, null);

        assertNotNull(card);
        assertEquals("suspension", card.get("card_type").asText());
        assertEquals("未知命令", card.get("intercepted_command").asText());
        assertEquals("未知工具", card.get("tool_name").asText());
    }

    @Test
    void testRenderSuspensionCardContainsFeishuCard() {
        JsonNode card = renderer.renderSuspensionCard(
                "session-123",
                "tool-call-456",
                "rm -rf /data",
                "DeleteTool"
        );

        JsonNode feishuCard = card.get("feishu_card");
        assertNotNull(feishuCard);
        assertEquals("interactive", feishuCard.get("msg_type").asText());

        JsonNode content = feishuCard.get("content");
        assertNotNull(content);
        assertTrue(content.has("elements"));
    }

    @Test
    void testRenderSuspensionCardContainsSlackAttachments() {
        JsonNode card = renderer.renderSuspensionCard(
                "session-123",
                "tool-call-456",
                "rm -rf /data",
                "DeleteTool"
        );

        JsonNode slackAttachments = card.get("slack_attachments");
        assertNotNull(slackAttachments);
        assertTrue(slackAttachments.isArray());
        assertTrue(slackAttachments.size() > 0);

        JsonNode firstAttachment = slackAttachments.get(0);
        assertEquals("danger", firstAttachment.get("color").asText());
        assertTrue(firstAttachment.get("title").asText().contains("高危操作拦截"));
    }

    @Test
    void testRenderSuspensionCardContainsApprovalButtons() {
        JsonNode card = renderer.renderSuspensionCard(
                "session-123",
                "tool-call-456",
                "format c:",
                "FormatTool"
        );

        // 检查飞书卡片中的按钮
        JsonNode feishuCard = card.get("feishu_card");
        JsonNode elements = feishuCard.get("content").get("elements");
        boolean hasActions = false;
        for (JsonNode element : elements) {
            if ("action".equals(element.get("tag").asText())) {
                hasActions = true;
                JsonNode actions = element.get("actions");
                assertTrue(actions.isArray());
                assertTrue(actions.size() >= 2);

                // 验证批准按钮
                JsonNode approveButton = actions.get(0);
                assertTrue(approveButton.get("value").asText().contains("\"approved\":true"));

                // 验证拒绝按钮
                JsonNode rejectButton = actions.get(1);
                assertTrue(rejectButton.get("value").asText().contains("\"approved\":false"));
            }
        }
        assertTrue(hasActions);
    }

    @Test
    void testRenderSummaryCardWithSuccess() {
        JsonNode card = renderer.renderSummaryCard("session-789", "所有任务已完成", true);

        assertNotNull(card);
        assertEquals("summary", card.get("card_type").asText());
        assertEquals("session-789", card.get("session_id").asText());
        assertTrue(card.get("success").asBoolean());
        assertEquals("所有任务已完成", card.get("summary_text").asText());
    }

    @Test
    void testRenderSummaryCardWithFailure() {
        JsonNode card = renderer.renderSummaryCard("session-789", "任务执行失败", false);

        assertNotNull(card);
        assertEquals("summary", card.get("card_type").asText());
        assertFalse(card.get("success").asBoolean());
        assertEquals("任务执行失败", card.get("summary_text").asText());
    }

    @Test
    void testRenderSummaryCardWithNullSummary() {
        JsonNode card = renderer.renderSummaryCard("session-789", null, true);

        assertNotNull(card);
        assertEquals("summary", card.get("card_type").asText());
        assertEquals("任务已完成", card.get("summary_text").asText());
    }

    @Test
    void testRenderSummaryCardSuccessHasGreenColor() {
        JsonNode card = renderer.renderSummaryCard("session-789", "成功", true);

        // 检查 Slack attachment 的颜色
        JsonNode slackAttachments = card.get("slack_attachments");
        JsonNode firstAttachment = slackAttachments.get(0);
        assertEquals("good", firstAttachment.get("color").asText());
    }

    @Test
    void testRenderSummaryCardFailureHasRedColor() {
        JsonNode card = renderer.renderSummaryCard("session-789", "失败", false);

        // 检查 Slack attachment 的颜色
        JsonNode slackAttachments = card.get("slack_attachments");
        JsonNode firstAttachment = slackAttachments.get(0);
        assertEquals("danger", firstAttachment.get("color").asText());
    }

    @Test
    void testAllCardsContainTimestamp() {
        // 进度条卡片可能不包含时间戳
        JsonNode progressBar = renderer.renderProgressBar(10, 5, "测试");

        // 挂起卡片应该包含时间戳
        JsonNode suspensionCard = renderer.renderSuspensionCard("s1", "t1", "cmd", "tool");
        assertTrue(suspensionCard.has("timestamp"));

        // 总结卡片应该包含时间戳
        JsonNode summaryCard = renderer.renderSummaryCard("s2", "总结", true);
        assertTrue(summaryCard.has("timestamp"));
    }

    @Test
    void testProgressBarStringFormatting() {
        JsonNode card = renderer.renderProgressBar(10, 5, "测试");

        // 验证飞书卡片中包含进度条元素
        JsonNode feishuCard = card.get("feishu_card");
        JsonNode elements = feishuCard.get("content").get("elements");

        boolean hasProgressBar = false;
        for (JsonNode element : elements) {
            JsonNode text = element.get("text");
            if (text != null && text.asText().contains("█")) {
                hasProgressBar = true;
                break;
            }
        }
        assertTrue(hasProgressBar);
    }

    @Test
    void testFeishuCardStructureCompliance() {
        JsonNode card = renderer.renderProgressBar(5, 3, "测试任务");

        JsonNode feishuCard = card.get("feishu_card");
        assertEquals("interactive", feishuCard.get("msg_type").asText());

        JsonNode content = feishuCard.get("content");
        assertNotNull(content);
        assertTrue(content.has("elements"));

        // 验证 elements 是数组
        JsonNode elements = content.get("elements");
        assertTrue(elements.isArray());
        assertTrue(elements.size() > 0);
    }

    @Test
    void testSlackAttachmentStructureCompliance() {
        JsonNode card = renderer.renderSuspensionCard("s1", "t1", "rm -rf /", "DeleteTool");

        JsonNode slackAttachments = card.get("slack_attachments");
        assertTrue(slackAttachments.isArray());
        assertTrue(slackAttachments.size() > 0);

        JsonNode attachment = slackAttachments.get(0);
        assertTrue(attachment.has("color"));
        assertTrue(attachment.has("title"));
        assertTrue(attachment.has("text"));
        assertTrue(attachment.has("footer"));
        assertTrue(attachment.has("ts"));
    }

    @Test
    void testSuspensionCardValueJsonFormat() {
        String sessionId = "test-session-123";
        String toolCallId = "test-tool-call-456";

        JsonNode card = renderer.renderSuspensionCard(sessionId, toolCallId, "cmd", "tool");

        // 验证按钮的 value 字段是合法的 JSON 字符串
        JsonNode feishuCard = card.get("feishu_card");
        JsonNode elements = feishuCard.get("content").get("elements");

        for (JsonNode element : elements) {
            if ("action".equals(element.get("tag").asText())) {
                JsonNode actions = element.get("actions");
                for (JsonNode action : actions) {
                    String value = action.get("value").asText();
                    // 验证可以解析为 JSON
                    assertDoesNotThrow(() -> {
                        try {
                            objectMapper.readTree(value);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

                    // 验证包含必要的字段
                    assertDoesNotThrow(() -> {
                        try {
                            JsonNode valueJson = objectMapper.readTree(value);
                            assertEquals(sessionId, valueJson.get("session_id").asText());
                            assertEquals(toolCallId, valueJson.get("tool_call_id").asText());
                            assertTrue(valueJson.has("approved"));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        }
    }
}
