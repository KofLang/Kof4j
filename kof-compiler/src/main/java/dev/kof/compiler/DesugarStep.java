package dev.kof.compiler;

/**
 * Internal AST-phase desugar hook (2.2.3, `DECISIONS.md` §D-DESUGAR-STEP,
 * 21/09/2026 — option B: compiler-internal, zero language surface).
 *
 * <p>A step receives the compilation unit after the parse/merge/imports pass and
 * BEFORE semantic analysis, and returns the desugared unit. Registered by
 * compiler internals only; the default registry holds the five built-in source
 * desugars in their historical order, so existing behavior is unchanged (freeze
 * rule 3).
 *
 * <p>This is the seam the source desugars plug into (`using`, `tests`,
 * `application`, `infra`, `nested-functions`). The post-IR {@link CodegenStep} hook is a
 * different phase and stays for future IR-level passes.
 */
public interface DesugarStep {

    /** Stable telemetry/diagnostic name. Never null, never empty. */
    String name();

    /**
     * Transforms the compilation unit. Must return a non-null unit; a null
     * return is a programming error and fails loudly (never silent, R6).
     */
    CompilationUnitNode apply(CompilationUnitNode unit, CompilerDriver driver);
}
