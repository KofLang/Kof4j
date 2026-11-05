package dev.kof.compiler;

/**
 * NAT009 — a collection method of the stdlib batch (maintainer decision
 * 05/11) that the native runtimes do not implement yet: {@code List.join},
 * {@code List.add(index, value)}, {@code Set.containsAll}. A native target
 * refuses it at compile time instead of an {@code ld} undefined reference
 * (R6); a native face replaces this gate.
 */
final class CollectionNativeGap {

    static final String CODE = "NAT009";

    private CollectionNativeGap() {}

    /** Emits NAT009 and returns true on a native target. */
    static boolean refuse(CompilerDriver driver, MethodCallExpr mc, String member) {
        if (!driver.target.isNative() || driver.currentDiagnostics == null) return false;
        SourcePosition pos = mc.position();
        driver.currentDiagnostics.error(pos != null ? pos.file() : "", pos != null ? pos.line() : 0,
                pos != null ? pos.column() : 0, 0,
                member + ": not available on the " + driver.target + " target yet (" + CODE
                        + ") — the native runtime has no face for it; use the JVM/Script/JS targets",
                CODE);
        return true;
    }
}
