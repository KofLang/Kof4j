package dev.kof.compiler.memory;

/**
 * D-MEMORY-SAFETY Fase 2 (fatia 1) — as cinco clases de dono da spec
 * {@code docs/spec/memory-safety.md} §2.1, na ordem exata da tabela.
 *
 * Dono = a entidade responsavel pelo fim da vida do objeto. Em Kof nao ha
 * `free`/`drop` de usuario: o fim chega por coleta (Scope/GcRoot/Container/
 * Closure) ou por liberacao explicita na fronteira FFI.
 *
 * Este pacote e infraestrutura INTERNA: nada aqui muda codigo de usuario nem
 * emite diagnosticos (emissao = Fase 3). A regra e representada para que o
 * resto do compilador possa raciocinar sobre ela sem re-derivar do texto.
 */
public enum OwnerKind {

    /** Escopo do bloco/funcao onde o binding dono foi declarado. */
    SCOPE("end of the block/function of the owning binding"),

    /** Raiz de GC: campo estatico, slot de pilha, registrador (conservativo no Native). */
    GC_ROOT("when the root becomes unreachable"),

    /** Container: a colecao e dona dos elementos enquanto os contem. */
    CONTAINER("when the container is collected or cleared"),

    /** Closure: e dona dos seus captures ate ser coletada. */
    CLOSURE("when the closure is collected"),

    /** Fronteira FFI: liberacao explicita (arena close / kof_ffi_release). */
    FFI("explicit release (arena close or kof_ffi_release)");

    private final String lifetimeEnd;

    OwnerKind(String lifetimeEnd) {
        this.lifetimeEnd = lifetimeEnd;
    }

    public String lifetimeEnd() {
        return lifetimeEnd;
    }
}
