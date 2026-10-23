package dev.kof.compiler.nat;

import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Frame cross riscv64: layout dos slots locais, acesso a local e o prologue
 * (setup do frame + save dos args + zeragem dos slots que não são arg do §625).
 * Extraído de {@code NativeRiscvCrossEmit} — o arquivo cruzou a linha de 600
 * linhas ao ganhar o guard de imediato do §687, e a responsabilidade é coesa:
 * tudo aqui é sobre ONDE os locais vivem no frame.
 *
 * <p>Layout (idêntico ao x86_64, alto→baixo): {@code [s11+0]=old s11},
 * {@code [s11-8]=saved ra}, {@code [s11-16-idx*8]=local idx}, {@code [sp..] =
 * operandos}.
 */
final class NativeRiscvCrossFrame {

    private NativeRiscvCrossFrame() {}

    static int localOff(int idx) { return -(idx + 1) * 8 - 16; }

    /**
     * §687: `ld`/`sd` do riscv levam imediato de 12 bits ASSINADO (±2048). Um
     * frame grande (array local volumoso, struct grande, muitas dezenas de
     * locais) passa disso e o assembler RECUSA `sd zero,-2056(s11)` — o build
     * morria no assembler, não em Kof. Acima do limite o endereço do slot é
     * materializado em `t6` (rascunho do emissor: nenhuma op usa t4-t6) e o
     * acesso vira offset 0. O tradutor aarch64 já cobre a sequência
     * (`aarch64MovImm` + o caminho de 3 registradores `sub x15, x29, x15`),
     * então a face aarch64 sai equivalente: era SÓ o assembler riscv que
     * recusava. O `li` leva o valor POSITIVO e o `sub` faz o sinal — o caminho
     * de imediato negativo do `li` nunca foi exercitado no tradutor.
     */
    static void emitLocalMem(StringBuilder sb, String op, String reg, int idx) {
        int off = localOff(idx);
        if (off >= -2048 && off <= 2047) {
            sb.append("    ").append(op).append(' ').append(reg).append(", ")
              .append(off).append("(s11)\n");
            return;
        }
        sb.append("    li t6, ").append(-off).append("\n");
        sb.append("    sub t6, s11, t6\n");
        sb.append("    ").append(op).append(' ').append(reg).append(", 0(t6)\n");
    }

    /**
     * Prologue: reserva o frame, salva os args de entrada nos slots locais e
     * zera os slots que NÃO são arg. ABI riscv: a0=this/arg0, a1..a7 = demais
     * args (até 8 registradores).
     */
    static void emitPrologue(StringBuilder sb, IRMethod method, int frameSize) {
        sb.append("    addi sp, sp, -16\n");
        sb.append("    sd s11, 0(sp)\n");
        sb.append("    sd ra, 8(sp)\n");
        sb.append("    addi s11, sp, 16\n");
        // §546: frames grandes estouram o imediato de 12 bits do `addi`; usa
        // li+sub (o tradutor aarch64 cobre ambos).
        if (frameSize <= 2032) {
            sb.append("    addi sp, sp, -").append(frameSize).append("\n");
        } else {
            sb.append("    li t0, ").append(frameSize).append("\n");
            sb.append("    sub sp, sp, t0\n");
        }
        String[] argRegs = {"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"};
        // §620 (bug 9 cross): capturas de lambda NÃO são args de entrada (são
        // carregadas dos campos do objeto via ops). O laço antigo percorria os
        // locais na ordem de INSERÇÃO [this, captura, param] e consumia um
        // registrador para a captura, deslocando todos os params reais — a
        // lambda com captura recebia o argumento no registrador errado (lixo no
        // cross, correto no x86 que já pulava capturas). Só os slots 1..(soma
        // das larguras dos params) recebem registradores; capturas (slots
        // acima) são preenchidas pelas ops. Locais ordenados por índice (a
        // lista do IR pode estar fora de ordem; `this`=0 primeiro).
        int paramSlotMax = 1;
        for (Type pt : method.parameterTypes()) {
            paramSlotMax += NativeTypeKinds.isDoubleWidthSlot(pt) ? 2 : 1;
        }
        List<IRLocalVariable> sortedLocals = new ArrayList<>(method.localVariables());
        sortedLocals.sort(Comparator.comparingInt(IRLocalVariable::index));
        // §758: o receiver é o SLOT 0, não o NOME "this" — a captura de `this`
        // numa lambda também se chama "this" (slot >= 1) e era tratada como
        // receiver, gravando a própria lambda no campo de captura; o invoke
        // então despachava pela vtable da lambda → recursão/SIGSEGV no cross.
        int argIdx = 1; // a0 = slot 0 (receiver ou 1º param static); params em a1+
        for (IRLocalVariable lv : sortedLocals) {
            if (lv.index() == 0) {
                emitLocalMem(sb, "sd", "a0", lv.index());
                continue;
            }
            if (lv.index() >= paramSlotMax) {
                // captura de lambda (ou temporário de índice alto): preenchido
                // pelas ops, NÃO consome registrador de entrada (§620).
                // §625: mas o slot precisa ser ZERADO, não deixado stale — o GC
                // conservador do cross varre o frame [sp..bottom] e seguiria o
                // lixo como ponteiro. O x86 tinha o MESMO padrão latente (mesma
                // correção aplicada lá); a regressão AV1 só se materializou no
                // cross pelo layout de frame/operandos, não por já zerar.
                emitLocalMem(sb, "sd", "zero", lv.index());
                continue;
            }
            if (argIdx < argRegs.length) {
                emitLocalMem(sb, "sd", argRegs[argIdx], lv.index());
            } else {
                int stackOff = 8 * (argIdx - argRegs.length);
                sb.append("    ld t0, ").append(stackOff).append("(s11)\n");
                emitLocalMem(sb, "sd", "t0", lv.index());
            }
            argIdx++;
        }
    }
}
