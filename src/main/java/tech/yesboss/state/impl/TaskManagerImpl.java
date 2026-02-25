package tech.yesboss.state.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.persistence.entity.TaskSession;
import tech.yesboss.persistence.entity.TaskSession.ImType;
import tech.yesboss.persistence.entity.TaskSession.Role;
import tech.yesboss.persistence.entity.TaskSession.Status;
import tech.yesboss.persistence.repository.TaskSessionRepository;
import tech.yesboss.state.TaskManager;
import tech.yesboss.state.model.ImRoute;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务中枢管理器实现 (Task Manager Implementation)
 *
 * <p>负责维护任务的状态机流转，并管理父子任务的生命周期。</p>
 */
public class TaskManagerImpl implements TaskManager {

    private static final Logger logger = LoggerFactory.getLogger(TaskManagerImpl.class);

    // Valid state transitions: fromState -> Set<toStates>
    private static final ConcurrentHashMap<Status, Set<Status>> VALID_TRANSITIONS = new ConcurrentHashMap<>();

    static {
        // PLANNING can transition to RUNNING or FAILED
        VALID_TRANSITIONS.put(Status.PLANNING, Set.of(Status.RUNNING, Status.FAILED));
        // RUNNING can transition to SUSPENDED, COMPLETED, or FAILED
        VALID_TRANSITIONS.put(Status.RUNNING, Set.of(Status.SUSPENDED, Status.COMPLETED, Status.FAILED));
        // SUSPENDED can transition to RUNNING (resume) or FAILED
        VALID_TRANSITIONS.put(Status.SUSPENDED, Set.of(Status.RUNNING, Status.FAILED));
        // COMPLETED and FAILED are terminal states (no outgoing transitions)
        VALID_TRANSITIONS.put(Status.COMPLETED, Set.of());
        VALID_TRANSITIONS.put(Status.FAILED, Set.of());
    }

    private final TaskSessionRepository repository;

    // In-memory cache of recently created session IDs (to handle async DB writes)
    private final Set<String> sessionCache = ConcurrentHashMap.newKeySet();

    /**
     * Create a new TaskManagerImpl.
     *
     * @param repository The task session repository
     * @throws IllegalArgumentException if repository is null
     */
    public TaskManagerImpl(TaskSessionRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("TaskSessionRepository cannot be null");
        }
        this.repository = repository;
        logger.info("TaskManager initialized");
    }

    @Override
    public String createMasterTask(String imType, String imGroupId, String topic) {
        if (imType == null || imType.trim().isEmpty()) {
            throw new IllegalArgumentException("imType cannot be null or empty");
        }
        if (imGroupId == null || imGroupId.trim().isEmpty()) {
            throw new IllegalArgumentException("imGroupId cannot be null or empty");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("topic cannot be null or empty");
        }

        // Generate unique session ID
        String sessionId = generateSessionId();

        // Parse imType
        ImType imTypeEnum;
        try {
            imTypeEnum = ImType.valueOf(imType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid imType: " + imType +
                    ". Valid values are: FEISHU, SLACK, CLI");
        }

        // Create master session (no parent, role is MASTER, initial status is PLANNING)
        repository.saveSession(
                sessionId,
                null,  // parentId is null for Master
                imTypeEnum,
                imGroupId,
                Role.MASTER,
                Status.PLANNING,
                topic,
                null   // assignedTask is null for Master
        );

        // Add to in-memory cache immediately (since DB write is async)
        sessionCache.add(sessionId);

        logger.info("Created Master task: sessionId={}, imType={}, imGroupId={}, topic={}",
                sessionId, imType, imGroupId, topic);

        return sessionId;
    }

    @Override
    public String createWorkerTask(String parentSessionId, String assignedTask) {
        if (parentSessionId == null || parentSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("parentSessionId cannot be null or empty");
        }
        if (assignedTask == null || assignedTask.trim().isEmpty()) {
            throw new IllegalArgumentException("assignedTask cannot be null or empty");
        }

        // Fetch parent session
        Optional<TaskSession> parentOpt = repository.findById(parentSessionId);
        if (parentOpt.isEmpty()) {
            throw new IllegalStateException("Parent session not found: " + parentSessionId);
        }

        TaskSession parent = parentOpt.get();
        if (parent.role() != Role.MASTER) {
            throw new IllegalStateException("Parent session must be a MASTER, but was: " + parent.role());
        }

        // Generate unique session ID
        String sessionId = generateSessionId();

        // Create worker session (inherit IM route from parent)
        repository.saveSession(
                sessionId,
                parentSessionId,  // link to parent
                parent.imType(),  // inherit imType
                parent.imGroupId(), // inherit imGroupId
                Role.WORKER,
                Status.RUNNING,   // Workers start in RUNNING state
                null,  // topic is derived from assignedTask
                assignedTask
        );

        // Add to in-memory cache immediately (since DB write is async)
        sessionCache.add(sessionId);

        logger.info("Created Worker task: sessionId={}, parentSessionId={}, assignedTask={}",
                sessionId, parentSessionId, assignedTask);

        return sessionId;
    }

    @Override
    public void transitionState(String sessionId, Status newStatus) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus cannot be null");
        }

        // Fetch current session
        Optional<TaskSession> sessionOpt = repository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }

        TaskSession session = sessionOpt.get();
        Status currentStatus = session.status();

        // Check if transition is valid
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid state transition from %s to %s for session %s",
                            currentStatus, newStatus, sessionId)
            );
        }

        // Update status in repository
        repository.updateStatus(sessionId, newStatus, null);

        logger.info("Transitioned session {} from {} to {}", sessionId, currentStatus, newStatus);
    }

    @Override
    public Status getStatus(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }

        Optional<TaskSession> sessionOpt = repository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }

        return sessionOpt.get().status();
    }

    @Override
    public String getParentSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }

        Optional<TaskSession> sessionOpt = repository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }

        return sessionOpt.get().parentId();
    }

    @Override
    public ImRoute getImRoute(String masterSessionId) {
        if (masterSessionId == null || masterSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("masterSessionId cannot be null or empty");
        }

        Optional<TaskSession> sessionOpt = repository.findById(masterSessionId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalStateException("Master session not found: " + masterSessionId);
        }

        TaskSession session = sessionOpt.get();
        if (session.role() != Role.MASTER) {
            throw new IllegalStateException("Session is not a MASTER: " + masterSessionId);
        }

        return new ImRoute(session.imType().name(), session.imGroupId());
    }

    @Override
    public boolean sessionExists(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }

        // Check in-memory cache first (for newly created sessions that haven't been written to DB yet)
        if (sessionCache.contains(sessionId)) {
            return true;
        }

        return repository.findById(sessionId).isPresent();
    }

    @Override
    public boolean isMasterSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }

        Optional<TaskSession> sessionOpt = repository.findById(sessionId);
        return sessionOpt.isPresent() && sessionOpt.get().role() == Role.MASTER;
    }

    @Override
    public boolean isWorkerSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }

        Optional<TaskSession> sessionOpt = repository.findById(sessionId);
        return sessionOpt.isPresent() && sessionOpt.get().role() == Role.WORKER;
    }

    /**
     * Check if a state transition is valid.
     *
     * @param fromState The current state
     * @param toState   The target state
     * @return true if the transition is valid, false otherwise
     */
    private boolean isValidTransition(Status fromState, Status toState) {
        if (fromState == toState) {
            // Same state is always valid (no-op)
            return true;
        }

        Set<Status> validNextStates = VALID_TRANSITIONS.get(fromState);
        return validNextStates != null && validNextStates.contains(toState);
    }

    /**
     * Generate a unique session ID using UUID.
     *
     * @return A unique session ID
     */
    private String generateSessionId() {
        return "session_" + UUID.randomUUID();
    }
}
