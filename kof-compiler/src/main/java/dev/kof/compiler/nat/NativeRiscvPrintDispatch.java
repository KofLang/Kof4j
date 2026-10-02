package dev.kof.compiler.nat;

import dev.kof.compiler.KofBuffer;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.Type;

/**
 * Despacho de {@code println}/{@code print} no cross riscv64/aarch64 (aarch64
 * herda via tradutor). Extraído de {@link NativeRiscvCrossOps} pela regra
 * ≤500/≤600 linhas quando o ramo do {@code Buffer(U8)} (#651 fatia B) empurrou
 * a classe para a linha vermelha. Comportamento byte-idêntico ao bloco
 * original — só a localização muda.
 */
final class NativeRiscvPrintDispatch {

    private NativeRiscvPrintDispatch() {}

    /** @return true quando {@code kc} é um println/print e foi emitido. */
    static boolean emit(StringBuilder sb, KofCall kc, Type argType, NativeRiscvCrossEmit other) {
        String mn = kc.methodName();
        if (kc.kind() != KofCallKind.INSTANCE
                || !("println".equals(mn) || "print".equals(mn))) {
            return false;
        }
        boolean nl = "println".equals(mn);
        sb.append("    pop a0\n");
        // T? (get de Map, SG-008/bug 87): despacho pelo INNER — sem isso
        // Nullable(primitivo) caía no println_string sobre raw int (segv)
        Type dispatchType = argType instanceof Type.NullableType nt ? nt.inner() : argType;
        if (argType instanceof Type.NullableType nnt2
                && nnt2.inner() instanceof Type.PrimitiveType ipt2
                && NativeBoxTags.unboxFn(ipt2.name()) != null) {
            // §284-map: Nullable(Int/Short/Byte/Long) = caixa fisica do
            // slot de Map (escrita no lowerer). Despacha pela caixa; o
            // Nullable(Char) ja chega DESEMBALADO do lowerer (ramo char,
            // valueOf(CHAR)) e nunca passa por aqui.
            sb.append("    call kof_box_to_string\n");
            sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
        } else if (KofBuffer.isBufferType(dispatchType)) {
            // #651 fatia B: Buffer(U8) no cross imprime pelo contrato de valor
            // ("Buffer[cap]"), como o x86-64/JVM/JS — não como ponteiro cru.
            sb.append("    call kof_buffer_to_string\n");
            sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
        } else if (dispatchType instanceof Type.PrimitiveType pt) {
            String cn = Type.canonicalPrimitiveName(pt.name());
            switch (cn) {
                case "char" -> {
                    // §333/#259: Char imprime o CARACTERE (D-PRINT/§216),
                    // não o codepoint — paridade com JVM e x86.
                    sb.append("    call kof_char_to_string\n");
                    sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
                }
                case "int", "short", "byte" -> {
                    sb.append(nl ? "    call kof_println_int\n" : "    call kof_print_int\n");
                }
                case "long" -> sb.append(nl ? "    call kof_println_int\n" : "    call kof_print_int\n");
                case "bool", "boolean" -> {
                    sb.append("    call kof_bool_to_string\n");
                    sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
                }
                case "float" -> {
                    // FLT001 (fechado 15/09): println(double/float) direto
                    // de System.out (não passa pelo valueOf do sugar).
                    sb.append("    call kof_float_to_string\n");
                    sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
                }
                case "double" -> {
                    sb.append("    call kof_double_to_string\n");
                    sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
                }
                default -> sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
            }
        } else {
            // §284: pode chegar um BOX de erasure aqui (println de um
            // Object direto, sem sugar) — kof_box_to_string normaliza
            // box→string e passa nao-box cru (o caminho antigo roda
            // inalterado p/ String/objeto real).
            sb.append("    call kof_box_to_string\n");
            sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
        }
        // o receiver (System.out via KofGetStatic) é descartado — o
        // runtime nativo não usa o PrintStream.
        sb.append("    addi sp, sp, 8\n");
        sb.append("    li a0, 0\n");
        other.pushRiscv(sb, "a0");
        return true;
    }
}
