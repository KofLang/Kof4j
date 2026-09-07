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
                String mg = nb.sanitizeName(c.name()) + "_" + nb.sanitizeName(m.name());
                if ("<init>".equals(m.name())) mg += "_" + m.parameterTypes().size();
                nb.functionMangleMap.putIfAbsent(m.name(), mg);
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
        sb.append(NativeRiscvAsm.RISCV_RUNTIME_ASM).append(NativeRiscvAsm.RISCV_STRN002_ASM).append(NativeRiscvAsm.RISCV_RUNTIME_ASM_B).append(NativeRiscvAsm.RISCV_MAPSET_ASM);

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
        Files.writeString(asmFile, sb.toString());
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
                String mg = nb.sanitizeName(c.name()) + "_" + nb.sanitizeName(m.name());
                if ("<init>".equals(m.name())) mg += "_" + m.parameterTypes().size();
                nb.functionMangleMap.putIfAbsent(m.name(), mg);
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
        riscvSb.append(NativeRiscvAsm.RISCV_RUNTIME_ASM).append(NativeRiscvAsm.RISCV_STRN002_ASM).append(NativeRiscvAsm.RISCV_RUNTIME_ASM_B).append(NativeRiscvAsm.RISCV_MAPSET_ASM);

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

        // traduz linha-a-linha
        StringBuilder sb = new StringBuilder();
        for (String line : riscvSb.toString().split("\n", -1)) {
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

}