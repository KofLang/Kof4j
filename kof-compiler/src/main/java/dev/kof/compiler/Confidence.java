package dev.kof.compiler;

/**
 * Conceptual confidence of a recovered element (docs/future/LEGACY_IR.md §4).
 * The migration platform distinguishes information observed directly from the
 * artifact from that which is inferred — it never fabricates behavior
 * silently, and everything that cannot be recovered is marked UNKNOWN.
 */
public enum Confidence {
    /** Observed directly in the artifact (e.g. a field name in the constant pool). */
    EXACT("exact"),
    /** Observed plus metadata (debug info, signatures). */
    WITH_METADATA("with-metadata"),
    /** Derived from analysis (data flow, type inference). */
    INFERRED("inferred"),
    /** Plausible, based on heuristics. */
    HEURISTIC("heuristic"),
    /** Not recoverable. */
    UNKNOWN("unknown");

    private final String label;

    Confidence(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}