package dev.kof.compiler;

import java.util.List;

/**
 * G240 — what {@code lock (target)} may lock on, and the runtime calls it
 * lowers to. The target must be a non-null object: a primitive or a value
 * has no identity to lock on, and a nullable target would fail only at run
 * time (CONC004, at compile time instead).
 */
final class LockTargets {

    static final String ENTER = "kof_lock_enter";
    static final String EXIT = "kof_lock_exit";
    static final Type OWNER = new Type.ClassType("kof.sync", "Lock", List.of());
    static final Type OBJECT = new Type.ClassType("java.lang", "Object", List.of());

    private LockTargets() {}

    static void check(SemanticAnalyzer sa, LockStmt ls, Type t) {
        if (sa.diagnostics() == null || t == null || Type.isUnknown(t)) return;
        String why = t instanceof Type.NullableType ? "a nullable value (it may be null at run time)"
                : t instanceof Type.PrimitiveType || BuiltinTypes.isString(t) ? "a " + Type.display(t)
                        + " value (it has no identity to lock on)"
                : null;
        if (why == null) return;
        sa.diagnostics().error(ls, "lock target must be a non-null object, got " + why
                + "; lock on the object that owns the shared state, e.g. lock (this) { ... }", "CONC004");
    }
}
