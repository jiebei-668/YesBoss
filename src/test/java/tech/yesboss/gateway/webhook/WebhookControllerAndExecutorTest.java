package tech.yesboss.gateway.webhook;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.yesboss.gateway.webhook.controller.WebhookController;
import tech.yesboss.gateway.webhook.controller.impl.WebhookControllerImpl;
import tech.yesboss.gateway.webhook.executor.WebhookEventExecutor;
import tech.yesboss.gateway.webhook.model.ImWebhookEvent;
import tech.yesboss.gateway.webhook.executor.impl.WebhookEventExecutorImpl;
import tech.yesboss.session.SessionManager;
import tech.yesboss.state.TaskManager;
import tech.yesboss.runner.MasterRunner;
import tech.yesboss.safeguard.SuspendResumeEngine;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for WebhookController and WebhookEventExecutor.
 *
 * Tests cover:
 * - Async event processing
 * - Signature verification (Feishu and Slack)
 * - Payload parsing
 * - Immediate 200 OK response
 * - Graceful shutdown
 */
class WebhookControllerAndExecutorTest {

    @Mock
    private SessionManager mockSessionManager;

    @Mock
    private TaskManager mockTaskManager;

    @Mock
    private MasterRunner mockMasterRunner;

    @Mock
    private SuspendResumeEngine mockSuspendResumeEngine;

    private WebhookEventExecutor executor;
    private WebhookController controller;
    private AutoCloseable mockCloseable;

    // Test secrets for signature verification
    private static final String FEISHU_SECRET = "test_feishu_secret";
    private static final String SLACK_SECRET = "test_slack_signing_secret";

    @BeforeEach
    void setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this);

        // Setup common mock behavior
        when(mockSessionManager.bindOrCreateTaskSession(anyString(), anyString(), anyString()))
            .thenReturn("test-session-id");
        doNothing().when(mockMasterRunner).run(anyString());
        doNothing().when(mockSuspendResumeEngine).resume(anyString(), anyString(), anyBoolean(), anyString());

        // Initialize executor and controller
        executor = new WebhookEventExecutorImpl(mockSessionManager, mockTaskManager, mockMasterRunner);
        controller = new WebhookControllerImpl(executor, mockSuspendResumeEngine, FEISHU_SECRET, SLACK_SECRET);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (executor != null) {
            executor.shutdown();
        }
        mockCloseable.close();
    }

    // ==================== WebhookEventExecutor Tests ====================

    @Test
    void testExecutorConstructorNullSessionManager() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new WebhookEventExecutorImpl(null, mockTaskManager, mockMasterRunner);
        });

        assertTrue(exception.getMessage().contains("sessionManager"));
    }

    @Test
    void testExecutorConstructorNullTaskManager() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new WebhookEventExecutorImpl(mockSessionManager, null, mockMasterRunner);
        });

        assertTrue(exception.getMessage().contains("taskManager"));
    }

    @Test
    void testExecutorConstructorNullMasterRunner() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new WebhookEventExecutorImpl(mockSessionManager, mockTaskManager, null);
        });

        assertTrue(exception.getMessage().contains("masterRunner"));
    }

    @Test
    void testExecutorIsRunningAfterCreation() {
        assertTrue(executor.isRunning());
    }

    @Test
    void testExecutorAsyncProcessing() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean processed = new AtomicBoolean(false);

        // Mock masterRunner to set flag when called
        doAnswer(invocation -> {
            processed.set(true);
            latch.countDown();
            return null;
        }).when(mockMasterRunner).run(anyString());

        // Create and submit event
        ImWebhookEvent event = ImWebhookEvent.create(
            "CLI",
            "command",
            "test-group",
            "test-user",
            "test command"
        );

        executor.processAsync(event);

        // Wait for async processing
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Event should be processed");
        assertTrue(processed.get(), "Event should be marked as processed");
    }

    @Test
    void testExecutorMultipleConcurrentEvents() throws InterruptedException {
        int eventCount = 10;
        CountDownLatch latch = new CountDownLatch(eventCount);
        AtomicInteger processedCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            processedCount.incrementAndGet();
            latch.countDown();
            return null;
        }).when(mockMasterRunner).run(anyString());

        // Submit multiple events concurrently
        for (int i = 0; i < eventCount; i++) {
            ImWebhookEvent event = ImWebhookEvent.create(
                "CLI",
                "command",
                "test-group-" + i,
                "test-user",
                "command " + i
            );
            executor.processAsync(event);
        }

        // Wait for all events to be processed
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All events should be processed");
        assertEquals(eventCount, processedCount.get());
    }

    @Test
    void testExecutorShutdown() {
        assertTrue(executor.isRunning());

        executor.shutdown();

        assertFalse(executor.isRunning());
    }

    @Test
    void testExecutorRejectsEventsAfterShutdown() {
        ImWebhookEvent event = ImWebhookEvent.create(
            "CLI",
            "command",
            "test-group",
            "test-user",
            "test"
        );

        executor.shutdown();

        assertThrows(IllegalStateException.class, () -> {
            executor.processAsync(event);
        });
    }

    @Test
    void testExecutorHandlesEventProcessingException() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> caughtException = new AtomicReference<>();

        // Mock masterRunner to throw exception
        doThrow(new RuntimeException("Test exception")).when(mockMasterRunner).run(anyString());

        // Add a listener to catch the error logged by the executor
        // Since we can't directly catch exceptions in async code,
        // we verify the executor doesn't crash
        ImWebhookEvent event = ImWebhookEvent.create(
            "CLI",
            "command",
            "test-group",
            "test-user",
            "test"
        );

        try {
            executor.processAsync(event);
            // Wait a bit for async processing
            Thread.sleep(500);
            // Executor should still be running despite the exception
            assertTrue(executor.isRunning());
        } finally {
            latch.countDown();
        }
    }

    // ==================== WebhookController Tests ====================

    @Test
    void testControllerConstructorNullExecutor() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new WebhookControllerImpl(null, mockSuspendResumeEngine, FEISHU_SECRET, SLACK_SECRET);
        });

        assertTrue(exception.getMessage().contains("executor"));
    }

    @Test
    void testControllerConstructorAllowsNullSecrets() {
        assertDoesNotThrow(() -> {
            new WebhookControllerImpl(executor, mockSuspendResumeEngine, null, null);
        });

        assertDoesNotThrow(() -> {
            new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        });
    }

    @Test
    void testControllerIsReady() {
        assertTrue(controller.isReady());

        executor.shutdown();

        assertFalse(controller.isReady());
    }

    // ==================== Feishu Event Tests ====================

    @Test
    void testHandleFeishuEventReturns200Ok() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String signature = "test-signature";
        String body = "{\"header\":{\"event_type\":\"im.message.receive_v1\"}}";

        String response = noVerifyController.handleFeishuEvent(timestamp, nonce, signature, body);

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleFeishuEventWithEmptyBody() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String response = noVerifyController.handleFeishuEvent("123456", "nonce", "sig", "");

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleFeishuEventWithNullBody() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String response = noVerifyController.handleFeishuEvent("123456", "nonce", "sig", null);

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleFeishuEventSignatureVerification() throws InterruptedException {
        // Use a real signature for this test
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String body = "{\"header\":{\"event_type\":\"im.message.receive_v1\"}}";

        // Calculate real signature
        String baseString = timestamp + nonce + body;
        String signature = calculateTestHmacSha256(baseString, FEISHU_SECRET);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockMasterRunner).run(anyString());

        String response = controller.handleFeishuEvent(timestamp, nonce, signature, body);

        assertEquals("200 OK", response);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Event should be processed asynchronously");
    }

    @Test
    void testHandleFeishuEventInvalidSignature() {
        // Use an invalid signature
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String invalidSignature = "invalid-signature";
        String body = "{\"header\":{\"event_type\":\"im.message.receive_v1\"}}";

        assertThrows(SecurityException.class, () -> {
            controller.handleFeishuEvent(timestamp, nonce, invalidSignature, body);
        });
    }

    @Test
    void testHandleFeishuEventExpiredTimestamp() {
        // Use an old timestamp (more than 3 minutes ago)
        long oldTimestamp = System.currentTimeMillis() / 1000 - 200;
        String timestamp = String.valueOf(oldTimestamp);
        String nonce = "test-nonce";
        String body = "{\"header\":{\"event_type\":\"im.message.receive_v1\"}}";

        String baseString = timestamp + nonce + body;
        String signature = calculateTestHmacSha256(baseString, FEISHU_SECRET);

        assertThrows(SecurityException.class, () -> {
            controller.handleFeishuEvent(timestamp, nonce, signature, body);
        });
    }

    @Test
    void testHandleFeishuEventSignatureSkippedWhenNoSecret() {
        // Create controller without Feishu secret
        WebhookController noSecretController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", SLACK_SECRET);

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String signature = "any-signature";
        String body = "{\"header\":{\"event_type\":\"im.message.receive_v1\"}}";

        // Should not throw SecurityException even with invalid signature
        assertDoesNotThrow(() -> {
            String response = noSecretController.handleFeishuEvent(timestamp, nonce, signature, body);
            assertEquals("200 OK", response);
        });
    }

    // ==================== Slack Event Tests ====================

    @Test
    void testHandleSlackEventReturns200Ok() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = "test-signature";
        String body = "{\"event\":{\"type\":\"message\"}}";

        String response = noVerifyController.handleSlackEvent(timestamp, signature, body);

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleSlackEventWithEmptyBody() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String response = noVerifyController.handleSlackEvent("123456", "sig", "");

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleSlackEventUrlVerification() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = "test-signature";
        String challenge = "test-challenge-value";
        String body = "{\"type\":\"url_verification\",\"challenge\":\"" + challenge + "\"}";

        String response = noVerifyController.handleSlackEvent(timestamp, signature, body);

        assertEquals(challenge, response);
    }

    @Test
    void testHandleSlackEventSignatureVerification() throws InterruptedException {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String body = "{\"event\":{\"type\":\"message\"}}";

        // Calculate real Slack signature
        String baseString = "v0:" + timestamp + ":" + body;
        String signature = "v0=" + calculateTestHmacSha256(baseString, SLACK_SECRET);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockMasterRunner).run(anyString());

        String response = controller.handleSlackEvent(timestamp, signature, body);

        assertEquals("200 OK", response);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Event should be processed asynchronously");
    }

    @Test
    void testHandleSlackEventInvalidSignature() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String invalidSignature = "v0=invalid-signature";
        String body = "{\"event\":{\"type\":\"message\"}}";

        assertThrows(SecurityException.class, () -> {
            controller.handleSlackEvent(timestamp, invalidSignature, body);
        });
    }

    @Test
    void testHandleSlackEventExpiredTimestamp() {
        // Use an old timestamp (more than 5 minutes ago)
        long oldTimestamp = System.currentTimeMillis() / 1000 - 400;
        String timestamp = String.valueOf(oldTimestamp);
        String body = "{\"event\":{\"type\":\"message\"}}";

        String baseString = "v0:" + timestamp + ":" + body;
        String signature = "v0=" + calculateTestHmacSha256(baseString, SLACK_SECRET);

        assertThrows(SecurityException.class, () -> {
            controller.handleSlackEvent(timestamp, signature, body);
        });
    }

    // ==================== CLI Command Tests ====================

    @Test
    void testHandleCliCommand() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockMasterRunner).run(anyString());

        assertDoesNotThrow(() -> {
            controller.handleCliCommand("test command");
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS), "CLI command should be processed");
    }

    @Test
    void testHandleCliCommandWithEmptyString() {
        assertDoesNotThrow(() -> {
            controller.handleCliCommand("");
        });
    }

    @Test
    void testHandleCliCommandWithNull() {
        assertDoesNotThrow(() -> {
            controller.handleCliCommand(null);
        });
    }

    // ==================== ImWebhookEvent Tests ====================

    @Test
    void testImWebhookEventCreateWithCurrentTimestamp() {
        long before = System.currentTimeMillis();

        ImWebhookEvent event = ImWebhookEvent.create(
            "FEISHU",
            "message",
            "test-group",
            "test-user",
            "payload"
        );

        long after = System.currentTimeMillis();

        assertEquals("FEISHU", event.imType());
        assertEquals("message", event.eventType());
        assertEquals("test-group", event.imGroupId());
        assertEquals("test-user", event.userId());
        assertEquals("payload", event.payload());
        assertTrue(event.receivedAt() >= before && event.receivedAt() <= after);
    }

    @Test
    void testImWebhookEventValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            ImWebhookEvent.create("", "message", "group", "user", "payload");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            ImWebhookEvent.create("FEISHU", "", "group", "user", "payload");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            ImWebhookEvent.create("FEISHU", "message", "", "user", "payload");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            ImWebhookEvent.create("FEISHU", "message", "group", "", "payload");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            ImWebhookEvent.create("FEISHU", "message", "group", "user", null);
        });
    }

    @Test
    void testImWebhookEventHelperMethods() {
        ImWebhookEvent event = ImWebhookEvent.create(
            "FEISHU",
            "message",
            "group-123",
            "user-456",
            "payload"
        );

        assertTrue(event.isMessageEvent());
        assertFalse(event.isGroupJoinEvent());
        assertFalse(event.isGroupDeleteEvent());
        assertTrue(event.isFeishu());
        assertFalse(event.isSlack());
        assertFalse(event.isCli());
    }

    // ==================== Feishu Callback Tests ====================

    @Test
    void testHandleFeishuCallbackReturns200Ok() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String signature = "test-signature";
        String body = buildFeishuCallbackJson("worker-123", "call-456", true);

        String response = noVerifyController.handleFeishuCallback(timestamp, nonce, signature, body);

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleFeishuCallbackWithEmptyBody() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String signature = "test-signature";

        String response = noVerifyController.handleFeishuCallback(timestamp, nonce, signature, "");

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleFeishuCallbackWithNullBody() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String signature = "test-signature";

        String response = noVerifyController.handleFeishuCallback(timestamp, nonce, signature, null);

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleFeishuCallbackSignatureVerification() throws InterruptedException {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String body = buildFeishuCallbackJson("worker-123", "call-456", true);

        // Calculate real signature
        String baseString = timestamp + nonce + body;
        String signature = calculateTestHmacSha256(baseString, FEISHU_SECRET);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockSuspendResumeEngine).resume(anyString(), anyString(), anyBoolean(), anyString());

        String response = controller.handleFeishuCallback(timestamp, nonce, signature, body);

        assertEquals("200 OK", response);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be processed asynchronously");
    }

    @Test
    void testHandleFeishuCallbackInvalidSignature() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String invalidSignature = "invalid-signature";
        String body = buildFeishuCallbackJson("worker-123", "call-456", true);

        assertThrows(SecurityException.class, () -> {
            controller.handleFeishuCallback(timestamp, nonce, invalidSignature, body);
        });
    }

    @Test
    void testHandleFeishuCallbackParsesApproveAction() throws InterruptedException {
        // Create controller without signature verification for parsing tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String sessionId = "worker-session-123";
        String toolCallId = "tool-call-456";
        String body = buildFeishuCallbackJson(sessionId, toolCallId, true);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertEquals(sessionId, invocation.getArgument(0));
            assertEquals(toolCallId, invocation.getArgument(1));
            assertEquals(true, invocation.getArgument(2));
            latch.countDown();
            return null;
        }).when(mockSuspendResumeEngine).resume(anyString(), anyString(), anyBoolean(), anyString());

        String response = noVerifyController.handleFeishuCallback(timestamp, nonce, "sig", body);

        assertEquals("200 OK", response);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be processed");
    }

    @Test
    void testHandleFeishuCallbackParsesRejectAction() throws InterruptedException {
        // Create controller without signature verification for parsing tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String sessionId = "worker-session-123";
        String toolCallId = "tool-call-456";
        String body = buildFeishuCallbackJson(sessionId, toolCallId, false);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertEquals(sessionId, invocation.getArgument(0));
            assertEquals(toolCallId, invocation.getArgument(1));
            assertEquals(false, invocation.getArgument(2));
            latch.countDown();
            return null;
        }).when(mockSuspendResumeEngine).resume(anyString(), anyString(), anyBoolean(), anyString());

        String response = noVerifyController.handleFeishuCallback(timestamp, nonce, "sig", body);

        assertEquals("200 OK", response);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be processed");
    }

    @Test
    void testHandleFeishuCallbackSignatureSkippedWhenNoSecret() {
        // Create controller without Feishu secret
        WebhookController noSecretController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", SLACK_SECRET);

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";
        String signature = "any-signature";
        String body = buildFeishuCallbackJson("worker-123", "call-456", true);

        // Should not throw SecurityException even with invalid signature
        assertDoesNotThrow(() -> {
            String response = noSecretController.handleFeishuCallback(timestamp, nonce, signature, body);
            assertEquals("200 OK", response);
        });
    }

    // ==================== Slack Callback Tests ====================

    @Test
    void testHandleSlackCallbackReturns200Ok() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String payload = buildSlackCallbackPayload("worker-123", "call-456", true);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = "test-signature";

        String response = noVerifyController.handleSlackCallback(payload, timestamp, signature);

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleSlackCallbackWithEmptyPayload() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = "test-signature";

        String response = noVerifyController.handleSlackCallback("", timestamp, signature);

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleSlackCallbackWithNullPayload() {
        // Create controller without signature verification for basic tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = "test-signature";

        String response = noVerifyController.handleSlackCallback(null, timestamp, signature);

        assertEquals("200 OK", response);
    }

    @Test
    void testHandleSlackCallbackSignatureVerification() throws InterruptedException {
        String payload = buildSlackCallbackPayload("worker-123", "call-456", true);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        // Calculate real Slack signature
        String baseString = "v0:" + timestamp + ":" + payload;
        String signature = "v0=" + calculateTestHmacSha256(baseString, SLACK_SECRET);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockSuspendResumeEngine).resume(anyString(), anyString(), anyBoolean(), anyString());

        String response = controller.handleSlackCallback(payload, timestamp, signature);

        assertEquals("200 OK", response);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be processed asynchronously");
    }

    @Test
    void testHandleSlackCallbackInvalidSignature() {
        String payload = buildSlackCallbackPayload("worker-123", "call-456", true);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String invalidSignature = "v0=invalid-signature";

        assertThrows(SecurityException.class, () -> {
            controller.handleSlackCallback(payload, timestamp, invalidSignature);
        });
    }

    @Test
    void testHandleSlackCallbackParsesApproveAction() throws InterruptedException {
        // Create controller without signature verification for parsing tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String sessionId = "worker-session-123";
        String toolCallId = "tool-call-456";
        String payload = buildSlackCallbackPayload(sessionId, toolCallId, true);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertEquals(sessionId, invocation.getArgument(0));
            assertEquals(toolCallId, invocation.getArgument(1));
            assertEquals(true, invocation.getArgument(2));
            latch.countDown();
            return null;
        }).when(mockSuspendResumeEngine).resume(anyString(), anyString(), anyBoolean(), anyString());

        String response = noVerifyController.handleSlackCallback(payload, timestamp, "sig");

        assertEquals("200 OK", response);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be processed");
    }

    @Test
    void testHandleSlackCallbackParsesRejectAction() throws InterruptedException {
        // Create controller without signature verification for parsing tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        String sessionId = "worker-session-123";
        String toolCallId = "tool-call-456";
        String payload = buildSlackCallbackPayload(sessionId, toolCallId, false);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertEquals(sessionId, invocation.getArgument(0));
            assertEquals(toolCallId, invocation.getArgument(1));
            assertEquals(false, invocation.getArgument(2));
            latch.countDown();
            return null;
        }).when(mockSuspendResumeEngine).resume(anyString(), anyString(), anyBoolean(), anyString());

        String response = noVerifyController.handleSlackCallback(payload, timestamp, "sig");

        assertEquals("200 OK", response);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be processed");
    }

    @Test
    void testHandleSlackCallbackParsesActionName() throws InterruptedException {
        // Create controller without signature verification for parsing tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        // Test parsing based on action name instead of value JSON
        String sessionId = "worker-session-123";
        String toolCallId = "tool-call-456";
        String payload = buildSlackCallbackPayloadWithActionName(sessionId, toolCallId, "approve_action");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertEquals(true, invocation.getArgument(2));
            latch.countDown();
            return null;
        }).when(mockSuspendResumeEngine).resume(anyString(), anyString(), anyBoolean(), anyString());

        String response = noVerifyController.handleSlackCallback(payload, timestamp, "sig");

        assertEquals("200 OK", response);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be processed");
    }

    @Test
    void testHandleSlackCallbackRejectActionName() throws InterruptedException {
        // Create controller without signature verification for parsing tests
        WebhookController noVerifyController = new WebhookControllerImpl(executor, mockSuspendResumeEngine, "", "");
        // Test parsing based on action name for rejection
        String sessionId = "worker-session-123";
        String toolCallId = "tool-call-456";
        String payload = buildSlackCallbackPayloadWithActionName(sessionId, toolCallId, "reject_action");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertEquals(false, invocation.getArgument(2));
            latch.countDown();
            return null;
        }).when(mockSuspendResumeEngine).resume(anyString(), anyString(), anyBoolean(), anyString());

        String response = noVerifyController.handleSlackCallback(payload, timestamp, "sig");

        assertEquals("200 OK", response);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be processed");
    }

    // ==================== Helper Methods ====================

    /**
     * Calculate HMAC-SHA256 for testing signature verification.
     *
     * @param data   The data to hash
     * @param secret The secret key
     * @return Base64-encoded HMAC-SHA256 hash
     */
    private String calculateTestHmacSha256(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey =
                new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }

    /**
     * Build Feishu callback JSON payload.
     *
     * @param sessionId  The worker session ID
     * @param toolCallId The tool call ID
     * @param approved   Whether the user approved
     * @return JSON payload string
     */
    private String buildFeishuCallbackJson(String sessionId, String toolCallId, boolean approved) {
        return String.format("""
            {
              "schema": "2.0",
              "header": {
                "event_id": "event-123",
                "event_type": "im.message.receive_v1",
                "tenant_key": "test-tenant",
                "app_id": "test-app",
                "create_time": "%d"
              },
              "action": {
                "value": "{\\\"session_id\\\":\\\"%s\\\",\\\"tool_call_id\\\":\\\"%s\\\",\\\"approved\\\":%s}"
              }
            }
            """,
            System.currentTimeMillis() / 1000,
            sessionId, toolCallId, approved
        );
    }

    /**
     * Build Slack callback payload (URL-encoded).
     *
     * @param sessionId  The worker session ID
     * @param toolCallId The tool call ID
     * @param approved   Whether the user approved
     * @return URL-encoded payload string
     */
    private String buildSlackCallbackPayload(String sessionId, String toolCallId, boolean approved) {
        String jsonPayload = String.format("""
            {
              "type": "interactive_message",
              "actions": [
                {
                  "name": "action_name",
                  "value": "{\\"session_id\\":\\"%s\\",\\"tool_call_id\\":\\"%s\\",\\"approved\\":%s}"
                }
              ],
              "callback_id": "callback-123",
              "team": {
                "id": "T123"
              },
              "channel": {
                "id": "C123"
              },
              "user": {
                "id": "U123"
              }
            }
            """,
            sessionId, toolCallId, approved
        );

        return java.net.URLEncoder.encode(jsonPayload, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Build Slack callback payload with action name instead of value JSON.
     *
     * @param sessionId  The worker session ID
     * @param toolCallId The tool call ID
     * @param actionName The action name (approve_action or reject_action)
     * @return URL-encoded payload string
     */
    private String buildSlackCallbackPayloadWithActionName(String sessionId, String toolCallId, String actionName) {
        String jsonPayload = String.format("""
            {
              "type": "interactive_message",
              "actions": [
                {
                  "name": "%s",
                  "value": "{\\"session_id\\":\\"%s\\",\\"tool_call_id\\":\\"%s\\"}"
                }
              ],
              "callback_id": "callback-123",
              "team": {
                "id": "T123"
              },
              "channel": {
                "id": "C123"
              },
              "user": {
                "id": "U123"
              }
            }
            """,
            actionName, sessionId, toolCallId
        );

        return java.net.URLEncoder.encode(jsonPayload, java.nio.charset.StandardCharsets.UTF_8);
    }
}
