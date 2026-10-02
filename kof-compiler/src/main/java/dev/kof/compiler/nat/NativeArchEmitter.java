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

    /**
     * Resolve a ferramenta de cross (as/ld) com prefixo de diretorio via
     * {@code KOF_CROSS_PREFIX} — override de teste/ambiente, o MESMO padrao
     * da casa de {@code KOF_GDB}/{@code KOF_CROSS_SYSROOT}/{@code KOF_PUBLISH_API}:
     * sem a env, os nomes Debian de PATH (comportamento inalterado); com ela,
     * {@code <prefix>/<nome>} (stub de toolchain host-provavel; toolchain real
     * continua o caminho de producao/CI).
     */
    static String crossTool(String name) {
        String prefix = System.getenv("KOF_CROSS_PREFIX");
        return (prefix == null || prefix.isBlank()) ? name : Path.of(prefix, name).toString();
    }
    private final NativeBackend nb;
    NativeArchEmitter(NativeBackend nb) { this.nb = nb; }

    void emitRiscv(IRModule module, Path outputDir) throws IOException {
        nb.labelCounter = 0;
        nb.labelMap.clear();
        nb.stringLiterals.clear();
        nb.stringCounter = 0;
        // X7-2 fatia 2: registro DWARF do cross (frame_base = s11/x27).
        nb.kofDwarf.fns.clear();
        nb.kofDwarf.arch = NativeDwarf.Arch.RISCV64;
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
                String mg = NativeSymbolMangling.fnSymbol(c.name(), m, nb.allClassesMap);
                nb.functionMangleMap.putIfAbsent(NativeSymbolMangling.fnKey(c.name(), m, nb.allClassesMap), mg);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(".option arch, rv64g\n");
        // X7-2 (DWARF cross, fatia 1): a line table é arch-independente — o
        // GAS do riscv/aarch aceita `.file`/`.loc` idênticos ao x86 e gera o
        // .debug_line (gdb `break Main.kf:3` funciona). Os DIEs de subprogram
        // (frame_base por ABI: s11/x29) ficam para a fatia 2.
        if (nb.debugInfo) {
            sb.append(".file 1 \"").append(nb.sourceFile).append("\"\n");
        }
        sb.append(".section .data\n");
        // G-3 (NATIVE002 face 1): abertura do intervalo de raízes estáticas do
        // mark conservador riscv. Rótulo LOCAL (`.L`, fora do .symtab — a lição
        // do G-1 no ArtifactSizeTest), espelho riscv-only do #113 x86
        // (NativeBackend.emit:212). O sentinel .quad 0 é a 1ª palavra varrida
        // (nunca pointer-plausível, o mark ignora). O fecho (.Lkof_heap_root_end)
        // vem logo antes do .text dos métodos, excluindo a arena .bss do bump.
        sb.append(".Lkof_heap_root_start:\n");
        sb.append(".quad 0\n");
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
        NativeClassMeta.emitToStringTable(nb, sb);
        NativeClassMeta.emitEqualsTable(nb, sb);
        NativeClassMeta.emitHashCodeTable(nb, sb);
        // vtables por classe (offset 8 do header aponta para elas)
        for (IRClass c : module.classes()) {
            nb.currentClass = c;
            nb.crossEmit().emitMethodTableRiscv(sb, c);
        }
        // G-3: fecho do intervalo de raízes estáticas (ver abertura acima).
        // Ainda em .data, no ponto mais alto ANTES do .text: cobre literais,
        // campos estáticos, super_table e method tables — e NÃO a arena .bss.
        sb.append(".Lkof_heap_root_end:\n");
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
                nb.crossEmit().emitCrossMethodRiscv(sb, c, m, usesSpawn && "main".equals(m.name()));
            }
        }
        // #431 fatia 2: helper char*→String p/ extern com retorno String —
        // no texto do PROGRAMA (a poda só alcança o blob do runtime), em
        // plena seção .text; o aarch64 o recebe pela tradução linha-a-linha.
        if (nb.ffiUsesCstr) NativeFfiCallRiscv.emitRiscvCstrHelper(sb);
        if (nb.ffiUsesArray) NativeFfiCallRiscv.emitRiscvArrayPackHelper(sb);
        if (nb.ffiUsesStrArray) NativeFfiCallRiscv.emitRiscvStrArrayPackHelper(sb);

        // Ponto de entrada: chama <mainClass>_main e sai via exit_group(94).
        // O runtime é asm puro — binário estático. exit_group (não exit/93)
        // mata as threads do scheduler que não foram canceladas (daemon-like)
        // — senão o processo fica pendurado esperando a thread do timer.
        String mainEntry = mainClass != null ? nb.sanitizeName(mainClass.name()) + "_main" : "kof_main";
        sb.append("\n.globl _start\n");
        sb.append("_start:\n");
        sb.append("    andi sp, sp, -16\n");
        emitClinitCallsRiscv(sb, module);
        sb.append("    call ").append(mainEntry).append("\n");
        // #431: externs bindados → flusha o stdio da C antes do exit_group
        // cru (sem atexit a linha do puts da lib se perde — medição 19/09).
        if (!nb.ffiLibs.isEmpty()) {
            sb.append("    li a0, 0\n");   // fflush(NULL) — a0 lixo = SEGV
            sb.append("    call fflush\n");
        }
        sb.append("    li a0, 0\n");
        sb.append("    call kof_plat_exit_group\n");
        int rtStart = sb.length();
        sb.append(NativeRiscvAsm.RISCV_RUNTIME_ASM).append(NativeRiscvAsm.RISCV_STRN002_ASM).append(NativeRiscvAsm.RISCV_RUNTIME_ASM_B).append(NativeRiscvAsm.RISCV_MAPSET_ASM);
        int rtEnd = sb.length();
        // Fora de [rtStart,rtEnd): o pruner reconstrói a região a partir de
        // RiscvSlices (peças fixas) e descartaria texto avulso; o resolver
        // por-programa precisa sobreviver (referenciado pelo RtB57 find).
        NativeOrmCtors.collect(nb, module.classes());
        if (!nb.ormCtorClasses.isEmpty()) sb.append(NativeRiscvOrmCtors.emit(nb, nb.ormCtorClasses));
        // NATIVE002-stdlib: http.get/post/status riscv64 (asm puro, syscalls
        // asm-generic — mesmos números do aarch64; aarch64 herda via tradutor).
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
        if (nb.debugInfo) nb.kofDwarf.emit(sb, nb.sourceFile);
        String prunedRiscv = pruneRiscvRuntime(sb, rtStart, rtEnd, "riscv64");
        Files.writeString(asmFile, prunedRiscv);
        System.err.println("NativeBackend: generated riscv64 " + asmFile);

        // link dinâmico SOB DEMANDA (diretriz 15/09): estático p/ sempre até o
        // runtime (podado) referenciar libc/libsqlite3; aí vira -lc/-lsqlite3 +
        // --dynamic-linker. DB001: o consumidor SQLite arrasta a libc junto.
        boolean sqlite = NativeCrossLink.needsSqlite(prunedRiscv);
        // #431: extern BINDA — a `library()` vira input do ld cross (link-by-use,
        // DB001) e força o dinâmico (sem ela o `call sym` não resolve).
        boolean ffi = !nb.ffiLibs.isEmpty();
        boolean libm = NativeCrossLink.needsLibm(prunedRiscv);
        // §545: spawn usa `_dl_allocate_tls` (loader) para o tp do worker —
        // só existe no link dinâmico, então um programa com spawn força -lc.
        boolean dynamic = sqlite || ffi || usesSpawn || NativeCrossLink.needsLibc(prunedRiscv);
        String sysroot = NativeCrossLink.sysrootFor("riscv64");
        if (dynamic && sysroot == null) {
            // R6: sem libc-cross não há como ligar dinâmico — segue estático,
            // mas avisa (o consumidor libc ficará sem .so → provável ld aborta,
            // que já é propagado como erro de compilação).
            System.err.println("NativeBackend: riscv64 needs libc but KOF_CROSS_SYSROOT/" +
                    "/tmp/opencode/x/usr/riscv64-linux-gnu missing — trying static link");
        }
        if (sqlite && !NativeCrossLink.sqliteAvailable("riscv64")) {
            System.err.println("NativeBackend: riscv64 uses kof.db but libsqlite3.so is not in the " +
                    "sysroot (CI installs only libc6-*-cross) — ld will abort with undefined reference");
        }
        if (dynamic) System.err.println("NativeBackend: riscv64 dynamic link (" +
                (sqlite ? "libc+libsqlite3 detected" : "libc detected") + (ffi ? " +ffi libs" : "")
                + (libm ? " +libm" : "") + ")");

        try {
            Path objFile = asmFile.resolveSibling("kof.o");
            // --no-relax SÓ no ld (NativeCrossLink.ldArgs): a gp-relaxation que
            // quebra `la`→`addi rd,gp,off` com gp=0 é LINK-time; o `-mno-relax`
            // no `as` fazia o GAS ligar branch local à frente SEM relocação
            // (`.L*` definido em outra `.section .text.<fn>` da S-5 vira `j .`
            // silencioso — §445). PC-relative (auipc+addi) sempre correto.
            nb.runCommand(new String[]{crossTool("riscv64-linux-gnu-as"), "-o", objFile.toString(), asmFile.toString()}, "riscv64-as");
            // S-5 (cross): --gc-sections remove as seções .text.<fn> mortas
            // criadas por sectionizeTextFunctions. Seguro aqui: NÃO existe GC
            // no asm riscv/aarch (bump-pointer, sem scan conservative) — nada
            // vivo pode depender de símbolo sem reloc. O x86 continua sem
            // gc-sections até a fase `kof_heap_root_end` (root-scan varre
            // root_start.._end; seção deletada fora do intervalo = raiz que
            // o coletor nunca vê — precisa primeiro o fim explícito).
            nb.runCommand(NativeCrossLink.ldArgs(crossTool("riscv64-linux-gnu-ld"), binFile, objFile,
                    "riscv64", dynamic, sysroot, sqlite, nb.ffiLibs, libm), "riscv64-ld");
            Files.deleteIfExists(objFile);
            if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
            binFile.toFile().setExecutable(true);
        } catch (NativeAssembler.ToolchainMissing e) {
            // toolchain ausente: gracioso (assumeToolchain pula o teste)
            System.err.println("NativeBackend: riscv64 toolchain missing (NATIVE002), keeping asm: " + e.getMessage());
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
        // X7-2 fatia 2: idem riscv, mas o frame_base do DIE ja sai codificado
        // p/ fp=x29 do ARM (a traducao repassa as diretivas `.` verbatim).
        nb.kofDwarf.fns.clear();
        nb.kofDwarf.arch = NativeDwarf.Arch.AARCH64;
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
                String mg = NativeSymbolMangling.fnSymbol(c.name(), m, nb.allClassesMap);
                nb.functionMangleMap.putIfAbsent(NativeSymbolMangling.fnKey(c.name(), m, nb.allClassesMap), mg);
            }
        }
        StringBuilder riscvSb = new StringBuilder();
        riscvSb.append(".option arch, rv64g\n");
        // X7-2 (DWARF cross, fatia 1): idem emitRiscv — `.file`/`.loc` passam
        // pelo tradutor aarch64 intactos (diretivas `.` são verbatim) e o GAS
        // ARM gera o .debug_line.
        if (nb.debugInfo) {
            riscvSb.append(".file 1 \"").append(nb.sourceFile).append("\"\n");
        }
        riscvSb.append(".section .data\n");
        // G-3: abertura do intervalo de raízes estáticas riscv (o aarch64 herda
        // via tradutor). Ver comentário em emitRiscv.
        riscvSb.append(".Lkof_heap_root_start:\n");
        riscvSb.append(".quad 0\n");
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
        NativeClassMeta.emitToStringTable(nb, riscvSb);
        NativeClassMeta.emitEqualsTable(nb, riscvSb);
        NativeClassMeta.emitHashCodeTable(nb, riscvSb);
        for (IRClass c : module.classes()) {
            nb.currentClass = c;
            nb.crossEmit().emitMethodTableRiscv(riscvSb, c);
        }
        // G-3: fecho do intervalo de raízes estáticas (ver emitRiscv).
        riscvSb.append(".Lkof_heap_root_end:\n");
        riscvSb.append(".section .text\n");
        riscvSb.append(".macro pop r\n");
        riscvSb.append("    ld \\r, 0(sp)\n");
        riscvSb.append("    addi sp, sp, 8\n");
        riscvSb.append(".endm\n");
        boolean usesSpawnA = nb.usesSpawn(module);
        for (IRClass c : module.classes()) {
            nb.currentClass = c;
            for (IRMethod m : c.methods()) {
                nb.crossEmit().emitCrossMethodRiscv(riscvSb, c, m, usesSpawnA && "main".equals(m.name()));
            }
        }
        // #431 fatia 2: idem riscv — o helper entra ANTES da tradução p/ o
        // ARM (linhas todas cobertas pelo tradutor: beqz/lbu/j/mv/li/sd/ld/call/ret).
        if (nb.ffiUsesCstr) NativeFfiCallRiscv.emitRiscvCstrHelper(riscvSb);
        if (nb.ffiUsesArray) NativeFfiCallRiscv.emitRiscvArrayPackHelper(riscvSb);
        if (nb.ffiUsesStrArray) NativeFfiCallRiscv.emitRiscvStrArrayPackHelper(riscvSb);
        String mainEntry = mainClass != null ? nb.sanitizeName(mainClass.name()) + "_main" : "kof_main";
        riscvSb.append("\n.globl _start\n");
        riscvSb.append("_start:\n");
        riscvSb.append("    andi sp, sp, -16\n");
        emitClinitCallsRiscv(riscvSb, module);
        riscvSb.append("    call ").append(mainEntry).append("\n");
        if (!nb.ffiLibs.isEmpty()) {
            riscvSb.append("    li a0, 0\n");   // fflush(NULL) — idem riscv
            riscvSb.append("    call fflush\n");
        }
        riscvSb.append("    li a0, 0\n");
        // #431 (achado na lane do flush): exit_group (94), não exit/93 —
        // com 93 a thread do scheduler (time.interval) ou as threads internas
        // de uma lib C (GLFW/raylib) sobrevivem ao main e o processo NUNCA
        // morre (hang medido sob qemu antes do fix; o x86 e o riscv já
        // usavam 94 — M32.3). Números riscv/aarch idênticos (asm-generic).
        // B-0 (D-BAREMETAL-BOOT): a saída cruza a costura kof_plat_exit_group.
        riscvSb.append("    call kof_plat_exit_group\n");
        int rtStart = riscvSb.length();
        riscvSb.append(NativeRiscvAsm.RISCV_RUNTIME_ASM).append(NativeRiscvAsm.RISCV_STRN002_ASM).append(NativeRiscvAsm.RISCV_RUNTIME_ASM_B).append(NativeRiscvAsm.RISCV_MAPSET_ASM);
        int rtEnd = riscvSb.length();
        // Fora de [rtStart,rtEnd): sobrevive ao pruner (ver emitRiscv) e entra
        // no riscv ANTES do tradutor — o aarch64 herda linha-a-linha.
        NativeOrmCtors.collect(nb, module.classes());
        if (!nb.ormCtorClasses.isEmpty()) riscvSb.append(NativeRiscvOrmCtors.emit(nb, nb.ormCtorClasses));

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
        if (nb.debugInfo) nb.kofDwarf.emit(riscvSb, nb.sourceFile);
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
        boolean sqlite = NativeCrossLink.needsSqlite(prunedRiscv);
        boolean ffi = !nb.ffiLibs.isEmpty();
        boolean libm = NativeCrossLink.needsLibm(prunedRiscv);
        // §545: idem riscv — spawn usa `_dl_allocate_tls`, só no dinâmico.
        boolean dynamic = sqlite || ffi || usesSpawnA || NativeCrossLink.needsLibc(prunedRiscv);
        String sysroot = NativeCrossLink.sysrootFor("aarch64");
        if (sqlite && !NativeCrossLink.sqliteAvailable("aarch64")) {
            System.err.println("NativeBackend: aarch64 uses kof.db but libsqlite3.so is not in the " +
                    "sysroot (CI installs only libc6-*-cross) — ld will abort with undefined reference");
        }
        if (dynamic) System.err.println("NativeBackend: aarch64 dynamic link (" +
                (sqlite ? "libc+libsqlite3 detected" : "libc detected") + (ffi ? " +ffi libs" : "")
                + (libm ? " +libm" : "") + ")");
        try {
            Path objFile = asmFile.resolveSibling("kof.o");
            nb.runCommand(new String[]{crossTool("aarch64-linux-gnu-as"), "-o", objFile.toString(), asmFile.toString()}, "aarch64-as");
            nb.runCommand(NativeCrossLink.ldArgs(crossTool("aarch64-linux-gnu-ld"), binFile, objFile,
                    "aarch64", dynamic, sysroot, sqlite, nb.ffiLibs, libm), "aarch64-ld");
            Files.deleteIfExists(objFile);
            if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
            binFile.toFile().setExecutable(true);
        } catch (NativeAssembler.ToolchainMissing e) {
            System.err.println("NativeBackend: aarch64 toolchain missing (NATIVE002), keeping asm: " + e.getMessage());
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
            String subset = NativeCrossSections.sectionizeTextFunctions(RiscvSlices.renderSubset(keep));
            StringBuilder out = new StringBuilder(all.substring(0, rtStart));
            out.append(subset);
            if (!subset.endsWith("\n")) out.append('\n');
            out.append(".section .text\n");
            out.append(all.substring(rtEnd));
            System.err.println("NativeBackend: " + arch + " runtime prune " + keep.size() + "/"
                    + pieces.size() + " pieces kept (" + (all.length() - out.length())
                    + " bytes pruned)");
            return out.toString();
        } catch (RuntimeException e) {
            System.err.println("NativeBackend: " + arch + " runtime prune DESABILITADO (" + e
                    + ") — emitindo runtime completo (fallback seguro).");
            return all;
        }
    }

    /** #133 (§186): chama cada <clinit> do módulo antes do main (riscv64/aarch64). */
    private void emitClinitCallsRiscv(StringBuilder sb, IRModule module) {
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) {
                    sb.append("    call ").append(NativeSymbolMangling.fnSymbol(
                            c.name(), m.name(), m.parameterTypes(), nb.allClassesMap)).append("\n");
                }
            }
        }
    }
}
