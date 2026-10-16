package dev.kof.compiler.nat;

import dev.kof.compiler.IRLocalVariable;

/**
 * X7-2 fatia 2 — registro do DWARF cross. Extrai da classe de emissao
 * (gate 500): prepara o {@code .Lfe_} de fim de funcao e o {@code Fn} do
 * {@link NativeDwarf} com os slots REAIS da moldura riscv/aarch64
 * ({@code crossLocalOffRiscv}, base s11/x29), espelhando o registro x86 de
 * {@code NativeMethodEmitter} (:147-175). O frame_base em si e do
 * {@link NativeDwarf} (por ABI) — este arquivo so alimenta o registro.
 */
final class NativeDwarfCrossRegister {

    private NativeDwarfCrossRegister() {}

    static void register(NativeRiscvCrossEmit re, StringBuilder sb, String mangled,
            dev.kof.compiler.IRMethod method) {
                // X7-2 fatia 2: espelho exato do registro x86 (NativeMethodEmitter
                // :147-175) — `.Lfe_` p/ o high_pc (offset) e o Fn com os slots
                // REAIS da moldura cross (o prologue grava cada idx em
                // re.crossLocalOffRiscv(idx), base s11/x29 — mesma expressao que o
                // fbreg do DIE usa).
                sb.append(".Lfe_").append(mangled).append(":\n");
                int declLine = 1;
                if (method.debugInfo() != null && !method.debugInfo().positions().isEmpty()) {
                    for (var pos : method.debugInfo().positions().values()) {
                        if (pos.line() > 0 && (declLine == 1 || pos.line() < declLine)) declLine = pos.line();
                    }
                }
                java.util.List<NativeDwarf.Local> params = new java.util.ArrayList<>();
                java.util.List<NativeDwarf.Local> locals = new java.util.ArrayList<>();
                // no modelo cross toda entrada e slot de 8B (sem wide-slot do x86):
                // this + params ocupam os indices mais baixos na ordem da assinatura.
                int paramSlotMax = 1 + method.parameterTypes().size();
                for (IRLocalVariable lv : method.localVariables()) {
                    if (lv.name() == null || lv.name().isEmpty() || lv.name().startsWith("tmp")
                            || lv.name().startsWith("cap") || lv.name().startsWith("lambda$")) {
                        continue;
                    }
                    NativeDwarf.Local slot = new NativeDwarf.Local(lv.name(),
                            re.crossLocalOffRiscv(lv.index()), NativeDwarf.dwarfKindOf(lv.type()));
                    if (lv.name().equals("this") || lv.index() < paramSlotMax) {
                        params.add(slot);
                    } else {
                        locals.add(slot);
                    }
                }
                re.nb.kofDwarf.add(mangled, method.name(), declLine,
                        NativeDwarf.dwarfKindOf(method.returnType()), params, locals);
    }
}
