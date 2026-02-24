package tech.yesboss.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.persistence.entity.TaskSession;
import tech.yesboss.persistence.entity.TaskSession.ImType;
import tech.yesboss.persistence.entity.TaskSession.Role;
import tech.yesboss.persistence.entity.TaskSession.Status;
import tech.yesboss.persistence.repository.TaskSessionRepository;
import tech.yesboss.state.impl.TaskManagerImpl;
import tech.yesboss.state.model.ImRoute;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for TaskManager state transitions and session creation.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>createMasterTask generates correct session ID and triggers repository save</li>
 *   <li>createWorkerTask links parent ID properly</li>
 *   <li>transitionState enforces valid state flows and updates repository</li>
 *   <li>getParentSessionId correctly distinguishes Master and Worker sessions</li>
 * </ul>
 */
@DisplayName("TaskManager Tests")
class TaskManagerTest {

    private TaskSessionRepository mockRepository;
    private TaskManager taskManager;

    @BeforeEach
    void setUp() {
        mockRepository = mock(TaskSessionRepository.class);
        taskManager = new TaskManagerImpl(mockRepository);
    }

    @Test
    @DisplayName("Constructor should throw exception for null repository")
    void testConstructorWithNullRepository() {
        assertThrows(IllegalArgumentException.class,
                () -> new TaskManagerImpl(null),
                "Should throw exception for null repository");
    }

    @Test
    @DisplayName("createMasterTask should generate unique session ID and save to repository")
    void testCreateMasterTaskGeneratesSessionId() {
        // Arrange
        String imType = "FEISHU";
        String imGroupId = "group_123";
        String topic = "Test task";

        // Act
        String sessionId = taskManager.createMasterTask(imType, imGroupId, topic);

        // Assert
        assertNotNull(sessionId);
        assertTrue(sessionId.startsWith("session_"));
        verify(mockRepository).saveSession(
                eq(sessionId),
                eq(null),
                eq(ImType.FEISHU),
                eq(imGroupId),
                eq(Role.MASTER),
                eq(Status.PLANNING),
                eq(topic),
                eq(null)
        );
    }

    @Test
    @DisplayName("createMasterTask should throw exception for null imType")
    void testCreateMasterTaskWithNullImType() {
        assertThrows(IllegalArgumentException.class,
                () -> taskManager.createMasterTask(null, "group", "topic"),
                "Should throw exception for null imType");
    }

    @Test
    @DisplayName("createMasterTask should throw exception for empty imType")
    void testCreateMasterTaskWithEmptyImType() {
        assertThrows(IllegalArgumentException.class,
                () -> taskManager.createMasterTask("", "group", "topic"),
                "Should throw exception for empty imType");
    }

    @Test
    @DisplayName("createMasterTask should throw exception for invalid imType")
    void testCreateMasterTaskWithInvalidImType() {
        assertThrows(IllegalArgumentException.class,
                () -> taskManager.createMasterTask("INVALID_TYPE", "group", "topic"),
                "Should throw exception for invalid imType");
    }

    @Test
    @DisplayName("createMasterTask should support all valid imType values")
    void testCreateMasterTaskWithValidImTypes() {
        // Test FEISHU
        String sessionId1 = taskManager.createMasterTask("FEISHU", "group1", "topic1");
        assertNotNull(sessionId1);

        // Test SLACK
        String sessionId2 = taskManager.createMasterTask("SLACK", "group2", "topic2");
        assertNotNull(sessionId2);

        // Test CLI
        String sessionId3 = taskManager.createMasterTask("CLI", "group3", "topic3");
        assertNotNull(sessionId3);

        // Test lowercase (should be case-insensitive)
        String sessionId4 = taskManager.createMasterTask("feishu", "group4", "topic4");
        assertNotNull(sessionId4);

        verify(mockRepository, times(4)).saveSession(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("createWorkerTask should link to parent and inherit IM route")
    void testCreateWorkerTaskLinksParent() {
        // Arrange
        String parentSessionId = "parent_session";
        String assignedTask = "Implement feature X";

        TaskSession parentSession = new TaskSession(
                parentSessionId,
                null,
                ImType.SLACK,
                "slack_group_1",
                Role.MASTER,
                Status.RUNNING,
                "Parent task",
                null,  // executionPlan
                null,  // assignedTask
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        when(mockRepository.findById(parentSessionId)).thenReturn(Optional.of(parentSession));

        // Act
        String workerSessionId = taskManager.createWorkerTask(parentSessionId, assignedTask);

        // Assert
        assertNotNull(workerSessionId);
        assertTrue(workerSessionId.startsWith("session_"));

        verify(mockRepository).saveSession(
                eq(workerSessionId),
                eq(parentSessionId),
                eq(ImType.SLACK),
                eq("slack_group_1"),
                eq(Role.WORKER),
                eq(Status.RUNNING),
                eq(null),
                eq(assignedTask)
        );
    }

    @Test
    @DisplayName("createWorkerTask should throw exception if parent not found")
    void testCreateWorkerTaskWithNonExistentParent() {
        when(mockRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> taskManager.createWorkerTask("nonexistent", "task"),
                "Should throw exception if parent not found");
    }

    @Test
    @DisplayName("createWorkerTask should throw exception if parent is not a Master")
    void testCreateWorkerTaskWithNonMasterParent() {
        String parentSessionId = "worker_parent";
        String assignedTask = "Subtask";

        TaskSession workerParent = new TaskSession(
                parentSessionId,
                "grandparent",
                ImType.FEISHU,
                "group_1",
                Role.WORKER,
                Status.RUNNING,
                "Worker task",
                null,  // executionPlan
                "Assigned task",
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        when(mockRepository.findById(parentSessionId)).thenReturn(Optional.of(workerParent));

        assertThrows(IllegalStateException.class,
                () -> taskManager.createWorkerTask(parentSessionId, assignedTask),
                "Should throw exception if parent is not a MASTER");
    }

    @Test
    @DisplayName("createWorkerTask should throw exception for null parentSessionId")
    void testCreateWorkerTaskWithNullParentSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> taskManager.createWorkerTask(null, "task"),
                "Should throw exception for null parentSessionId");
    }

    @Test
    @DisplayName("createWorkerTask should throw exception for null assignedTask")
    void testCreateWorkerTaskWithNullAssignedTask() {
        assertThrows(IllegalArgumentException.class,
                () -> taskManager.createWorkerTask("parent", null),
                "Should throw exception for null assignedTask");
    }

    @Test
    @DisplayName("transitionState should allow PLANNING to RUNNING")
    void testTransitionStatePlanningToRunning() {
        String sessionId = "session_1";
        TaskSession session = createMockSession(sessionId, Status.PLANNING);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        taskManager.transitionState(sessionId, Status.RUNNING);

        verify(mockRepository).updateStatus(sessionId, Status.RUNNING, null);
    }

    @Test
    @DisplayName("transitionState should allow RUNNING to SUSPENDED")
    void testTransitionStateRunningToSuspended() {
        String sessionId = "session_2";
        TaskSession session = createMockSession(sessionId, Status.RUNNING);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        taskManager.transitionState(sessionId, Status.SUSPENDED);

        verify(mockRepository).updateStatus(sessionId, Status.SUSPENDED, null);
    }

    @Test
    @DisplayName("transitionState should allow SUSPENDED to RUNNING (resume)")
    void testTransitionStateSuspendedToRunning() {
        String sessionId = "session_3";
        TaskSession session = createMockSession(sessionId, Status.SUSPENDED);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        taskManager.transitionState(sessionId, Status.RUNNING);

        verify(mockRepository).updateStatus(sessionId, Status.RUNNING, null);
    }

    @Test
    @DisplayName("transitionState should allow RUNNING to COMPLETED")
    void testTransitionStateRunningToCompleted() {
        String sessionId = "session_4";
        TaskSession session = createMockSession(sessionId, Status.RUNNING);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        taskManager.transitionState(sessionId, Status.COMPLETED);

        verify(mockRepository).updateStatus(sessionId, Status.COMPLETED, null);
    }

    @Test
    @DisplayName("transitionState should allow RUNNING to FAILED")
    void testTransitionStateRunningToFailed() {
        String sessionId = "session_5";
        TaskSession session = createMockSession(sessionId, Status.RUNNING);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        taskManager.transitionState(sessionId, Status.FAILED);

        verify(mockRepository).updateStatus(sessionId, Status.FAILED, null);
    }

    @Test
    @DisplayName("transitionState should reject invalid transition (FAILED to RUNNING)")
    void testTransitionStateFailedToRunning() {
        String sessionId = "session_6";
        TaskSession session = createMockSession(sessionId, Status.FAILED);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class,
                () -> taskManager.transitionState(sessionId, Status.RUNNING),
                "Should reject transition from FAILED to RUNNING");

        verify(mockRepository, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("transitionState should reject invalid transition (COMPLETED to RUNNING)")
    void testTransitionStateCompletedToRunning() {
        String sessionId = "session_7";
        TaskSession session = createMockSession(sessionId, Status.COMPLETED);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class,
                () -> taskManager.transitionState(sessionId, Status.RUNNING),
                "Should reject transition from COMPLETED to RUNNING");

        verify(mockRepository, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("transitionState should reject invalid transition (PLANNING to SUSPENDED)")
    void testTransitionStatePlanningToSuspended() {
        String sessionId = "session_8";
        TaskSession session = createMockSession(sessionId, Status.PLANNING);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class,
                () -> taskManager.transitionState(sessionId, Status.SUSPENDED),
                "Should reject transition from PLANNING to SUSPENDED");

        verify(mockRepository, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("transitionState should throw exception for non-existent session")
    void testTransitionStateWithNonExistentSession() {
        when(mockRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> taskManager.transitionState("nonexistent", Status.RUNNING),
                "Should throw exception for non-existent session");
    }

    @Test
    @DisplayName("getStatus should return session status")
    void testGetStatus() {
        String sessionId = "session_status";
        TaskSession session = createMockSession(sessionId, Status.RUNNING);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        Status status = taskManager.getStatus(sessionId);

        assertEquals(Status.RUNNING, status);
    }

    @Test
    @DisplayName("getParentSessionId should return parent ID for Worker")
    void testGetParentSessionIdForWorker() {
        String sessionId = "worker_session";
        String parentId = "master_session";
        TaskSession session = createMockSession(sessionId, Status.RUNNING, parentId);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        String result = taskManager.getParentSessionId(sessionId);

        assertEquals(parentId, result);
    }

    @Test
    @DisplayName("getParentSessionId should return null for Master")
    void testGetParentSessionIdForMaster() {
        String sessionId = "master_session";
        TaskSession session = createMockSession(sessionId, Status.RUNNING, null);

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        String result = taskManager.getParentSessionId(sessionId);

        assertNull(result);
    }

    @Test
    @DisplayName("getImRoute should return ImRoute for Master session")
    void testGetImRoute() {
        String sessionId = "master_session";
        TaskSession session = new TaskSession(
                sessionId,
                null,
                ImType.FEISHU,
                "feishu_group_123",
                Role.MASTER,
                Status.RUNNING,
                "Test task",
                null,  // executionPlan
                null,  // assignedTask
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        ImRoute route = taskManager.getImRoute(sessionId);

        assertEquals("FEISHU", route.imType());
        assertEquals("feishu_group_123", route.imGroupId());
    }

    @Test
    @DisplayName("getImRoute should throw exception if session is not a Master")
    void testGetImRouteForWorkerSession() {
        String sessionId = "worker_session";
        TaskSession session = new TaskSession(
                sessionId,
                "master",
                ImType.FEISHU,
                "feishu_group_123",
                Role.WORKER,
                Status.RUNNING,
                "Test task",
                null,  // executionPlan
                "Assigned task",
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class,
                () -> taskManager.getImRoute(sessionId),
                "Should throw exception for Worker session");
    }

    @Test
    @DisplayName("sessionExists should return true for existing session")
    void testSessionExists() {
        String sessionId = "existing_session";
        when(mockRepository.findById(sessionId))
                .thenReturn(Optional.of(createMockSession(sessionId, Status.RUNNING)));

        assertTrue(taskManager.sessionExists(sessionId));
    }

    @Test
    @DisplayName("sessionExists should return false for non-existent session")
    void testSessionNotExists() {
        when(mockRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertFalse(taskManager.sessionExists("nonexistent"));
    }

    @Test
    @DisplayName("isMasterSession should return true for Master session")
    void testIsMasterSession() {
        String sessionId = "master_session";
        TaskSession session = new TaskSession(
                sessionId,
                null,
                ImType.SLACK,
                "group_1",
                Role.MASTER,
                Status.RUNNING,
                "Test",
                null,  // executionPlan
                null,  // assignedTask
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertTrue(taskManager.isMasterSession(sessionId));
    }

    @Test
    @DisplayName("isWorkerSession should return true for Worker session")
    void testIsWorkerSession() {
        String sessionId = "worker_session";
        TaskSession session = new TaskSession(
                sessionId,
                "master",
                ImType.SLACK,
                "group_1",
                Role.WORKER,
                Status.RUNNING,
                "Test",
                null,  // executionPlan
                "Assigned task",
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        when(mockRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertTrue(taskManager.isWorkerSession(sessionId));
    }

    /**
     * Helper method to create a mock TaskSession.
     */
    private TaskSession createMockSession(String sessionId, Status status) {
        return createMockSession(sessionId, status, null);
    }

    /**
     * Helper method to create a mock TaskSession with parent.
     */
    private TaskSession createMockSession(String sessionId, Status status, String parentId) {
        return new TaskSession(
                sessionId,
                parentId,
                ImType.FEISHU,
                "group_test",
                Role.MASTER,
                status,
                "Test task",
                null,  // executionPlan
                null,  // assignedTask
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
    }
}
