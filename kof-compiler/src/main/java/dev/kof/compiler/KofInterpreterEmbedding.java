package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;

/**
 * Embedding contract (kofscript-embedding plan, issue #842) — the host-facing
 * surface of the interpreter, kept OUT of {@link KofInterpreter} so the
 * classical interpreter stays under the 500-line gate (the embedding adds
 * state and three public entry points; the mechanism lives here).
 *
 * <p>Three host requirements, all proven in `KofEmbedSessionTest`:
 * <ol>
 *   <li><b>bounded execution</b> — the budget ticks at every IR op boundary
 *       ({@link KofStepBudget}); a spent grant PARKS the worker;</li>
 *   <li><b>capability confinement</b> — a non-null grant set gates the stdlib
 *       surfaces ({@link KofCapability}); an ungranted one refuses with
 *       {@code EMBED001} before running;</li>
 *   <li>the session bindings live on the host facade (kof-script), not here:
 *       the interpreter must stay unaware of host values.</li>
 * </ol>
 *
 * <p>NULL grants = the classic unconfined behavior; this is why every existing
 * caller keeps its exact semantics (the regression battery 70/0F).
 */
public final class KofInterpreterEmbedding {

    private final KofInterpreter interp;

    /** The classic batch entry (moved here to keep KofInterpreter under 600). */
    public static KofInterpreter.Result runBatch(IRModule module, String[] args,
                                                List<Diagnostic> warnings) {
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        ByteArrayOutputStream se = new ByteArrayOutputStream();
        PrintStream po = new PrintStream(so, true);
        PrintStream pe = new PrintStream(se, true);
        KofInterpreter interp = new KofInterpreter(module, po, pe);
        int code = 0;
        try {
            interp.ensureRuntimeForModule();
            interp.execute(args == null ? new String[0] : args);
        } catch (Throwable t) {
            code = 1;
            pe.println(KofInterpreter.describeError(t));
            if (System.getProperty("kof.interp.trace") != null) t.printStackTrace(pe);
        } finally {
            po.flush();
            pe.flush();
        }
        return new KofInterpreter.Result(code, so.toString(), se.toString(),
                warnings == null ? List.of() : warnings);
    }

    /** The inherit-output batch entry (REPL: immediate writes). */
    public static KofInterpreter.Result runInheritBatch(IRModule module, String[] args) {
        KofInterpreter interp = new KofInterpreter(module, System.out, System.err);
        int code = 0;
        try {
            interp.ensureRuntimeForModule();
            interp.execute(args == null ? new String[0] : args);
        } catch (Throwable t) {
            code = 1;
            System.err.println(KofInterpreter.describeError(t));
        }
        return new KofInterpreter.Result(code, null, null);
    }
    private KofStepBudget budget = KofStepBudget.UNBOUNDED;
    private Set<KofCapability> grants;

    KofInterpreterEmbedding(KofInterpreter interp) {
        this.interp = interp;
    }

    /** Installs the cooperative work budget (NULL restores UNBOUNDED). */
    public void setStepBudget(KofStepBudget b) {
        this.budget = b == null ? KofStepBudget.UNBOUNDED : b;
    }

    /** Installs the capability grants (NULL restores the unconfined behavior). */
    public void setCapabilityGrants(Set<KofCapability> granted) {
        this.grants = granted;
    }

    /** The per-op hook the interpreter's loop calls. */
    void tick() {
        budget.tick();
    }

    /** Only a NON-NULL grant set confines; an ungranted surface refuses by name. */
    void requireCapability(String fnName) {
        if (grants == null) return;
        KofCapability cap = KofCapability.forRuntimeFn(fnName);
        if (cap != null && !grants.contains(cap)) {
            throw new KofCapabilityDeniedException(cap, fnName);
        }
    }
}
