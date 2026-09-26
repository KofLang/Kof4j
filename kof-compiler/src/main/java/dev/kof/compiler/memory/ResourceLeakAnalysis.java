package dev.kof.compiler.memory;

import dev.kof.compiler.AstNode;
import dev.kof.compiler.AssignmentExpr;
import dev.kof.compiler.DiagnosticCollector;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.FieldAccessExpr;
import dev.kof.compiler.IdentifierExpr;
import dev.kof.compiler.LambdaExpr;
import dev.kof.compiler.MethodCallExpr;
import dev.kof.compiler.ReturnStmt;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.VarDeclStmt;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * D-MEMORY-SAFETY Fase 3 (fatia 3.1b) — L-05/{@code MEM014} compile-time
 * resource-lifetime check of {@code docs/spec/memory-safety.md} §9: a handle
 * created by {@code web.app()} that is <b>never closed anywhere in the body</b>
 * (any depth) and <b>never handed to anyone</b> (returned, passed, aliased,
 * stored) leaks — the diagnostic fires at the creation site as a WARNING.
 *
 * <p>Complements {@link OwnershipPass} (fatia 3.1: O-01/MEM001 double claim,
 * O-02/MEM002 sibling read after claim) and the spec's runtime faces
 * (L-02/MEM011 — reading the claimer itself after its own close is a runtime
 * check, not a compile-time one). The pass runs in the SHARED frontend
 * ({@code StatementAnalyzer.analyzeBody}), so all targets emit the same
 * diagnostic by construction.</p>
 *
 * <p><b>Zero false positives by construction (conservative on every axis):</b>
 * a close found ANYWHERE in the body (even inside an {@code if}/loop/lambda)
 * silences the warning; any escape shape silences it (the caller/collaborator
 * owns the close); {@code app.close()}/{@code app.port()} in receiver position
 * is a use, NOT an escape; only {@code web.app()} creators are tracked (the
 * measured close-bearing surface — db/file handles join in later slices of
 * §5 without inventing unmeasured shapes).</p>
 */
public final class ResourceLeakAnalysis {

    private ResourceLeakAnalysis() {
    }

    public static void analyze(DiagnosticCollector diag, List<StatementNode> body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        for (StatementNode stmt : body) {
            if (!(stmt instanceof VarDeclStmt v) || v.initializer() == null) {
                continue;
            }
            if (!createsWebApp(v.initializer())) {
                continue;
            }
            if (closedAnywhere(body, v.name()) || escapesAnywhere(body, v.name())) {
                continue;
            }
            diag.warning(v, "L-05: resource '" + v.name() + "' from 'web.app()' is never"
                    + " closed in this scope and never handed to anyone — call '"
                    + v.name() + ".close()' or return/transfer the handle"
                    + " (MEM014)", "MEM014");
        }
    }

    private static boolean createsWebApp(ExpressionNode expr) {
        if (expr instanceof MethodCallExpr mc
                && "app".equals(mc.methodName())
                && mc.arguments().isEmpty()
                && mc.receiver() instanceof IdentifierExpr ns
                && "web".equals(ns.name())) {
            return true;
        }
        for (Object child : children(expr)) {
            if (child instanceof ExpressionNode e && createsWebApp(e)) {
                return true;
            }
        }
        return false;
    }

    /** A close on ANY binding of the handle, at any depth (lambda included). */
    private static boolean closedAnywhere(List<StatementNode> body, String name) {
        for (StatementNode stmt : body) {
            if (hasClose(stmt, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasClose(Object node, String name) {
        if (node == null || shadows(node, name)) {
            return false;
        }
        if (node instanceof MethodCallExpr mc && "close".equals(mc.methodName())
                && mc.arguments().isEmpty()
                && mc.receiver() instanceof IdentifierExpr id && id.name().equals(name)) {
            return true;
        }
        for (Object child : children(node)) {
            if (hasClose(child, name)) {
                return true;
            }
        }
        return false;
    }

    /** Returned, passed as a call argument, or aliased into another binding. */
    private static boolean escapesAnywhere(List<StatementNode> body, String name) {
        for (StatementNode stmt : body) {
            if (escapeShape(stmt, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean escapeShape(Object node, String name) {
        if (node == null || shadows(node, name)) {
            return false;
        }
        if (node instanceof ReturnStmt r && mentionsEscaping(r.value(), name)) {
            return true;
        }
        if (node instanceof VarDeclStmt v && !v.name().equals(name)
                && mentionsEscaping(v.initializer(), name)) {
            return true;
        }
        if (node instanceof AssignmentExpr as
                && !(as.target() instanceof IdentifierExpr id && id.name().equals(name))
                && mentionsEscaping(as.value(), name)) {
            return true;
        }
        for (Object child : children(node)) {
            if (escapeShape(child, name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bare reference / call argument / stored value = the handle left this
     * scope; receiver position ({@code app.close()}, {@code app.port()}) and
     * plain field reads are uses, not transfers.
     */
    private static boolean mentionsEscaping(Object node, String name) {
        if (node == null || shadows(node, name)) {
            return false;
        }
        if (node instanceof IdentifierExpr id) {
            return id.name().equals(name);
        }
        if (node instanceof MethodCallExpr mc) {
            if (!(mc.receiver() instanceof IdentifierExpr recv && recv.name().equals(name))
                    && mentionsEscaping(mc.receiver(), name)) {
                return true;
            }
            for (ExpressionNode arg : mc.arguments()) {
                if (mentionsEscaping(arg, name)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof FieldAccessExpr fa
                && fa.receiver() instanceof IdentifierExpr recv
                && recv.name().equals(name)) {
            return false;
        }
        for (Object child : children(node)) {
            if (mentionsEscaping(child, name)) {
                return true;
            }
        }
        return false;
    }

    /** Node introduces its own binding of {@code name} (shadow boundary). */
    private static boolean shadows(Object node, String name) {
        if (node instanceof VarDeclStmt v) {
            return v.name().equals(name);
        }
        if (node instanceof LambdaExpr lambda) {
            return lambda.parameters().stream().anyMatch(p -> p.name().equals(name));
        }
        if (node instanceof dev.kof.compiler.FunctionDeclarationNode fn) {
            return fn.parameters().stream().anyMatch(p -> p.name().equals(name));
        }
        return false;
    }

    private static List<Object> children(Object node) {
        List<Object> out = new ArrayList<>();
        if (!(node instanceof AstNode)) {
            return out;
        }
        for (RecordComponent rc : node.getClass().getRecordComponents()) {
            Object value;
            try {
                value = rc.getAccessor().invoke(node);
            } catch (IllegalAccessException | InvocationTargetException e) {
                continue;
            }
            if (value == null) {
                continue;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof AstNode child) {
                        out.add(child);
                    }
                }
            } else if (value instanceof AstNode child) {
                out.add(child);
            }
        }
        return out;
    }
}
