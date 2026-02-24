package tech.yesboss.safeguard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.safeguard.impl.CircuitBreakerImpl;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CircuitBreaker loop counting and exception handling.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>checkAndIncrement can be called up to threshold-1 times without exception</li>
 *   <li>checkAndIncrement throws CircuitBreakerOpenException on the threshold+1 call</li>
 *   <li>reset clears the counter and allows counting to start over</li>
 *   <li>getCurrentCount returns the correct count</li>
 *   <li>threshold can be configured</li>
 * </ul>
 */
@DisplayName("CircuitBreaker Tests")
class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreakerImpl();
    }

    @Test
    @DisplayName("Constructor with default threshold should use DEFAULT_THRESHOLD")
    void testConstructorWithDefaultThreshold() {
        assertEquals(CircuitBreaker.DEFAULT_THRESHOLD, circuitBreaker.getThreshold());
        assertEquals(20, circuitBreaker.getThreshold());
    }

    @Test
    @DisplayName("Constructor with custom threshold should set threshold")
    void testConstructorWithCustomThreshold() {
        CircuitBreaker customBreaker = new CircuitBreakerImpl(15);
        assertEquals(15, customBreaker.getThreshold());
    }

    @Test
    @DisplayName("Constructor with negative threshold should throw exception")
    void testConstructorWithNegativeThreshold() {
        assertThrows(IllegalArgumentException.class,
                () -> new CircuitBreakerImpl(-1),
                "Should throw exception for negative threshold");
    }

    @Test
    @DisplayName("Constructor with zero threshold should throw exception")
    void testConstructorWithZeroThreshold() {
        assertThrows(IllegalArgumentException.class,
                () -> new CircuitBreakerImpl(0),
                "Should throw exception for zero threshold");
    }

    @Test
    @DisplayName("checkAndIncrement 19 times should not throw exception")
    void testCheckAndIncrement19TimesNoException() {
        String sessionId = "session_19";

        // Should not throw exception for 19 iterations
        for (int i = 0; i < 19; i++) {
            assertDoesNotThrow(() -> circuitBreaker.checkAndIncrement(sessionId));
        }

        assertEquals(19, circuitBreaker.getCurrentCount(sessionId));
    }

    @Test
    @DisplayName("checkAndIncrement 20 times should throw exception on 20th call")
    void testCheckAndIncrement20TimesThrowsException() {
        String sessionId = "session_20";

        // First 19 calls should succeed
        for (int i = 0; i < 19; i++) {
            circuitBreaker.checkAndIncrement(sessionId);
        }

        // 20th call should throw exception
        CircuitBreakerOpenException ex = assertThrows(
                CircuitBreakerOpenException.class,
                () -> circuitBreaker.checkAndIncrement(sessionId),
                "Should throw CircuitBreakerOpenException on 20th call"
        );

        assertEquals(sessionId, ex.getSessionId());
        assertEquals(20, ex.getCurrentCount());
        assertEquals(20, ex.getThreshold());
    }

    @Test
    @DisplayName("checkAndIncrement should throw exception on threshold call")
    void testCheckAndIncrementThrowsOnThreshold() {
        CircuitBreaker customBreaker = new CircuitBreakerImpl(5);
        String sessionId = "session_threshold";

        // First 4 calls should succeed
        for (int i = 0; i < 4; i++) {
            customBreaker.checkAndIncrement(sessionId);
        }

        // 5th call should throw exception (at threshold)
        CircuitBreakerOpenException ex = assertThrows(
                CircuitBreakerOpenException.class,
                () -> customBreaker.checkAndIncrement(sessionId)
        );

        assertEquals(5, ex.getCurrentCount());
        assertEquals(5, ex.getThreshold());
    }

    @Test
    @DisplayName("reset should clear the counter and allow counting to start over")
    void testResetClearsCounter() {
        String sessionId = "session_reset";

        // Count to 10
        for (int i = 0; i < 10; i++) {
            circuitBreaker.checkAndIncrement(sessionId);
        }
        assertEquals(10, circuitBreaker.getCurrentCount(sessionId));

        // Reset
        circuitBreaker.reset(sessionId);

        // Counter should be back to 0
        assertEquals(0, circuitBreaker.getCurrentCount(sessionId));

        // Should be able to count again
        for (int i = 0; i < 19; i++) {
            circuitBreaker.checkAndIncrement(sessionId);
        }
        assertEquals(19, circuitBreaker.getCurrentCount(sessionId));
    }

    @Test
    @DisplayName("reset of non-existent session should not throw exception")
    void testResetNonExistentSession() {
        assertDoesNotThrow(() -> circuitBreaker.reset("nonexistent"));
    }

    @Test
    @DisplayName("getCurrentCount should return 0 for non-existent session")
    void testGetCurrentCountForNonExistentSession() {
        assertEquals(0, circuitBreaker.getCurrentCount("nonexistent"));
    }

    @Test
    @DisplayName("getCurrentCount should return correct count")
    void testGetCurrentCount() {
        String sessionId = "session_count";

        assertEquals(0, circuitBreaker.getCurrentCount(sessionId));

        circuitBreaker.checkAndIncrement(sessionId);
        assertEquals(1, circuitBreaker.getCurrentCount(sessionId));

        circuitBreaker.checkAndIncrement(sessionId);
        circuitBreaker.checkAndIncrement(sessionId);
        assertEquals(3, circuitBreaker.getCurrentCount(sessionId));
    }

    @Test
    @DisplayName("checkAndIncrement with null sessionId should throw exception")
    void testCheckAndIncrementWithNullSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> circuitBreaker.checkAndIncrement(null),
                "Should throw exception for null sessionId");
    }

    @Test
    @DisplayName("checkAndIncrement with empty sessionId should throw exception")
    void testCheckAndIncrementWithEmptySessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> circuitBreaker.checkAndIncrement(""),
                "Should throw exception for empty sessionId");
    }

    @Test
    @DisplayName("reset with null sessionId should throw exception")
    void testResetWithNullSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> circuitBreaker.reset(null),
                "Should throw exception for null sessionId");
    }

    @Test
    @DisplayName("reset with empty sessionId should throw exception")
    void testResetWithEmptySessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> circuitBreaker.reset(""),
                "Should throw exception for empty sessionId");
    }

    @Test
    @DisplayName("getCurrentCount with null sessionId should throw exception")
    void testGetCurrentCountWithNullSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> circuitBreaker.getCurrentCount(null),
                "Should throw exception for null sessionId");
    }

    @Test
    @DisplayName("getCurrentCount with empty sessionId should throw exception")
    void testGetCurrentCountWithEmptySessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> circuitBreaker.getCurrentCount(""),
                "Should throw exception for empty sessionId");
    }

    @Test
    @DisplayName("setThreshold should update the threshold")
    void testSetThreshold() {
        circuitBreaker.setThreshold(10);
        assertEquals(10, circuitBreaker.getThreshold());
    }

    @Test
    @DisplayName("setThreshold with negative value should throw exception")
    void testSetThresholdWithNegativeValue() {
        assertThrows(IllegalArgumentException.class,
                () -> circuitBreaker.setThreshold(-1),
                "Should throw exception for negative threshold");
    }

    @Test
    @DisplayName("setThreshold with zero should throw exception")
    void testSetThresholdWithZero() {
        assertThrows(IllegalArgumentException.class,
                () -> circuitBreaker.setThreshold(0),
                "Should throw exception for zero threshold");
    }

    @Test
    @DisplayName("Multiple sessions should have independent counters")
    void testMultipleSessionsIndependentCounters() {
        String session1 = "session_1";
        String session2 = "session_2";

        // Count session1 to 10
        for (int i = 0; i < 10; i++) {
            circuitBreaker.checkAndIncrement(session1);
        }

        // Count session2 to 5
        for (int i = 0; i < 5; i++) {
            circuitBreaker.checkAndIncrement(session2);
        }

        assertEquals(10, circuitBreaker.getCurrentCount(session1));
        assertEquals(5, circuitBreaker.getCurrentCount(session2));
    }

    @Test
    @DisplayName("After exception, counter should be removed")
    void testCounterRemovedAfterException() {
        String sessionId = "session_remove";
        CircuitBreakerImpl impl = new CircuitBreakerImpl(5);

        // Count to threshold-1, then threshold call triggers exception
        for (int i = 0; i < 4; i++) {
            impl.checkAndIncrement(sessionId);
        }

        assertThrows(CircuitBreakerOpenException.class,
                () -> impl.checkAndIncrement(sessionId));

        // Counter should be removed
        assertEquals(0, impl.getCurrentCount(sessionId));
        assertEquals(0, impl.getActiveCounterCount());
    }

    @Test
    @DisplayName("clearAll should remove all counters")
    void testClearAll() {
        CircuitBreakerImpl impl = new CircuitBreakerImpl();

        impl.checkAndIncrement("session1");
        impl.checkAndIncrement("session2");
        impl.checkAndIncrement("session3");

        assertEquals(3, impl.getActiveCounterCount());

        impl.clearAll();

        assertEquals(0, impl.getActiveCounterCount());
        assertEquals(0, impl.getCurrentCount("session1"));
        assertEquals(0, impl.getCurrentCount("session2"));
        assertEquals(0, impl.getCurrentCount("session3"));
    }

    @Test
    @DisplayName("CircuitBreakerOpenException should contain correct information")
    void testCircuitBreakerOpenExceptionContent() {
        CircuitBreakerImpl impl = new CircuitBreakerImpl(3);
        String sessionId = "session_exception";

        // Count to threshold-1, then threshold call triggers exception
        for (int i = 0; i < 2; i++) {
            impl.checkAndIncrement(sessionId);
        }

        CircuitBreakerOpenException ex = assertThrows(
                CircuitBreakerOpenException.class,
                () -> impl.checkAndIncrement(sessionId)
        );

        assertEquals(sessionId, ex.getSessionId());
        assertEquals(3, ex.getCurrentCount());
        assertEquals(3, ex.getThreshold());
        assertTrue(ex.getMessage().contains(sessionId));
        assertTrue(ex.toString().contains("CircuitBreakerOpenException"));
    }
}
