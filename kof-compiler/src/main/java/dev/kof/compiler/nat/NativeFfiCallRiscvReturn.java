package dev.kof.compiler.nat;

import dev.kof.compiler.AbiLayout;
import dev.kof.compiler.FfiSignature;
import dev.kof.compiler.FfiStructLayout;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * #431 (Native FFI): materialização do RETORNO de um {@code record} por valor
 * no alvo cross (riscv64 LP64 + aarch64 AAPCS64). Extraído de
 * {@link NativeFfiCallRiscv} (regra ≤500 linhas) — três faces:
 *
 * <ul>
 *   <li>register path INTEGER (≤ 16 B, {@code a0}/{@code a1});</li>
 *   <li>register path FP homogêneo-flutuante (M1 unidade-2, {@code fa0}/{@code fa1}
 *       ou {@code v0..v3});</li>
 *   <li>memory path (sret &gt; 16 B, ponteiro {@code a0}/{@code x8}).</li>
 * </ul>
 *
 * <p>O mesmo texto riscv serve as duas archs — o
 * {@code NativeAarch64Translator} normaliza os mnemonicos.
 */
final class NativeFfiCallRiscvReturn {

    private NativeFfiCallRiscvReturn() {}

    /**
     * 3.7 fatia 3: materializa o `record` devolvido por valor no alvo cross
     * (register path, campos INTEGER, &le; 16 B — {@code div_t} de {@code div}).
     * Os words chegam em {@code a0}/{@code a1} ({@code x0}/{@code x1} sob
     * AAPCS64 — o tradutor mapeia) e são SALVOS na pilha antes do
     * {@code kof_alloc} (a alocação clobberaria os registradores de retorno);
     * o objeto Kof é alocado+inicializado e cada campo é extraído do seu word
     * (shift pela largura natural — mesmo packing little-endian nas duas archs).
     * Struct com campo float/HFA ou &gt; 16 B nunca chega aqui (gate FFI001, R6).
     */
    static void emitRiscvStructReturn(NativeBackend nb, StringBuilder sb, KofCall kc) {
        NativeOpHelpers.Resolved r = NativeOpHelpers.resolveClass(nb, kc.returnType());
        List<Type> fts = new ArrayList<>();
        if (r != null) for (var f : r.layout().fields()) fts.add(f.type());
        Type st = FfiStructLayout.structType(fts);
        AbiLayout.Layout l = FfiStructLayout.layout(AbiLayout.Abi.SYSV_X86_64, st);
        int words = (l.size() + 7) / 8;
        // 1) salva os words de retorno na pilha (kof_alloc clobbera a0-a3)
        sb.append("    addi sp, sp, -").append(8 * words).append("\n");
        for (int e = 0; e < words; e++) {
            sb.append("    sd a").append(e).append(", ").append(8 * e).append("(sp)\n");
        }
        // 2) aloca+inicializa o objeto Kof (a0 = objeto)
        emitAllocInit(nb, sb, r);
        sb.append("    mv t3, a0\n");
        // 3) cada campo: do word cru p/ o slot Kof (largura natural)
        List<FfiStructLayout.FieldInfo> fs = FfiStructLayout.fields(st);
        for (FfiStructLayout.FieldInfo f : fs) {
            int cOff = f.cOffset();
            int e = cOff / 8;
            int shift = (cOff - e * 8) * 8;
            int kofOff = 16 + 8 * f.kofSlot();
            sb.append("    ld t0, ").append(8 * e).append("(sp)\n");
            if (shift > 0) sb.append("    srli t0, t0, ").append(shift).append("\n");
            switch (f.scalar().size) {
                case 1 -> sb.append("    andi t0, t0, 255\n");
                case 2 -> sb.append("    slli t0, t0, 48\n    srli t0, t0, 48\n");
                case 4 -> sb.append("    sext.w t0, t0\n");
                default -> { }
            }
            sb.append("    sd t0, ").append(kofOff).append("(t3)\n");
        }
        // 4) remove o stash e empilha o objeto como resultado
        sb.append("    addi sp, sp, ").append(8 * words).append("\n");
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd t3, 0(sp)\n");
    }

    /**
     * D-MEMORY-SAFETY M1 unidade-2 (09/10): materializa o `record`
     * HOMOGÊNEO-FLUTUANTE (≤ 2 campos ≤ 16 B) devolvido por valor no cross.
     * LP64D entrega os campos em {@code fa0}/{@code fa1}; AAPCS64 em
     * {@code v0..v3} (o tradutor normaliza {@code fa{n}}→{@code f{n}}→
     * {@code d{n}}). Cada campo é lido com {@code fmv.x.*} (bits para um
     * registrador INTEGER — soft-float-safe) e salvo na pilha de operandos
     * ANTES do {@code kof_alloc} (que clobberaria os registradores FP).
     * Depois, o objeto Kof é alocado+inicializado e cada slot é escrito no seu
     * campo (Float nos 32 bits BAIXOS, Double em 8 B — a mesma forma do
     * parâmetro em {@link FfiStructLayout#emitRiscvHfaFieldLoad}).
     */
    static void emitRiscvHfaStructReturn(NativeBackend nb, StringBuilder sb, KofCall kc, Type st) {
        List<FfiStructLayout.FieldInfo> fs = FfiStructLayout.fields(st);
        int words = fs.size();
        // 1) salva os bits de cada campo na pilha de operandos (o kof_alloc
        //    clobbera os registradores de retorno FP; o callee usa memória
        //    ABAIXO de sp, então os slots salvos acima de sp ficam intactos).
        sb.append("    addi sp, sp, -").append(8 * words).append("\n");
        for (int fi = 0; fi < words; fi++) {
            boolean flt = fs.get(fi).scalar() == AbiLayout.Scalar.FLOAT;
            sb.append(flt ? "    fmv.x.w t0, fa" : "    fmv.x.d t0, fa").append(fi).append("\n");
            sb.append(flt ? "    sw t0, " : "    sd t0, ").append(8 * fi).append("(sp)\n");
        }
        // 2) aloca+inicializa o objeto Kof
        emitAllocInit(nb, sb, NativeOpHelpers.resolveClass(nb, kc.returnType()));
        sb.append("    mv t3, a0\n");
        // 3) cada campo: do slot salvo (bits) p/ o slot Kof (mesma forma do param)
        for (int fi = 0; fi < words; fi++) {
            FfiStructLayout.FieldInfo f = fs.get(fi);
            int kofOff = 16 + 8 * f.kofSlot();
            sb.append(f.scalar() == AbiLayout.Scalar.FLOAT ? "    lw t0, " : "    ld t0, ")
              .append(8 * fi).append("(sp)\n");
            sb.append("    sd t0, ").append(kofOff).append("(t3)\n");
        }
        // 4) remove o stash e empilha o objeto como resultado
        sb.append("    addi sp, sp, ").append(8 * words).append("\n");
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd t3, 0(sp)\n");
    }

    /**
     * D-MEM-FFI-CROSS-FULL face 3: true quando o extern devolve um `record`
     * INTEGER por MEMÓRIA (sret > 16 B). Usado tanto na emissão quanto na
     * reserva do slot de rascunho do frame (o ponteiro do buffer C). */
    static boolean usesMemStructReturn(NativeBackend nb, KofCall kc) {
        if (FfiSignature.charOfType(kc.returnType()) != null) return false;
        return FfiStructLayout.crossMemoryReturn(nb.target, structStructType(nb, kc));
    }

    /** Type real do `record` de retorno (campos na ordem do layout) — mesma
     *  derivação do register path, computada uma vez para o gate da face 3. */
    static Type structStructType(NativeBackend nb, KofCall kc) {
        NativeOpHelpers.Resolved r = NativeOpHelpers.resolveClass(nb, kc.returnType());
        List<Type> fts = new ArrayList<>();
        if (r != null) for (var f : r.layout().fields()) fts.add(f.type());
        return FfiStructLayout.structType(fts);
    }

    /**
     * D-MEM-FFI-CROSS-FULL face 3: materializa o `record` devolvido por MEMÓRIA
     * (sret, campos INTEGER, &gt; 16 B). O buffer C foi alocado no passo 0 e o
     * ponteiro gravado no rascunho do frame; cada campo é lido do seu offset C
     * (arch-independente para INTEGER) e escrito no objeto Kof (mesma largura
     * natural do register path). O objeto é alocado+inicializado e empilhado
     * como resultado.
     */
    static void emitRiscvMemStructReturn(NativeBackend nb, StringBuilder sb, KofCall kc, Type st) {
        NativeOpHelpers.Resolved r = NativeOpHelpers.resolveClass(nb, kc.returnType());
        // t5 = objeto Kof (kof_alloc clobbera a0-a3; t3 é recarregado do
        // rascunho DEPOIS do init, pois kof_alloc/kof_init_object não o
        // preservam — caller-saved).
        emitAllocInit(nb, sb, r);
        sb.append("    mv t5, a0\n");
        // t3 = buffer C (ponteiro sret), agora seguro (nenhum call adiante).
        sb.append("    ld t3, ").append(nb.crossScratchOff(0)).append("(s11)\n");
        for (FfiStructLayout.FieldInfo f : FfiStructLayout.fields(st)) {
            int cOff = f.cOffset();
            int kofOff = 16 + 8 * f.kofSlot();
            switch (f.scalar().size) {
                case 1 -> sb.append("    lbu t0, ").append(cOff).append("(t3)\n");
                case 2 -> sb.append("    lhu t0, ").append(cOff).append("(t3)\n");
                case 4 -> sb.append("    lw t0, ").append(cOff).append("(t3)\n");
                default -> sb.append("    ld t0, ").append(cOff).append("(t3)\n");
            }
            sb.append("    sd t0, ").append(kofOff).append("(t5)\n");
        }
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd t5, 0(sp)\n");
    }

    /** Aloca+inicializa o objeto Kof do `record` de retorno ({@code a0} = objeto
     *  na saída); {@code kof_alloc}/{@code kof_init_object} clobberam os
     *  registradores de argumento/retorno, por isso o chamador salva o retorno
     *  ANTES. */
    private static void emitAllocInit(NativeBackend nb, StringBuilder sb, NativeOpHelpers.Resolved r) {
        int size = r != null ? r.layout().totalSize()
                             : dev.kof.compiler.ClassLayout.HEADER_SIZE + 64;
        sb.append("    li a0, ").append(size).append("\n");
        sb.append("    call kof_alloc\n");
        if (r != null) {
            String mangled = nb.sanitizeName(r.name());
            sb.append("    mv a1, a0\n");
            sb.append("    li a2, ").append(r.typeId()).append("\n");
            sb.append("    la a3, ").append(mangled).append("_vtable\n");
            sb.append("    mv a0, a1\n");
            sb.append("    mv a1, a2\n");
            sb.append("    mv a2, a3\n");
            sb.append("    call kof_init_object\n");
        }
    }
}
