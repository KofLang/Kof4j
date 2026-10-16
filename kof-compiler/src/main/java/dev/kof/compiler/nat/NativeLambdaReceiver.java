package dev.kof.compiler.nat;

import dev.kof.compiler.IRLocalVariable;

/**
 * #876 — quem é o RECEPTOR (`this`) num método nativo.
 *
 * <p>O receptor é o local de <b>slot 0</b> e SÓ ele. Uma LAMBDA tem DOIS locais
 * chamados `this`: o slot 0 (a própria lambda, plantado pelo
 * {@code CompilerLambdaClass}) e a CAPTURA do `this` externo, re-hospedada num
 * slot POSTERIOR com o mesmo nome (`known-bugs` §735). Tratar os dois como
 * receptor gravava o PRÓPRIO OBJETO no campo da captura — medido no binário:
 * {@code Lambda0_init_1} fazia {@code mov %rdi,-0x8; mov %rdi,-0x10} e a captura
 * em {@code %rsi} era ignorada —, então o "this capturado" virava a lambda, o
 * dispatch virtual caía no {@code Lambda0_invoke} dela mesma e o {@code invoke}
 * chamava a si próprio: recursão infinita (gdb: 174.665 frames) até estourar a
 * pilha — SIGSEGV, rc=139.
 *
 * <p>Extraído para manter {@code NativeMethodEmitter} no baseline do
 * {@code check_500} (o arquivo estava congelado em 601 linhas com split
 * planejado).
 */
final class NativeLambdaReceiver {

    private NativeLambdaReceiver() {}

    /** True apenas para o receptor real (slot 0). */
    static boolean isThisReceiver(IRLocalVariable lv) {
        return lv.index() == 0 && "this".equals(lv.name());
    }
}
