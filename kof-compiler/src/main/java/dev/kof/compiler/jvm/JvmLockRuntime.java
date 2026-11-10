package dev.kof.compiler.jvm;

/**
 * G240 — JVM and Script face of {@code lock (target) { body }}: one
 * {@link java.util.concurrent.locks.ReentrantLock} per locked object, keyed by
 * IDENTITY (two equal records are two locks), created on first use and removed
 * when its last holder or waiter leaves, so locking never leaks memory. A
 * {@code ReentrantLock} (not a monitor) keeps virtual threads unpinned and lets
 * the Script interpreter, which reaches the runtime by reflection, share the
 * same implementation: {@code monitorenter} cannot span two calls.
 */
final class JvmLockRuntime {

    private JvmLockRuntime() {}

    // §176/§257: a method, not a constant literal, so consumers never inline a stale copy.
    static String source() {
        return """
                // ── lock (target) { } (G240) — per-object reentrant lock ──
                private static final class KofLockKey {
                    final Object target;
                    KofLockKey(Object target) { this.target = target; }
                    @Override public boolean equals(Object o) {
                        return o instanceof KofLockKey k && k.target == target;
                    }
                    @Override public int hashCode() { return System.identityHashCode(target); }
                }
                private static final class KofLockEntry {
                    final java.util.concurrent.locks.ReentrantLock lock =
                            new java.util.concurrent.locks.ReentrantLock();
                    int users;
                }
                private static final java.util.Map<KofLockKey, KofLockEntry> KOF_LOCKS =
                        new java.util.HashMap<>();
                public static void kof_lock_enter(Object target) {
                    if (target == null) throw new NullPointerException("lock target is null");
                    KofLockKey key = new KofLockKey(target);
                    KofLockEntry e;
                    synchronized (KOF_LOCKS) {
                        e = KOF_LOCKS.computeIfAbsent(key, k -> new KofLockEntry());
                        e.users++;
                    }
                    e.lock.lock();
                }
                public static void kof_lock_exit(Object target) {
                    KofLockKey key = new KofLockKey(target);
                    synchronized (KOF_LOCKS) {
                        KofLockEntry e = KOF_LOCKS.get(key);
                        if (e == null) return;
                        e.lock.unlock();
                        if (--e.users == 0) KOF_LOCKS.remove(key);
                    }
                }
                """;
    }
}
