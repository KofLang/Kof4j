package dev.kof.compiler.js;

import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofCall;

import java.util.List;
import java.util.Map;

/**
 * G240 — the JS face of {@code lock (target) { }}. JS runs one thread, so the
 * lock itself is a no-op ({@code kof_lock_*} emit nothing) — EXCEPT across a
 * suspension point: an async function yields at {@code await} (and at
 * {@code sleep}, {@code channel.receive}, a call to a suspending function), and
 * another task could then run inside the same lock. A function that both
 * locks and suspends is refused (CONC005) instead of compiling to a lock that
 * silently does not exclude.
 */
final class JsLockCheck {

    static final String CODE = "CONC005";

    private JsLockCheck() {}

    static boolean isLockOp(String name) {
        return name.startsWith("kof_lock_");
    }

    static void rejectSuspendingLocks(List<IRClass> classes, Map<String, Boolean> async) {
        for (IRClass clazz : classes) {
            if (JsLoweringContext.skipClass(clazz)) continue;
            for (IRMethod method : clazz.methods()) {
                if (!async.getOrDefault(JsLoweringContext.asyncMethodKey(clazz, method), false)) continue;
                boolean locks = method.basicBlocks().stream().flatMap(b -> b.operations().stream())
                        .anyMatch(op -> op instanceof KofCall kc && "kof_lock_enter".equals(kc.methodName()));
                if (locks) {
                    throw new IllegalStateException(CODE + ": '" + method.name() + "' uses lock and also"
                            + " suspends (await, sleep, channel receive or a call to a suspending function);"
                            + " on the JS target another task would run while the lock is held."
                            + " Move the suspending call into a function without lock.");
                }
            }
        }
    }
}
