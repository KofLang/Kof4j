package dev.kof.compiler.nat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FASE 3 (REFACTOR-500): invocação do as/ld nativo (x86_64 host).
 * Extraído verbatim de NativeBackend (assemble/runCommand/ToolchainMissing);
 * os flags de link (db/mysql/concurrency) viram parâmetros.
 */
public final class NativeAssembler {

    private NativeAssembler() {}

    /** Toolchain ausente (binário não encontrado) — gracioso: mantém asm,
     *  assumeToolchain() pula o teste. NÃO confundir com falha de as/ld. */
    static final class ToolchainMissing extends IOException {
        ToolchainMissing(String m) { super(m); }
    }

    /** #431: `ffiLibs` = as `library()` dos `extern` bound (link-by-use). Um
     *  caminho (contém '/') entra como input posicional do ld; um soname vira
     *  `-l:<nome>` — exatamente o padrão do SQLite (DB001), sem dlopen. */
    static void assemble(Path asmFile, Path binFile, boolean usesDb,
                   boolean usesConcurrency, java.util.Collection<String> ffiLibs,
                   boolean usesPow, boolean freestanding) throws IOException {
        Path objFile = asmFile.resolveSibling(asmFile.getFileName() + ".o");
        boolean bare = freestanding && System.getProperty("os.name", "").toLowerCase().contains("linux");
        Path asmToAssemble = asmFile;
        if (bare) {
            // B-1b: seção por FUNÇÃO no .text (reusa NativeCrossSections, o
            // mesmo passe do cross, lição §445) → o ld com --gc-sections
            // descarta o que o programa não alcança. Com a face (i) do B-1b
            // (panic imprime string, sem dispatcher genérico), o hello deixa
            // de arrastar dtoa/pthread/usleep — o link fecha SEM libc e SEM
            // `--unresolved-symbols=ignore-all`.
            asmToAssemble = asmFile.resolveSibling(asmFile.getFileName() + ".bare.s");
            Files.writeString(asmToAssemble,
                    NativeCrossSections.sectionizeTextFunctions(Files.readString(asmFile), "kof_"));
        }
        System.err.println("NativeBackend: assembling " + asmFile);
        try {
            runCommand(new String[]{"as", "-o", objFile.toString(), asmToAssemble.toString()}, "as");
        } catch (IOException e) {
            System.err.println("NativeBackend: as failed: " + e.getMessage());
            throw e;
        }
        // B-1: perfil freestanding (x86_64) — link ESTÁTICO, sem
        // `-dynamic-linker` e sem `-lc`; o binário só fala com o SO pela
        // costura kof_plat_* (B-0). As capacidades libc-dependentes já foram
        // recusadas em NativeBackend.assemble (NATIVE003). B-1b: as refs libc
        // de funções não-alcançadas morrem no `--gc-sections`.
        if (bare) {
            try {
                java.util.List<String> ldCmd = new java.util.ArrayList<>(java.util.Arrays.asList(
                        "ld", "-o", binFile.toString(), objFile.toString(), "--gc-sections"));
                Path ldScript = null;
                // B-1 (23/09): no perfil FREESTANDING entra o linker script
                // próprio — `_end` explícito (topo da varredura de raízes
                // estáticas do GC, ANTES da arena) + arena de heap e pilha de
                // tamanho configurável (env KOF_HEAP_SIZE/KOF_STACK_SIZE).
                // O UEFI (B-2) segue com o script default do ld + objcopy: a
                // conversão PE32+ depende do layout que ele já produz.
                if (!NativeProfile.activeIsUefi()) {
                    ldScript = asmFile.resolveSibling(asmFile.getFileName() + ".ld");
                    Files.writeString(ldScript, NativeProfile.active.isBios()
                            ? biosLinkerScript() : freestandingLinkerScript());
                    ldCmd.add("-T");
                    ldCmd.add(ldScript.toString());
                }
                ldCmd.add("-e");
                ldCmd.add("_start");
                runCommand(ldCmd.toArray(new String[0]), "ld");
                if (ldScript != null) Files.deleteIfExists(ldScript);
            } catch (IOException e) {
                // R6: no perfil freestanding nenhuma capacidade libc entra —
                // se sobrou símbolo libc (ex.: `println(Double)` alcança
                // snprintf/strtod do dtoa), a recusa é NOMEADA, não um
                // "undefined reference" cru do ld.
                if (String.valueOf(e.getMessage()).contains("undefined reference")) {
                    throw new IOException("NATIVE003: perfil freestanding nao suporta libc — "
                            + "o programa usa uma capacidade libc (float-print/db/concurrency/pow/ffi); "
                            + "use o perfil host ou remova a dependencia. Detalhe: " + e.getMessage());
                }
                throw e;
            }
            // B-2: perfil UEFI — o artefato final é PE32+ (EFI application):
            // ld produz um ELF estático intermediário e o objcopy converte
            // (a receita medida no OVMF: seções .text/.rodata/.data/.bss/.reloc,
            // subsystem 10; o .reloc dummy de 10 bytes vem do runtime
            // (RuntimeUefi) — o loader EDK2 exige dir de relocs não-vazio).
            if (NativeProfile.activeIsUefi()) {
                Path elfFile = binFile.resolveSibling(binFile.getFileName() + ".elf");
                Files.move(binFile, elfFile);
                try {
                    runCommand(new String[]{"objcopy",
                            // globs: o B-1b sectioniza o .text em
                            // .text.kof_<fn> — o -j é match exato, o glob
                            // pega as seções por função (medição B-2).
                            "-j", ".text*", "-j", ".rodata*", "-j", ".data*",
                            "-j", ".bss*", "-j", ".reloc",
                            "--target", "pei-x86-64", "--subsystem", "10",
                            elfFile.toString(), binFile.toString()}, "objcopy");
                } finally {
                    Files.deleteIfExists(elfFile);
                }
            }
            // B-3: perfil BIOS — a imagem final é um binário FLAT (o setor de
            // boot começa em 0x7C00 e leva a assinatura 0xAA55 em 0x1FE). O ld
            // produz o ELF e o objcopy --output-target=binary remove o
            // embrulho, preservando o offset do setor (LMA do .text.boot).
            if (NativeProfile.active.isBios()) {
                Path elfFile = binFile.resolveSibling(binFile.getFileName() + ".elf");
                Files.move(binFile, elfFile);
                try {
                    runCommand(new String[]{"objcopy",
                            "--output-target", "binary", elfFile.toString(), binFile.toString()},
                            "objcopy");
                } finally {
                    Files.deleteIfExists(elfFile);
                }
                // B-3b-3: escreve a contagem de setores no header KOFPAYLD.
                patchBiosPayloadHeader(binFile);
            }
            Files.deleteIfExists(objFile);
            Files.deleteIfExists(asmToAssemble);
            if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
            binFile.toFile().setExecutable(true);
            return;
        }
        // Native always needs dynamic linker + libc now (printf for float, db optionally)
        // to keep single codegen path; plain integer programs still work via ld+ld.so.
        boolean needsDynamic = true;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (needsDynamic && os.contains("linux")) {
            java.util.List<String> cmdL = new java.util.ArrayList<>(java.util.Arrays.asList(
                    "ld", "-o", binFile.toString(), objFile.toString(),
                    "-dynamic-linker", "/lib64/ld-linux-x86-64.so.2", "-lc"));
            if (usesDb) {
                cmdL.add("-l:libsqlite3.so.0");
                // §523 (27/09): sem `-l:libmariadb.so.3` — o wire mysql e
                // auto-contido (sockets + SHA1 proprios; zero simbolo
                // mariadb/mysql no binario, medido via readelf) desde sempre;
                // a flag so quebrava o link em host sem a lib (CI) e criava
                // NEEDED fantasma no load. Precedente S5.4 no cross (moot).
            }
            if (usesConcurrency) cmdL.add("-l:libpthread.so.0");
            // R2 fatia 1 (20/09): pow → libm só POR USO (decisão 7a mantida;
            // o EAGER "sempre ligado" caducou — o shim `call pow` do monolito
            // é FRACO desde aqui: `.weak pow` em RuntimeMath, então linka sem
            // libm e o simbolo nunca e alcancado quando usesPow=false, porque
            // o unicos call-sites nascem do scan usesPow no NativeBackend).
            // Recusa em riscv/aarch = KofMath.supportedOn (MATH001) — la o
            // link e estatico sem libc. Prova: LinkByUseTest (readelf medido).
            if (usesPow) cmdL.add("-lm");
            // #431: as libs dos `extern` bound entram no link (posicional se é
            // caminho, `-l:` se é soname). Arquivo ausente → erro honesto do ld
            // (nunca um binário que resolve em runtime pra faltar).
            // decisão F (08/10): o dir do próprio extern entra na busca do ld
            // ANTES das libs — os DT_NEEDED do .so (as deps dele) resolvem da
            // árvore do vendor antes dos caminhos default (a distro empilha
            // versões de ffmpeg conflitantes; sem isto o link acha a errada).
            // SDL3/soname sem dir: inócuo (deps já no path default). O -L vem
            // ANTES: o ld resolve os DT_NEEDED contra a busca ACUMULADA até o
            // ponto em que o .so é aberto.
            java.util.Set<String> libDirs = new java.util.LinkedHashSet<>();
            for (String lib : ffiLibs) {
                if (lib.indexOf('/') >= 0) {
                    Path parent = Path.of(lib).getParent();
                    if (parent != null) libDirs.add(parent.toString());
                }
            }
            for (String dir : libDirs) {
                cmdL.add("-rpath-link");
                cmdL.add(dir);
            }
            for (String lib : ffiLibs) {
                cmdL.add(lib.indexOf('/') >= 0 ? lib : "-l:" + lib);
            }
            runCommand(cmdL.toArray(new String[0]), "ld");
        } else {
            if (usesDb) {
                String os2 = System.getProperty("os.name", "").toLowerCase();
                if (os2.contains("linux")) {
                    String[] extra = new String[]{"-l:libsqlite3.so.0"};
                    String[] cmd = new String[7 + extra.length];
                    cmd[0] = "ld"; cmd[1] = "-o"; cmd[2] = binFile.toString(); cmd[3] = objFile.toString();
                    cmd[4] = "-dynamic-linker"; cmd[5] = "/lib64/ld-linux-x86-64.so.2"; cmd[6] = "-lc";
                    System.arraycopy(extra, 0, cmd, 7, extra.length);
                    runCommand(cmd, "ld");
                } else {
                    runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
                }
            } else {
                runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
            }
        }
        Files.deleteIfExists(objFile);
        if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
        binFile.toFile().setExecutable(true);
    }

    /** B-1 (23/09): linker script do perfil FREESTANDING. {@code _end} fecha a
     *  {@code .bss} REAL (topo da varredura de raízes estáticas do GC na
     *  {@code RuntimeGc}) ANTES da arena; a arena {@code __kof_heap_*} e a
     *  pilha {@code __kof_stack_*} ficam na MESMA PT_LOAD NOBITS (zero-fill do
     *  kernel) — o tamanho é configurável por {@code KOF_HEAP_SIZE}/
     *  {@code KOF_STACK_SIZE} (ou props {@code kof.heap.size}/
     *  {@code kof.stack.size}), senão 8 MiB/1 MiB. */
    private static String freestandingLinkerScript() {
        long heap = freestandingSize("KOF_HEAP_SIZE", "kof.heap.size", 8L * 1024 * 1024);
        long stack = freestandingSize("KOF_STACK_SIZE", "kof.stack.size", 1L * 1024 * 1024);
        return "ENTRY(_start)\n"
                + "SECTIONS\n{\n"
                + "  . = 0x400000;\n"
                + "  .text : { *(.text*) }\n"
                + "  .rodata : { *(.rodata*) }\n"
                + "  .data : { *(.data*) }\n"
                + "  .bss : {\n"
                + "    *(.bss*) *(COMMON)\n"
                + "    . = ALIGN(16);\n"
                + "    _end = .;\n"
                + "    __kof_heap_start = .;\n"
                + "    . += " + heap + ";\n"
                + "    __kof_heap_end = .;\n"
                + "    . = ALIGN(16);\n"
                + "    __kof_stack_bottom = .;\n"
                + "    . += " + stack + ";\n"
                + "    __kof_stack_top = .;\n"
                + "  }\n"
                + "  /DISCARD/ : { *(.note*) *(.comment) *(.eh_frame*) }\n"
                + "}\n";
    }

    /** B-3 (23/09): linker script do perfil BIOS. O setor de boot
     *  ({@code .text.boot}) é a PRIMEIRA seção, carregada pelo firmware em
     *  {@code 0x7C00}; a assinatura {@code 0xAA55} em 0x1FE vem do
     *  {@code .org 510} no próprio {@code _start} (NativeMethodEmitter).
     *  B-3b: {@code .boot2} (stage2) é forçado ao LMA {@code 0x7E00} (= setor
     *  LBA 1), para o próprio setor de boot carregá-lo do disco; {@code KEEP}
     *  impede o gc-sections de descartá-lo.
     *  B-3b-3: o PROGRAMA Kof é ligado na VMA {@code 0x100000} (a base fixa
     *  que o boot mapeia) com LMA em AT(...) — o arquivo flat fica compacto
     *  (setor 2 em diante) enquanto o código enxerga os endereços finais. A
     *  arena (heap+pilha) vive no .bss da VMA; o boot mapeia 32 MiB de
     *  páginas de 2 MiB para cobrir payload+arena. */
    private static String biosLinkerScript() {
        long heap = freestandingSize("KOF_HEAP_SIZE", "kof.heap.size", 512L * 1024);
        long stack = freestandingSize("KOF_STACK_SIZE", "kof.stack.size", 64L * 1024);
        // Layout DUAS FASES (B-3b-3): o stub (.text.boot) carrega o stage2
        // (.boot2, LBAs 1..4 -> 0x7E00, contíguo ao setor 0) que carrega o
        // header (.payload, VMA 0xC000 = LBA 34) e o programa (VMA 0x100000 =
        // arquivo 0xF8400 = LBA 1986). SEM AT(): o ld 2.42 segfaulta com LMA<VMA divergentes; o
        // objcopy flat usa os próprios VMAs (a imagem tem ~1 MiB de padding de
        // zeros — aceitável para o disco raw do qemu). O programa enxerga seus
        // endereços finais direto (VMA = endereço de execução em 0x100000); a
        // arena (heap+pilha) vive no .bss da VMA.
        return "ENTRY(_start)\n"
                + "SECTIONS\n{\n"
                + "  . = 0x" + Integer.toHexString(NativeBiosBootEmitter.BOOT_BASE) + ";\n"
                + "  .text.boot : { KEEP(*(.text.boot)) }\n"
                + "  . = 0x7E00;\n"
                + "  .boot2 : { KEEP(*(.boot2)) }\n"
                + "  . = 0x" + Integer.toHexString(NativeBiosBootEmitter.HEADER_VMA) + ";\n"
                + "  .payload : { KEEP(*(.payload)) }\n"
                + "  . = 0x" + Integer.toHexString(NativeBiosBootEmitter.PROGRAM_VMA) + ";\n"
                + "  .text : { *(.text*) }\n"
                + "  .rodata : { *(.rodata*) }\n"
                + "  .data : { *(.data*) }\n"
                + "  .bss : {\n"
                + "    *(.bss*) *(COMMON)\n"
                + "    . = ALIGN(16);\n"
                + "    _end = .;\n"
                + "    __kof_heap_start = .;\n"
                + "    . += " + heap + ";\n"
                + "    __kof_heap_end = .;\n"
                + "    . = ALIGN(16);\n"
                + "    __kof_stack_bottom = .;\n"
                + "    . += " + stack + ";\n"
                + "    __kof_stack_top = .;\n"
                + "  }\n"
                + "  /DISCARD/ : { *(.note*) *(.comment) *(.eh_frame*) }\n"
                + "}\n";
    }

    /**
     * B-3b-3: patch da CONTAGEM de setores do payload no header KOFPAYLD.
     * O tamanho só existe APÓS o link, então o header (VMA 0xC000 = arquivo
     * 0x4400 = LBA 34) leva um int LE em +8 escrito depois do objcopy: a
     * leitura 16-bit do boot usa o campo para ler os setores do programa
     * (VMA 0x100000 = arquivo 0xF8400 = LBA 1986, em diante). Falha NOMEADA se
     * a magia sumir (regressão do KEEP/gc) ou o payload estourar o staging de
     * modo real (< ~584 KiB de 0xC200 a 0xA0000).
     */
    private static void patchBiosPayloadHeader(Path binFile) throws IOException {
        byte[] img = Files.readAllBytes(binFile);
        int idx = -1;
        for (int i = 0; i + 8 <= img.length && i < 0x5000; i++) {
            if (img[i] == 'K' && img[i + 1] == 'O' && img[i + 2] == 'F' && img[i + 3] == 'P'
                    && img[i + 4] == 'A' && img[i + 5] == 'Y' && img[i + 6] == 'L' && img[i + 7] == 'D') {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            throw new IOException("KO-BIOS: header KOFPAYLD ausente na imagem flat "
                    + "(a seção .payload saiu do link? KEEP/gc-sections regressou)");
        }
        long payloadBytes = img.length - NativeBiosBootEmitter.PROGRAM_FILE_OFF;
        long sectors = (payloadBytes + 511) / 512;
        if (sectors < 1 || sectors > 0x4FF) {   // 0x4FF*512 ≈ 584 KiB (staging 0xC200..0xA0000)
            throw new IOException("KO-BIOS: payload de " + payloadBytes + " bytes (" + sectors
                    + " setores) fora do staging de modo real (< ~584 KiB); KOF_HEAP_SIZE/KOF_STACK_SIZE reduzem a arena");
        }
        for (int b = 0; b < 4; b++) img[idx + 8 + b] = (byte) ((sectors >>> (8 * b)) & 0xFF);
        Files.write(binFile, img);
    }

    /** Tamanho da região do script: env → prop → default (sempre > 0). */
    private static long freestandingSize(String env, String prop, long fallback) {
        String raw = System.getenv(env);
        if (raw == null || raw.isBlank()) raw = System.getProperty(prop);
        if (raw != null && !raw.isBlank()) {
            try {
                long v = Long.parseLong(raw.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {
                // valor inválido → default (o link segue determinístico)
            }
        }
        return fallback;
    }

    static void runCommand(String[] cmd, String name) throws IOException {
        Process p;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            p = pb.start();
        } catch (IOException e) {
            throw new ToolchainMissing(name + " not available: " + e.getMessage());
        }
        try {
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            if (p.exitValue() != 0) {
                throw new IOException(name + " failed (exit " + p.exitValue() + "): " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(name + " interrupted");
        }
    }
}
