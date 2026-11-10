package dev.kof.compiler;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * §809 — the imports a top-level declaration's OWN file wrote. A module is
 * compiled as one merged unit whose import list is the union of every file,
 * so {@code import java.security.Security} in {@code app/Main.kf} also looked
 * imported in {@code kernel/security/Security.kf}, and a project class named
 * {@code Security} took over {@code Security.getProvider(...)} in the file
 * that imported the JDK class. The analyzer and the lowering enter each
 * declaration here, and a static receiver asks {@link #explicitlyImportsExternal}
 * whether the current file's single-type import shadows a same-named project
 * class (JLS 6.4.1).
 */
final class FileImports {

    private final Map<AstNode, List<String>> byDeclaration = new IdentityHashMap<>();
    private AstNode current;

    void record(CompilationUnitNode unit) {
        for (AstNode d : unit.declarations()) byDeclaration.put(d, unit.imports());
    }

    /** Enters a top-level declaration; returns the previous one for {@link #restore}. */
    AstNode enter(AstNode decl) {
        AstNode prev = current;
        current = decl;
        return prev;
    }

    void restore(AstNode prev) { current = prev; }

    void clear() {
        byDeclaration.clear();
        current = null;
    }

    /**
     * True when the file of the declaration being compiled has a single-type
     * import {@code a.b.Name} that is not a project type: that import shadows
     * a project class with the same simple name, so {@code Name} is external.
     */
    boolean explicitlyImportsExternal(String simpleName, Predicate<String> isProjectFqn) {
        List<String> own = current == null ? null : byDeclaration.get(current);
        if (own == null || simpleName == null) return false;
        for (String imp : own) {
            if (!imp.endsWith(".*") && imp.endsWith("." + simpleName) && !isProjectFqn.test(imp)) {
                return true;
            }
        }
        return false;
    }
}
