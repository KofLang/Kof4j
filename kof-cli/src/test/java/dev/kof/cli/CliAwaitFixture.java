package dev.kof.cli;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Shared bounded-wait helpers for the CLI E2E fixtures — the single home of the
 * {@code Thread.sleep} used by the readiness/teardown loops, so the test classes
 * poll a deadline (or block on process exit) instead of guessing a fixed settle.
 * Pure test infrastructure: the CLI itself is never touched.
 */
final class CliAwaitFixture {
    private CliAwaitFixture() {
    }

    /** Bounded poll: retries while the condition throws or returns false. */
    static boolean awaitTrue(int attempts, long intervalMillis, Callable<Boolean> condition) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                if (Boolean.TRUE.equals(condition.call())) {
                    return true;
                }
            } catch (Exception e) {
                // not ready yet; retry
            }
            pause(intervalMillis);
        }
        return false;
    }

    /** Waits for a process handle to exit; true if it did within the timeout. */
    static boolean awaitExit(ProcessHandle handle, long timeoutMillis) {
        try {
            handle.onExit().get(timeoutMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return !handle.isAlive();
        } catch (Exception e) {
            return !handle.isAlive();
        }
    }

    /** Pause for alive-aware retry loops; keeps the raw sleep out of the tests. */
    static void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
