package tech.yesboss.tool.filesystem.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 操作频率限制器测试
 */
class OperationRateLimiterTest {

    private OperationRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // 创建一个限制较小的限制器用于测试
        rateLimiter = new OperationRateLimiter(10, 1000); // 每秒10次
    }

    @Test
    @DisplayName("默认构造函数应该创建有效的限制器")
    void testDefaultConstructor() {
        OperationRateLimiter limiter = new OperationRateLimiter();
        assertNotNull(limiter);

        // 应该允许操作
        assertTrue(limiter.tryAcquire("read_file", "/test/path"));
    }

    @Test
    @DisplayName("应该允许在限制内的操作")
    void testAllowsOperationsWithinLimit() {
        // read_file 的限制是 200 次/分钟
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.tryAcquire("read_file", "/test/path" + i),
                    "Operation " + i + " should be allowed");
        }
    }

    @Test
    @DisplayName("应该拒绝超过限制的操作")
    void testRejectsOperationsOverLimit() {
        // 创建一个限制很小的限制器
        // 使用没有预定义限制的操作类型，这样会使用全局限制
        OperationRateLimiter smallLimiter = new OperationRateLimiter(3, 60000);

        // 前3次应该成功 (使用 custom_op，没有在 OPERATION_LIMITS 中定义)
        assertTrue(smallLimiter.tryAcquire("custom_op", "/path1"));
        assertTrue(smallLimiter.tryAcquire("custom_op", "/path2"));
        assertTrue(smallLimiter.tryAcquire("custom_op", "/path3"));

        // 第4次应该被拒绝
        assertFalse(smallLimiter.tryAcquire("custom_op", "/path4"),
                "Operation over limit should be rejected");
    }

    @Test
    @DisplayName("不同操作类型应该有独立的限制")
    void testDifferentOperationsHaveIndependentLimits() {
        OperationRateLimiter smallLimiter = new OperationRateLimiter(2, 60000);

        // 用完 custom_op_a 的限制（使用没有预定义限制的操作）
        assertTrue(smallLimiter.tryAcquire("custom_op_a", "/path1"));
        assertTrue(smallLimiter.tryAcquire("custom_op_a", "/path2"));
        assertFalse(smallLimiter.tryAcquire("custom_op_a", "/path3"));

        // custom_op_b 应该仍然可用（独立的计数器）
        assertTrue(smallLimiter.tryAcquire("custom_op_b", "/path1"));
    }

    @Test
    @DisplayName("isAllowed 应该正确检查操作状态")
    void testIsAllowed() {
        OperationRateLimiter smallLimiter = new OperationRateLimiter(2, 60000);

        // 使用没有预定义限制的操作
        assertTrue(smallLimiter.isAllowed("custom_op"));

        smallLimiter.tryAcquire("custom_op", "/path1");
        smallLimiter.tryAcquire("custom_op", "/path2");

        // 达到限制后应该返回 false
        assertFalse(smallLimiter.isAllowed("custom_op"));
    }

    @Test
    @DisplayName("getStats 应该返回正确的统计信息")
    void testGetStats() {
        rateLimiter.tryAcquire("read_file", "/path1");
        rateLimiter.tryAcquire("read_file", "/path2");
        rateLimiter.tryAcquire("read_file", "/path3");

        OperationRateLimiter.OperationStats stats = rateLimiter.getStats("read_file");

        assertNotNull(stats);
        assertEquals("read_file", stats.operation());
        assertTrue(stats.currentCount() >= 3);
    }

    @Test
    @DisplayName("reset 应该重置指定操作的计数器")
    void testReset() {
        OperationRateLimiter smallLimiter = new OperationRateLimiter(2, 60000);

        // 使用没有预定义限制的操作
        smallLimiter.tryAcquire("custom_op", "/path1");
        smallLimiter.tryAcquire("custom_op", "/path2");
        assertFalse(smallLimiter.tryAcquire("custom_op", "/path3"));

        // 重置后应该可以继续操作
        smallLimiter.reset("custom_op");
        assertTrue(smallLimiter.tryAcquire("custom_op", "/path4"));
    }

    @Test
    @DisplayName("resetAll 应该重置所有计数器")
    void testResetAll() {
        OperationRateLimiter smallLimiter = new OperationRateLimiter(2, 60000);

        // 使用没有预定义限制的操作
        smallLimiter.tryAcquire("custom_op_a", "/path1");
        smallLimiter.tryAcquire("custom_op_a", "/path2");
        smallLimiter.tryAcquire("custom_op_b", "/path1");
        smallLimiter.tryAcquire("custom_op_b", "/path2");

        smallLimiter.resetAll();

        // 所有操作应该可以继续
        assertTrue(smallLimiter.tryAcquire("custom_op_a", "/path3"));
        assertTrue(smallLimiter.tryAcquire("custom_op_b", "/path3"));
    }

    @Test
    @DisplayName("getOperationLimits 应该返回操作限制配置")
    void testGetOperationLimits() {
        var limits = OperationRateLimiter.getOperationLimits();

        assertNotNull(limits);
        assertTrue(limits.containsKey("read_file"));
        assertTrue(limits.containsKey("write_file"));
        assertTrue(limits.containsKey("delete_file"));

        // delete_file 应该比 read_file 更严格
        assertTrue(limits.get("delete_file") < limits.get("read_file"));
    }

    @Test
    @DisplayName("OperationStats 应该正确计算使用百分比")
    void testOperationStatsUsagePercent() {
        OperationRateLimiter.OperationStats stats =
                new OperationRateLimiter.OperationStats("test", 50, 5, 100);

        assertEquals(50.0, stats.getUsagePercent(), 0.1);
        assertEquals("test", stats.operation());
        assertEquals(50, stats.currentCount());
        assertEquals(5, stats.rejectedCount());
        assertEquals(100, stats.limit());
    }

    @Test
    @DisplayName("限制器应该处理高并发操作")
    void testConcurrentOperations() throws InterruptedException {
        OperationRateLimiter smallLimiter = new OperationRateLimiter(50, 60000);

        // 使用多线程测试
        Thread[] threads = new Thread[10];
        int[] successCount = {0};

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    if (smallLimiter.tryAcquire("read_file", "/concurrent/path")) {
                        synchronized (successCount) {
                            successCount[0]++;
                        }
                    }
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // 应该有一些成功，一些被拒绝
        assertTrue(successCount[0] > 0);
        assertTrue(successCount[0] <= 200); // read_file 的限制
    }
}
