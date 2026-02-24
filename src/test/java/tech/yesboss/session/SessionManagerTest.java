package tech.yesboss.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.persistence.entity.TaskSession;
import tech.yesboss.persistence.event.DeleteMessagesEvent;
import tech.yesboss.persistence.event.DeleteTaskSessionEvent;
import tech.yesboss.persistence.repository.TaskSessionRepository;
import tech.yesboss.session.impl.SessionManagerImpl;
import tech.yesboss.state.TaskManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test class for SessionManager
 *
 * <p>Tests the session lifecycle and routing management functionality including:
 * <ul>
 *   <li>Binding IM group IDs to internal task sessions</li>
 *   <li>Retrieving internal task IDs for routing</li>
 *   <li>Cascade deletion of sessions and messages</li>
 * </ul>
 */
class SessionManagerTest {

    private TaskManager taskManager;
    private TaskSessionRepository taskSessionRepository;
    private SingleThreadDbWriter dbWriter;
    private SessionManager sessionManager;

    private static final String IM_TYPE_FEISHU = "FEISHU";
    private static final String IM_TYPE_SLACK = "SLACK";
    private static final String IM_GROUP_ID = "group-123";
    private static final String TOPIC = "Test task topic";
    private static final String MASTER_SESSION_ID = "master-session-456";
    private static final String WORKER_SESSION_ID_1 = "worker-session-1";
    private static final String WORKER_SESSION_ID_2 = "worker-session-2";

    @BeforeEach
    void setUp() {
        taskManager = mock(TaskManager.class);
        taskSessionRepository = mock(TaskSessionRepository.class);
        dbWriter = mock(SingleThreadDbWriter.class);
        sessionManager = new SessionManagerImpl(taskManager, taskSessionRepository, dbWriter);
    }

    // ==========================================
    // Constructor Tests
    // ==========================================

    @Test
    void testConstructorWithValidParameters() {
        assertDoesNotThrow(() -> new SessionManagerImpl(taskManager, taskSessionRepository, dbWriter));
    }

    @Test
    void testConstructorWithNullTaskManager() {
        assertThrows(IllegalArgumentException.class, () ->
            new SessionManagerImpl(null, taskSessionRepository, dbWriter)
        );
    }

    @Test
    void testConstructorWithNullTaskSessionRepository() {
        assertThrows(IllegalArgumentException.class, () ->
            new SessionManagerImpl(taskManager, null, dbWriter)
        );
    }

    @Test
    void testConstructorWithNullDbWriter() {
        assertThrows(IllegalArgumentException.class, () ->
            new SessionManagerImpl(taskManager, taskSessionRepository, null)
        );
    }

    // ==========================================
    // bindOrCreateTaskSession Tests
    // ==========================================

    @Test
    void testBindOrCreateTaskSessionCreatesNewSession() {
        // Setup
        when(taskManager.createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC))
            .thenReturn(MASTER_SESSION_ID);
        when(taskManager.sessionExists(MASTER_SESSION_ID)).thenReturn(true);

        // Execute
        String sessionId = sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);

        // Verify
        assertEquals(MASTER_SESSION_ID, sessionId);
        verify(taskManager).createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);
        assertTrue(sessionManager.hasBinding(IM_TYPE_FEISHU, IM_GROUP_ID));
    }

    @Test
    void testBindOrCreateTaskSessionReturnsExistingBinding() {
        // Setup - first call creates new session
        when(taskManager.createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC))
            .thenReturn(MASTER_SESSION_ID);
        when(taskManager.sessionExists(MASTER_SESSION_ID)).thenReturn(true);

        // First call creates binding
        String sessionId1 = sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);
        assertEquals(MASTER_SESSION_ID, sessionId1);

        // Second call should return existing binding without creating new session
        String sessionId2 = sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, "Different topic");
        assertEquals(MASTER_SESSION_ID, sessionId2);

        // Verify createMasterTask was called only once
        verify(taskManager, times(1)).createMasterTask(anyString(), anyString(), anyString());
    }

    @Test
    void testBindOrCreateTaskSessionReplacesStaleBinding() {
        // Setup
        when(taskManager.createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC))
            .thenReturn(MASTER_SESSION_ID)
            .thenReturn("new-master-session");

        // First call - session exists
        when(taskManager.sessionExists(MASTER_SESSION_ID)).thenReturn(true);
        String sessionId1 = sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);
        assertEquals(MASTER_SESSION_ID, sessionId1);

        // Second call - session no longer exists, should create new one
        when(taskManager.sessionExists(MASTER_SESSION_ID)).thenReturn(false);
        when(taskManager.sessionExists("new-master-session")).thenReturn(true);
        String sessionId2 = sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);
        assertEquals("new-master-session", sessionId2);

        // Verify createMasterTask was called twice
        verify(taskManager, times(2)).createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);
    }

    @Test
    void testBindOrCreateTaskSessionWithNullImType() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.bindOrCreateTaskSession(null, IM_GROUP_ID, TOPIC)
        );
    }

    @Test
    void testBindOrCreateTaskSessionWithEmptyImType() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.bindOrCreateTaskSession("  ", IM_GROUP_ID, TOPIC)
        );
    }

    @Test
    void testBindOrCreateTaskSessionWithNullImGroupId() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, null, TOPIC)
        );
    }

    @Test
    void testBindOrCreateTaskSessionWithEmptyImGroupId() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, "", TOPIC)
        );
    }

    @Test
    void testBindOrCreateTaskSessionWithNullTopic() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, null)
        );
    }

    @Test
    void testBindOrCreateTaskSessionWithEmptyTopic() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, "  ")
        );
    }

    @Test
    void testBindOrCreateTaskSessionMultipleIndependentBindings() {
        // Setup
        when(taskManager.createMasterTask(anyString(), anyString(), anyString()))
            .thenReturn(MASTER_SESSION_ID)
            .thenReturn("master-session-2");
        when(taskManager.sessionExists(anyString())).thenReturn(true);

        // Create two different bindings
        String sessionId1 = sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, "group-1", "Topic 1");
        String sessionId2 = sessionManager.bindOrCreateTaskSession(IM_TYPE_SLACK, "group-2", "Topic 2");

        // Verify
        assertEquals(MASTER_SESSION_ID, sessionId1);
        assertEquals("master-session-2", sessionId2);
        assertTrue(sessionManager.hasBinding(IM_TYPE_FEISHU, "group-1"));
        assertTrue(sessionManager.hasBinding(IM_TYPE_SLACK, "group-2"));
        assertEquals(2, sessionManager.getBindingCount());
    }

    // ==========================================
    // getInternalTaskId Tests
    // ==========================================

    @Test
    void testGetInternalTaskIdReturnsCorrectSessionId() {
        // Setup - create binding first
        when(taskManager.createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC))
            .thenReturn(MASTER_SESSION_ID);
        when(taskManager.sessionExists(MASTER_SESSION_ID)).thenReturn(true);

        sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);

        // Execute
        String sessionId = sessionManager.getInternalTaskId(IM_TYPE_FEISHU, IM_GROUP_ID);

        // Verify
        assertEquals(MASTER_SESSION_ID, sessionId);
    }

    @Test
    void testGetInternalTaskIdWithNullImType() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.getInternalTaskId(null, IM_GROUP_ID)
        );
    }

    @Test
    void testGetInternalTaskIdWithEmptyImType() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.getInternalTaskId("", IM_GROUP_ID)
        );
    }

    @Test
    void testGetInternalTaskIdWithNullImGroupId() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.getInternalTaskId(IM_TYPE_FEISHU, null)
        );
    }

    @Test
    void testGetInternalTaskIdWithEmptyImGroupId() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.getInternalTaskId(IM_TYPE_FEISHU, "  ")
        );
    }

    @Test
    void testGetInternalTaskIdThrowsWhenBindingNotFound() {
        // Execute & Verify
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            sessionManager.getInternalTaskId(IM_TYPE_FEISHU, IM_GROUP_ID)
        );

        assertTrue(exception.getMessage().contains("No binding found"));
        assertTrue(exception.getMessage().contains(IM_TYPE_FEISHU));
        assertTrue(exception.getMessage().contains(IM_GROUP_ID));
    }

    // ==========================================
    // destroySessionCascade Tests
    // ==========================================

    @Test
    void testDestroySessionCascadeDeletesMasterAndWorkers() {
        // Setup - create binding
        when(taskManager.createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC))
            .thenReturn(MASTER_SESSION_ID);
        when(taskManager.sessionExists(MASTER_SESSION_ID)).thenReturn(true);
        sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);

        // Mock worker sessions
        List<TaskSession> workerSessions = new ArrayList<>();
        workerSessions.add(createMockWorkerSession(WORKER_SESSION_ID_1, MASTER_SESSION_ID));
        workerSessions.add(createMockWorkerSession(WORKER_SESSION_ID_2, MASTER_SESSION_ID));
        when(taskSessionRepository.findByParentId(MASTER_SESSION_ID)).thenReturn(workerSessions);

        // Execute
        sessionManager.destroySessionCascade(IM_GROUP_ID);

        // Verify DeleteMessagesEvent calls (2 workers + 1 master = 3)
        verify(dbWriter, times(3)).submitEvent(any(tech.yesboss.persistence.event.DeleteMessagesEvent.class));

        // Verify DeleteTaskSessionEvent calls (2 workers + 1 master = 3)
        verify(dbWriter, times(3)).submitEvent(any(tech.yesboss.persistence.event.DeleteTaskSessionEvent.class));

        // Verify binding was removed
        assertFalse(sessionManager.hasBinding(IM_TYPE_FEISHU, IM_GROUP_ID));
        assertEquals(0, sessionManager.getBindingCount());
    }

    @Test
    void testDestroySessionCascadeWithNullImGroupId() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.destroySessionCascade(null)
        );
    }

    @Test
    void testDestroySessionCascadeWithEmptyImGroupId() {
        assertThrows(IllegalArgumentException.class, () ->
            sessionManager.destroySessionCascade("  ")
        );
    }

    @Test
    void testDestroySessionCascadeWithNonExistentBinding() {
        // Execute - should not throw, just log warning
        assertDoesNotThrow(() -> sessionManager.destroySessionCascade("non-existent-group"));

        // Verify no deletion events were submitted
        verify(dbWriter, never()).submitEvent(any(tech.yesboss.persistence.event.DeleteMessagesEvent.class));
        verify(dbWriter, never()).submitEvent(any(tech.yesboss.persistence.event.DeleteTaskSessionEvent.class));
    }

    @Test
    void testDestroySessionCascadeWithMultipleImTypesSameGroupId() {
        // Setup - create bindings for both FEISHU and SLACK with same group ID
        when(taskManager.createMasterTask(anyString(), eq(IM_GROUP_ID), anyString()))
            .thenReturn(MASTER_SESSION_ID)
            .thenReturn("master-session-slack");
        when(taskManager.sessionExists(anyString())).thenReturn(true);

        sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, "Feishu topic");
        sessionManager.bindOrCreateTaskSession(IM_TYPE_SLACK, IM_GROUP_ID, "Slack topic");

        // Mock worker sessions for both masters
        List<TaskSession> feishuWorkers = List.of(createMockWorkerSession(WORKER_SESSION_ID_1, MASTER_SESSION_ID));
        List<TaskSession> slackWorkers = List.of(createMockWorkerSession(WORKER_SESSION_ID_2, "master-session-slack"));

        when(taskSessionRepository.findByParentId(MASTER_SESSION_ID)).thenReturn(feishuWorkers);
        when(taskSessionRepository.findByParentId("master-session-slack")).thenReturn(slackWorkers);

        // Execute
        sessionManager.destroySessionCascade(IM_GROUP_ID);

        // Verify both bindings were deleted
        assertFalse(sessionManager.hasBinding(IM_TYPE_FEISHU, IM_GROUP_ID));
        assertFalse(sessionManager.hasBinding(IM_TYPE_SLACK, IM_GROUP_ID));
        assertEquals(0, sessionManager.getBindingCount());

        // Verify deletion events for both master sessions and their workers
        verify(dbWriter, times(4)).submitEvent(any(tech.yesboss.persistence.event.DeleteTaskSessionEvent.class));
    }

    @Test
    void testDestroySessionCascadeWithNoWorkers() {
        // Setup - create binding
        when(taskManager.createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC))
            .thenReturn(MASTER_SESSION_ID);
        when(taskManager.sessionExists(MASTER_SESSION_ID)).thenReturn(true);
        sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);

        // Mock no worker sessions
        when(taskSessionRepository.findByParentId(MASTER_SESSION_ID)).thenReturn(List.of());

        // Execute
        sessionManager.destroySessionCascade(IM_GROUP_ID);

        // Verify only master deletion events were submitted
        verify(dbWriter, times(1)).submitEvent(eq(new DeleteMessagesEvent(MASTER_SESSION_ID)));
        verify(dbWriter, times(1)).submitEvent(eq(new DeleteTaskSessionEvent(MASTER_SESSION_ID)));

        // Verify binding was removed
        assertFalse(sessionManager.hasBinding(IM_TYPE_FEISHU, IM_GROUP_ID));
    }

    // ==========================================
    // hasBinding Tests
    // ==========================================

    @Test
    void testHasBindingReturnsTrueWhenBindingExists() {
        // Setup
        when(taskManager.createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC))
            .thenReturn(MASTER_SESSION_ID);
        when(taskManager.sessionExists(MASTER_SESSION_ID)).thenReturn(true);
        sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);

        // Execute & Verify
        assertTrue(sessionManager.hasBinding(IM_TYPE_FEISHU, IM_GROUP_ID));
    }

    @Test
    void testHasBindingReturnsFalseWhenBindingNotExists() {
        assertFalse(sessionManager.hasBinding(IM_TYPE_FEISHU, "non-existent-group"));
    }

    @Test
    void testHasBindingWithNullParameters() {
        // Setup
        when(taskManager.createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC))
            .thenReturn(MASTER_SESSION_ID);
        when(taskManager.sessionExists(MASTER_SESSION_ID)).thenReturn(true);
        sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);

        // Execute & Verify
        assertFalse(sessionManager.hasBinding(null, IM_GROUP_ID));
        assertFalse(sessionManager.hasBinding(IM_TYPE_FEISHU, null));
        assertFalse(sessionManager.hasBinding(null, null));
    }

    // ==========================================
    // getBindingCount Tests
    // ==========================================

    @Test
    void testGetBindingCountReturnsZeroWhenNoBindings() {
        assertEquals(0, sessionManager.getBindingCount());
    }

    @Test
    void testGetBindingCountReturnsCorrectCount() {
        // Setup
        when(taskManager.createMasterTask(anyString(), anyString(), anyString()))
            .thenReturn(MASTER_SESSION_ID)
            .thenReturn("master-session-2")
            .thenReturn("master-session-3");
        when(taskManager.sessionExists(anyString())).thenReturn(true);

        // Create three bindings
        sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, "group-1", "Topic 1");
        sessionManager.bindOrCreateTaskSession(IM_TYPE_SLACK, "group-2", "Topic 2");
        sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, "group-3", "Topic 3");

        // Execute & Verify
        assertEquals(3, sessionManager.getBindingCount());
    }

    @Test
    void testGetBindingCountDecreasesAfterCascadeDeletion() {
        // Setup - create binding
        when(taskManager.createMasterTask(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC))
            .thenReturn(MASTER_SESSION_ID);
        when(taskManager.sessionExists(MASTER_SESSION_ID)).thenReturn(true);
        sessionManager.bindOrCreateTaskSession(IM_TYPE_FEISHU, IM_GROUP_ID, TOPIC);

        assertEquals(1, sessionManager.getBindingCount());

        // Mock worker sessions
        when(taskSessionRepository.findByParentId(MASTER_SESSION_ID)).thenReturn(List.of());

        // Execute cascade deletion
        sessionManager.destroySessionCascade(IM_GROUP_ID);

        // Verify
        assertEquals(0, sessionManager.getBindingCount());
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * Create a mock Worker TaskSession
     */
    private TaskSession createMockWorkerSession(String sessionId, String parentId) {
        return new TaskSession(
            sessionId,
            parentId,
            TaskSession.ImType.FEISHU,
            IM_GROUP_ID,
            TaskSession.Role.WORKER,
            TaskSession.Status.RUNNING,
            "Worker task",
            null,
            null,
            System.currentTimeMillis(),
            System.currentTimeMillis()
        );
    }
}
