package dev.kof.compiler;

import java.util.EnumSet;
import java.util.Set;

/**
 * Embedding contract (kofscript-embedding slice 3): the capability a script
 * must be GRANTED before the stdlib surface behind it may run.
 *
 * <p>Least privilege by default: a session built with no grants can compute,
 * print and use collections, but every gate below REFUSES with a named
 * diagnostic — never a silent no-op, never a fallback (the platform rule).
 *
 * <p>The mapping is intentionally coarse: one capability per stdlib surface.
 * A finer model is a later decision; this enum exists so the deny path is
 * explicit and testable today.
 */
public enum KofCapability {
    /** `kof.io` — filesystem read/write/delete. */
    FILESYSTEM("kof_io_"),
    /** `kof.net`/`kof.web` — outbound network and servers. */
    NETWORK("kof_net_", "kof_web_", "kof_mq_"),
    /** `kof.process` — spawning OS processes. */
    PROCESS("kof_proc_", "kof_process_"),
    /** `kof.db` — database connections. */
    DATABASE("kof_db_"),
    /** Reflection/host escape (`Class.forName`, host interop). */
    REFLECTION("kof_reflect_", "kof_host_");

    private final String[] prefixes;

    KofCapability(String... prefixes) {
        this.prefixes = prefixes;
    }

    /** The capability whose prefix matches the runtime function, or null. */
    static KofCapability forRuntimeFn(String fnName) {
        for (KofCapability c : values()) {
            for (String p : c.prefixes) {
                if (fnName.startsWith(p)) return c;
            }
        }
        return null;
    }

    /** The full grant set (a trusted, unconfined session). */
    public static Set<KofCapability> all() {
        return EnumSet.allOf(KofCapability.class);
    }

    /** No grants — the safe default for untrusted scripts. */
    public static Set<KofCapability> none() {
        return EnumSet.noneOf(KofCapability.class);
    }
}
