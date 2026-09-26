package dev.kof.compiler.memory;

import java.util.HashMap;
import java.util.Map;

/**
 * D-MEMORY-SAFETY Fase 2 (fatia 1) — a tabela de regras da spec
 * {@code docs/spec/memory-safety.md} (§2 O-, §3 L-, §3.1-3.2 B-, §4 M-,
 * §6 E-, §8 C-, §10 N-) como representacao interna unica. Cada linha guarda
 * o id da regra, o codigo de diagnostico esperado (null = regra permissiva
 * ou descritiva, sem diagnostico por construção) e a classificacao.
 *
 * NADA aqui emite nada (emissao/encaminhamento = Fase 3). O objetivo da
 * fatia 1 e dar ao compilador uma fonte nomeada, testada e traceavel do
 * contrato, em vez de regras repetidas em comentarios por todo o frontend.
 */
public enum MemRule {

    // §2.2 Ownership (O-)
    O_01("O-01", "Single owner", "MEM001", Class.COMPILE),
    O_02("O-02", "Explicit transfer (source nulled)", "MEM002", Class.COMPILE),
    O_03("O-03", "Container owns elements; clear releases", "MEM003", Class.COMPILE),
    O_04("O-04", "Closure owns captures", "MEM004", Class.COMPILE),
    O_05("O-05", "FFI ownership explicit at the boundary", "MEM005", Class.COMPILE_AND_RUNTIME),

    // §3.2 Lifetime (L-)
    L_01("L-01", "No dangling (GC by construction)", "MEM010", Class.RUNTIME),
    L_02("L-02", "No use-after-free (reachable never reclaimed)", "MEM011", Class.RUNTIME),
    L_03("L-03", "No double-free (kof_free at most once)", "MEM012", Class.RUNTIME),
    L_04("L-04", "Escape awareness (captures extend lifetime)", "MEM013", Class.COMPILE),
    L_05("L-05", "Resource lifetime (explicit close required)", "MEM014", Class.COMPILE),

    // §3.1-3.2 Borrowing & aliasing (B-)
    B_01("B-01", "Shared aliasing is allowed (reference semantics)", null, Class.NONE),
    B_02("B-02", "Mutation through alias is visible (allowed)", null, Class.NONE),
    B_03("B-03", "FFI borrow: one writable buffer, no concurrent FFI write", "MEM020", Class.COMPILE_AND_RUNTIME),
    B_04("B-04", "Spawn alias: shared mutable without sync is forbidden", "MEM021", Class.COMPILE_AND_RUNTIME),
    B_05("B-05", "Stdlib aliasing: no mutation during iteration without copy", "MEM022", Class.COMPILE_AND_RUNTIME),
    B_06("B-06", "Closure capture: value snapshot or box when mutated", "MEM023", Class.COMPILE),

    // §4 Mutability (M-) — ja imposta hoje por SEM037/038/054; representada aqui
    // para que o modelo de memoria enxergue a fronteira binding-objeto (binding
    // congelado, objeto nao).
    M_01("M-01", "val binding freeze", "SEM037", Class.COMPILE),
    M_02("M-02", "record component write forbidden", "SEM038", Class.COMPILE),
    M_03("M-03", "collection mutation through val is legal", null, Class.NONE),
    M_04("M-04", "element-field write through val collection forbidden", "SEM054", Class.COMPILE),

    // §6 Escape & capture (E-) — comportamento, sem diagnostico proprio nesta fase
    E_01("E-01", "capture by value (un-mutated locals)", null, Class.NONE),
    E_02("E-02", "capture by box (mutated locals re-boxed)", null, Class.NONE),
    E_03("E-03", "escape: closure ownership extends capture lifetime", null, Class.NONE),

    // §8 Concurrency (C-)
    C_01("C-01", "spawn shares references (no copy at boundary)", null, Class.NONE),
    C_02("C-02", "join_all guarantees no orphan tasks", null, Class.NONE),
    C_03("C-03", "data race guard at spawn with shared mutable state", "MEM021", Class.COMPILE_AND_RUNTIME),
    C_04("C-04", "worker stacks are never GC roots (native)", null, Class.RUNTIME),

    // §10 Nullability x ownership (N-)
    N_01("N-01", "dereference of T? without narrowing", "SEM049", Class.COMPILE),
    N_02("N-02", "null literal assignment forbidden", "SEM048", Class.COMPILE),
    N_03("N-03", "only x != null narrows (|| does not)", null, Class.COMPILE),
    N_04("N-04", "nullable primitive: boxed JVM/JS, 0-default native (divergence documented)", null, Class.NONE);

    /** Onde a regra pode ser pega — vocabulario da coluna "Classification" da spec. */
    public enum Class {
        COMPILE, RUNTIME, COMPILE_AND_RUNTIME, NONE
    }

    private static final Map<String, MemRule> BY_CODE = new HashMap<>();

    static {
        for (MemRule r : values()) {
            if (r.diagnostic != null) {
                MemRule prev = BY_CODE.putIfAbsent(r.id, r);
                assert prev == null : "rule id collision: " + r.id;
            }
        }
    }

    private final String id;
    private final String title;
    private final String diagnostic;
    private final Class classification;

    MemRule(String id, String title, String diagnostic, Class classification) {
        this.id = id;
        this.title = title;
        this.diagnostic = diagnostic;
        this.classification = classification;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** Codigo de diagnostico esperado, ou null = regra permissiva/descritiva. */
    public String diagnostic() {
        return diagnostic;
    }

    public Class classification() {
        return classification;
    }

    /** Verdadeiro quando a regra e proibicao com codigo proprio (nao permissiva). */
    public boolean forbids() {
        return diagnostic != null;
    }
}
