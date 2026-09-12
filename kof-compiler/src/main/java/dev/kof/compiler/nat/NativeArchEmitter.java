package dev.kof.compiler.nat;
import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofOperation;

import dev.kof.compiler.IRModule;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.Target;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** F3: emissão de arquivos .s riscv64/aarch64 (emitRiscv/emitAarch64). */
final class NativeArchEmitter {
    private final NativeBackend nb;
    NativeArchEmitter(NativeBackend nb) { this.nb = nb; }

    void emitRiscv(IRModule module, Path outputDir) throws IOException {
        nb.labelCounter = 0;
        nb.labelMap.clear();
        nb.stringLiterals.clear();
        nb.stringCounter = 0;
        nb.allClassesMap.clear();
        for (IRClass c : module.classes()) nb.allClassesMap.put(c.name(), c);

        IRClass mainClass = null;
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if ("main".equals(m.name())) { mainClass = c; break; }
            }
            if (mainClass != null) break;
        }
        // pré-registro do mangle de TODOS os métodos (forward reference de
        // função top-level — idem x86_64; sem isso `fib` cai no fallback
        // não-mangled e o ld falha).
        nb.functionMangleMap.clear();
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) continue;
                String mg = nb.fnSymbol(c.name(), m.name(), m.parameterTypes());
                nb.functionMangleMap.putIfAbsent(nb.fnKey(c.name(), m.name(), m.parameterTypes()), mg);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(".option arch, rv64g\n");
        sb.append(".section .data\n");
        for (IRClass c : module.classes()) {
            nb.currentClass = c;
            nb.collectStrings(c);
        }
        for (String[] e : nb.stringLiterals) {
            String esc = e[0].replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\t", "\\t");
            sb.append(e[1]).append(": .asciz \"").append(esc).append("\"\n");
        }
        // bug 59: símbolos de campos estáticos (ex: kof_static_java_lang_System_out)
        // referenciados por KofGetStatic no riscv/aarch precisam ser DEFINIDOS no
        // .data, senão o ld falha com "undefined reference". O x86_64 já emite via
        // emitStaticData; aqui faltava. `.quad`/`.asciz` são direções ELF universais.
        nb.collectStaticFields();
        nb.emitStaticData(sb);
        // kof_super_table: pares (typeId, superTypeId) terminados por (0,0) —
        // usado por kof_instanceof (mesmo layout do x86_64).
        sb.append(".align 4\n");
        sb.append("kof_super_table:\n");
        for (IRClass c : module.classes()) {
            if (c.typeId() == 0) continue;
            int superTypeId = 0;
            if (c.superName() != null && !c.superName().isEmpty()) {
                String superSimple = c.superName().substring(c.superName().lastIndexOf('/') + 1);
                for (IRClass other : module.classes()) {
                    if (other.name().equals(c.superName()) || other.name().endsWith("/" + superSimple)
                            || superSimple.equals(nb.sanitizeName(other.name()))) {
                        superTypeId = other.typeId();
                        break;
                    }
                }
            }
            sb.append("    .word ").append(c.typeId()).append(", ").append(superTypeId).append("\n");
        }
        sb.append("    .word 0, 0\n");
        // vtables por classe (offset 8 do header aponta para elas)
        for (IRClass c : module.classes()) {
            nb.currentClass = c;
            nb.crossEmit().emitMethodTableRiscv(sb, c);
        }
        sb.append(".section .text\n");
        // pop <reg>: desempilha o topo da pilha de operandos (sp) em <reg>
        sb.append(".macro pop r\n");
        sb.append("    ld \\r, 0(sp)\n");
        sb.append("    addi sp, sp, 8\n");
        sb.append(".endm\n");
        boolean usesSpawn = nb.usesSpawn(module);
        for (IRClass c : module.classes()) {
            nb.currentClass = c;
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) continue;
                nb.crossEmit().emitCrossMethodRiscv(sb, c, m, usesSpawn && "main".equals(m.name()));
            }
        }

        // Ponto de entrada: chama <mainClass>_main e sai via exit_group(94).
        // O runtime é asm puro — binário estático. exit_group (não exit/93)
        // mata as threads do scheduler que não foram canceladas (daemon-like)
        // — senão o processo fica pendurado esperando a thread do timer.
        String mainEntry = mainClass != null ? nb.sanitizeName(mainClass.name()) + "_main" : "kof_main";
        sb.append("\n.globl _start\n");
        sb.append("_start:\n");
        sb.append("    andi sp, sp, -16\n");
        sb.append("    call ").append(mainEntry).append("\n");
        sb.append("    li a0, 0\n");
        sb.append("    li a7, 94\n");
        sb.append("    ecall\n");
        int rtStart = sb.length();
        sb.append(NativeRiscvAsm.RISCV_RUNTIME_ASM).append(NativeRiscvAsm.RISCV_STRN002_ASM).append(NativeRiscvAsm.RISCV_RUNTIME_ASM_B).append(NativeRiscvAsm.RISCV_MAPSET_ASM);
        int rtEnd = sb.length();

        // NATIVE002-stdlib: http.get/post/status riscv64 (asm puro, syscalls
        // asm-generic — mesmos números do aarch64; aarch64 herda via tradutor).
        boolean usesHttp = false;
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    for (KofOperation op : b.operations()) {
                        if (op instanceof KofCall kc && kc.methodName().startsWith("kof_http_")) {
                            nb.usesHttp = true;
                        }
                    }
                }
                if (nb.usesHttp) break;
            }
            if (nb.usesHttp) break;
        }
        if (nb.usesHttp) nb.emitRiscvHttp(sb);
        if (usesSpawn) nb.emitRiscvSpawn(sb);

        String className = module.classes().isEmpty() ? "Default/Main" : module.classes().getFirst().name();
        Path asmFile = outputDir.resolve(className + ".s");
        Path binFile = outputDir.resolve(className);
        Files.createDirectories(asmFile.getParent());
        Files.writeString(asmFile, pruneRiscvRuntime(sb, rtStart, rtEnd, "riscv64"));
        System.err.println("NativeBackend: generated riscv64 " + asmFile);

        try {
            Path objFile = asmFile.resolveSibling("kof.o");
            // --no-relax (as+ld): sem gp-relaxation. Nosso _start não inicializa
            // gp (binário estático, sem C runtime); `la` relaxado vira `addi rd,gp,off`
            // e faulta (gp=0). Forçado PC-relative (auipc+addi) — sempre correto.
            nb.runCommand(new String[]{"riscv64-linux-gnu-as", "-mno-relax", "-o", objFile.toString(), asmFile.toString()}, "riscv64-as");
            nb.runCommand(new String[]{"riscv64-linux-gnu-ld", "--no-relax", "-o", binFile.toString(), objFile.toString()}, "riscv64-ld");
            Files.deleteIfExists(objFile);
            if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
            binFile.toFile().setExecutable(true);
        } catch (NativeAssembler.ToolchainMissing e) {
            // toolchain ausente: gracioso (assumeToolchain pula o teste)
            System.err.println("NativeBackend: riscv64 toolchain ausente (NATIVE002), keeping asm: " + e.getMessage());
        }
        // as/ld FALHOU (ex.: undefined reference) → propaga como erro de
        // compilação (R6: nunca success=true sem binário).
    }

    void emitAarch64(IRModule module, Path outputDir) throws IOException {
        // AArch64 = tradução linha-a-linha do riscv64 (mesmo modelo de pilha/layout).
        // Gera o asm riscv64 em memória via lowering já validado e traduz p/ ARMv8-A.
        nb.labelCounter = 0;
        nb.labelMap.clear();
        nb.stringLiterals.clear();
        nb.stringCounter = 0;
        nb.allClassesMap.clear();
        for (IRClass c : module.classes()) nb.allClassesMap.put(c.name(), c);
        IRClass mainClass = null;
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if ("main".equals(m.name())) { mainClass = c; break; }
            }
            if (mainClass != null) break;
        }
        nb.functionMangleMap.clear();
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) continue;
                String mg = nb.fnSymbol(c.name(), m.name(), m.parameterTypes());
                nb.functionMangleMap.putIfAbsent(nb.fnKey(c.name(), m.name(), m.parameterTypes()), mg);
            }
        }
        StringBuilder riscvSb = new StringBuilder();
        riscvSb.append(".option arch, rv64g\n");
        riscvSb.append(".section .data\n");
        for (IRClass c : module.classes()) {
            nb.currentClass = c;
            nb.collectStrings(c);
        }
        for (String[] e : nb.stringLiterals) {
            String esc = e[0].replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\t", "\\t");
            riscvSb.append(e[1]).append(": .asciz \"").append(esc).append("\"\n");
        }
        // bug 59: símbolos de campos estáticos definidos no .data (ver emitRiscv).
        nb.collectStaticFields();
        nb.emitStaticData(riscvSb);
        riscvSb.append(".align 4\n");
        riscvSb.append("kof_super_table:\n");
        for (IRClass c : module.classes()) {
            if (c.typeId() == 0) continue;
            int superTypeId = 0;
            if (c.superName() != null && !c.superName().isEmpty()) {
                String superSimple = c.superName().substring(c.superName().lastIndexOf('/') + 1);
                for (IRClass other : module.classes()) {
                    if (other.name().equals(c.superName()) || other.name().endsWith("/" + superSimple)
                            || superSimple.equals(nb.sanitizeName(other.name()))) {
                        superTypeId = other.typeId();
                        break;
                    }
                }
            }
            riscvSb.append("    .word ").append(c.typeId()).append(", ").append(superTypeId).append("\n");
        }
        riscvSb.append("    .word 0, 0\n");
        for (IRClass c : module.classes()) {
            nb.currentClass = c;
            nb.crossEmit().emitMethodTableRiscv(riscvSb, c);
        }
        riscvSb.append(".section .text\n");
        riscvSb.append(".macro pop r\n");
        riscvSb.append("    ld \\r, 0(sp)\n");
        riscvSb.append("    addi sp, sp, 8\n");
        riscvSb.append(".endm\n");
        boolean usesSpawnA = nb.usesSpawn(module);
        for (IRClass c : module.classes()) {
            nb.currentClass = c;
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) continue;
                nb.crossEmit().emitCrossMethodRiscv(riscvSb, c, m, usesSpawnA && "main".equals(m.name()));
            }
        }
        String mainEntry = mainClass != null ? nb.sanitizeName(mainClass.name()) + "_main" : "kof_main";
        riscvSb.append("\n.globl _start\n");
        riscvSb.append("_start:\n");
        riscvSb.append("    andi sp, sp, -16\n");
        riscvSb.append("    call ").append(mainEntry).append("\n");
        riscvSb.append("    li a0, 0\n");
        riscvSb.append("    li a7, 93\n");
        riscvSb.append("    ecall\n");
        int rtStart = riscvSb.length();
        riscvSb.append(NativeRiscvAsm.RISCV_RUNTIME_ASM).append(NativeRiscvAsm.RISCV_STRN002_ASM).append(NativeRiscvAsm.RISCV_RUNTIME_ASM_B).append(NativeRiscvAsm.RISCV_MAPSET_ASM);
        int rtEnd = riscvSb.length();

        // NATIVE002-stdlib: http riscv64 → aarch64 (traduzido). Mesma detecção
        // de uso do emitRiscv; o aarch64 herda linha-a-linha do riscv64.
        boolean usesHttpA = false;
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    for (KofOperation op : b.operations()) {
                        if (op instanceof KofCall kc && kc.methodName().startsWith("kof_http_")) {
                            usesHttpA = true;
                        }
                    }
                }
                if (usesHttpA) break;
            }
            if (usesHttpA) break;
        }
        if (usesHttpA) nb.emitRiscvHttp(riscvSb);
        if (usesSpawnA) nb.emitRiscvSpawn(riscvSb);

        // traduz linha-a-linha (runtime já podado — a poda no riscv vale p/ os 2)
        String prunedRiscv = pruneRiscvRuntime(riscvSb, rtStart, rtEnd, "aarch64");
        StringBuilder sb = new StringBuilder();
        for (String line : prunedRiscv.split("\n", -1)) {
            List<String> tr = NativeAarch64Translator.translateRiscvToAarch64(line);
            for (String t : tr) sb.append(t).append("\n");
        }
        String className = module.classes().isEmpty() ? "Default/Main" : module.classes().getFirst().name();
        Path asmFile = outputDir.resolve(className + ".s");
        Path binFile = outputDir.resolve(className);
        Files.createDirectories(asmFile.getParent());
        Files.writeString(asmFile, sb.toString());
        System.err.println("NativeBackend: generated aarch64 " + asmFile);
        try {
            Path objFile = asmFile.resolveSibling("kof.o");
            nb.runCommand(new String[]{"aarch64-linux-gnu-as", "-o", objFile.toString(), asmFile.toString()}, "aarch64-as");
            nb.runCommand(new String[]{"aarch64-linux-gnu-ld", "-o", binFile.toString(), objFile.toString()}, "aarch64-ld");
            Files.deleteIfExists(objFile);
            if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
            binFile.toFile().setExecutable(true);
        } catch (NativeAssembler.ToolchainMissing e) {
            System.err.println("NativeBackend: aarch64 toolchain ausente (NATIVE002), keeping asm: " + e.getMessage());
        }
        // as/ld FALHOU → propaga como erro de compilação (R6).
    }

    /** S-4.2 (issue #97, T1a.3): poda do runtime riscv64/aarch64 por
     *  alcançabilidade — o port riscv da S-3 x86. O texto do PROGRAMA (head
     *  .data/tabelas + métodos + _start + tail http/spawn, tudo fora de
     *  [rtStart,rtEnd)) é a FONTE DE SEEDS, varrido por `kof_*`/`.L*` raw
     *  (mesma regra da S-2.5/S-3: call sites reais sempre casam o regex).
     *  keep = piso obrigatório (print/panic/alloc) ∪ fecho UNIFICADO kof∪.L
     *  (medido: 33 arestas .L cross-peça no riscv — o fecho kof-only é
     *  INSEGURO aqui também). A concatenação riscv NÃO tem préâmbulo .text
     *  global: cada peça abre a própria seção (Rt0 .text, B4 .data/.bss,
     *  B5+ .text), então o tail — emitido DEPOIS por http/spawn via nb.*,
     *  já abre a sua; só o bloco mantido precisa fechar em .text para o
     *  append seguinte não herdar .data/.bss de uma peça podada no fim.
     *  keep == todas as peças → texto BYTE-IDÊNTICO ao de hoje (fallback
     *  pré-S-4, zero regressão). Mapa falho → runtime COMPLETO + stderr
     *  (R6: nunca link quebrado silencioso). aarch64: o chamador poda o
     *  riscvSb ANTES do tradutor — aarch herda a poda linha-a-linha. */
    static String pruneRiscvRuntime(StringBuilder sb, int rtStart, int rtEnd, String arch) {
        String all = sb.toString();
        try {
            String programText = all.substring(0, rtStart) + all.substring(rtEnd);
            java.util.Set<Integer> keep = RiscvSlices.keepForProgramText(programText);
            java.util.List<RiscvSlices.Piece> pieces = RiscvSlices.pieces();
            if (keep.size() >= pieces.size()) return all;
            String subset = RiscvSlices.renderSubset(keep);
            StringBuilder out = new StringBuilder(all.substring(0, rtStart));
            out.append(subset);
            if (!subset.endsWith("\n")) out.append('\n');
            out.append(".section .text\n");
            out.append(all.substring(rtEnd));
            System.err.println("NativeBackend: " + arch + " runtime prune " + keep.size() + "/"
                    + pieces.size() + " peças mantidas (" + (all.length() - out.length())
                    + " bytes podados)");
            return out.toString();
        } catch (RuntimeException e) {
            System.err.println("NativeBackend: " + arch + " runtime prune DESABILITADO (" + e
                    + ") — emitindo runtime completo (fallback seguro).");
            return all;
        }
    }

}