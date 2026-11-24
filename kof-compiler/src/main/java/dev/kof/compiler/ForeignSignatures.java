package dev.kof.compiler;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * #1195: which resolved {@code new}/{@code .method()} call sites carry a
 * signature that came from the CLASSPATH rather than from Kof source.
 *
 * <p>Kof lets a signature say "an absent value is fine" ({@code T?}). A foreign
 * signature has no such spelling, and several SDK calls legitimately take a
 * {@code null} ({@code new Button(null)}, {@code super.onCreate(null)}), so the
 * null-argument guard must not judge them — it would be guessing.
 */
final class ForeignSignatures {

    private final Set<NewExpr> constructors =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<MethodCallExpr> methods =
            Collections.newSetFromMap(new IdentityHashMap<>());

    void markConstructor(NewExpr expr, boolean foreign) {
        if (foreign) constructors.add(expr); else constructors.remove(expr);
    }

    void markMethod(MethodCallExpr call, boolean foreign) {
        if (foreign) methods.add(call); else methods.remove(call);
    }

    boolean isForeign(NewExpr expr) { return constructors.contains(expr); }

    boolean isForeign(MethodCallExpr call) { return methods.contains(call); }
}
