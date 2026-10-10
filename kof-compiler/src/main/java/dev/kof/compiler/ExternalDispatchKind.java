package dev.kof.compiler;

/**
 * §557 — Converte a flag {@code ownerIsInterface()} de uma assinatura externa
 * (bytecode ASM ou reflexão JDK) para o {@link SymbolTable.DispatchKind} que o
 * lowering e o emissor JVM esperam.
 *
 * <p>Separado de {@link MemberCallTyper} para manter o arquivo abaixo do limite
 * estrito de 600 linhas do gate {@code check_500.sh}.
 */
final class ExternalDispatchKind {

    private ExternalDispatchKind() {}

    /**
     * Devolve {@link SymbolTable.DispatchKind#INTERFACE} se o dono for
     * interface, ou {@link SymbolTable.DispatchKind#INSTANCE} para classes
     * normais.
     */
    static SymbolTable.DispatchKind of(boolean isInterface) {
        return isInterface ? SymbolTable.DispatchKind.INTERFACE : SymbolTable.DispatchKind.INSTANCE;
    }
}
