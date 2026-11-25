package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-6 (#1135, D-NATIVE-MACOS) — o CACHE do runtime sem o endereçamento
 * absoluto-indexado.
 *
 * <p><b>O DEFEITO (medido no assembler do Mach-O):</b> o cache acessava as
 * suas tabelas globais com a forma ELF {@code sym(,%r12,8)} — o
 * endereçamento ABSOLUTO com índice. O Mach-O (x86-64) não tem essa forma: o
 * assembler real recusa com "32-bit absolute addressing is not supported in
 * 64-bit mode". A forma que vale nos DOIS alvos é a base RIP-relativa mais o
 * índice: {@code leaq sym(%rip), %r11} e {@code movq (%r11,%r12,8), %rax}.
 *
 * <p><b>POR QUE NO EMISSOR, e não no tradutor:</b> a reescrita no tradutor
 * exigiria saber quais registradores estão vivos — o tradutor é linha a
 * linha. No emissor o autor do código sabe: o cache não usa o {@code %r11}
 * (medido: zero ocorrências), então ele é o rascunho.
 */
class NativeCacheRipRelativeE2ETest {

    private static final String CACHE_PROGRAM = """
            main() {
                cache.set("name", "Mel")
                println(cache.get("name"))
                cache.set("name", "Ana")
                println(cache.get("name"))
                println(cache.get("missing"))
                cache.set("t", "x", 1)
                println(cache.ttl("t") >= 0 && cache.ttl("t") <= 1)
                println(cache.get("t"))
                cache.delete("name")
                println(cache.get("name"))
            }
            """;

    /** O texto do RUNTIME no perfil pedido (sem passar pelo compilador). */
    private static String runtime(String profile) {
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            dev.kof.compiler.nat.NativeProfile.active = profile == null
                    ? previous
                    : dev.kof.compiler.nat.NativeProfile.of(profile);
            return dev.kof.compiler.NativeRuntime.generateRuntimeAssembly();
        } finally {
            dev.kof.compiler.nat.NativeProfile.active = previous;
        }
    }

    /**
     * O corpo de uma FUNÇÃO: do rótulo até o seu {@code ret} — as faces do
     * cache são funções simples, e o limite exato importa (o pedaço tem de
     * conter as instruções que o assembler recusa).
     */
    private static String functionBody(String asm, String label) {
        String[] lines = asm.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!inside) {
                if (trimmed.equals(label + ":")) inside = true;
                continue;
            }
            sb.append(line).append('\n');
            if (trimmed.equals("ret")) break;
        }
        return sb.toString();
    }

    private static String body(String asm, String label) {
        String[] lines = asm.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(label + ":")) {
                inside = true;
                continue;
            }
            if (!inside) continue;
            if (trimmed.startsWith(".section")) break;
            if (trimmed.endsWith(":") && !trimmed.contains(" ")
                    && !trimmed.startsWith(".")) break;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * O cache do runtime NATIVO continua igual: o round-trip, a
     * sobrescrita, o ausente, o TTL e o delete.
     */
    @Test
    void theNativeCacheBehaviourIsUnchanged(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, CACHE_PROGRAM);
        CompilationResult result = new CompilerDriver().compile(
                src, tmp.resolve("out"), Target.NATIVE);
        assertTrue(result.success(),
                "the native compile succeeds: " + result.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out/Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "the native binary runs: '" + out + "'");
        assertEquals("Mel\nAna\nnull\ntrue\nx\nnull", out);
    }

    /**
     * O texto do cache não usa mais o endereçamento ABSOLUTO-indexado — a
     * forma que o Mach-O recusa.
     */
    @Test
    void theCacheTextHasNoAbsoluteIndexedAddressing() {
        String asm = runtime("macos");
        String cache = functionBody(asm, "kof_cache_get")
                + functionBody(asm, "kof_cache_set")
                + functionBody(asm, "kof_cache_set_ttl")
                + functionBody(asm, "kof_cache_find_slot");
        assertFalse(cache.contains("(,%r"),
                "the absolute-indexed form is gone:\n" + cache);
        assertTrue(cache.contains("leaq kof_cache_keys(%rip), %r11"),
                "the base is RIP-relative:\n" + cache);
    }

    /**
     * O ASSEMBLER REAL do Mach-O aceita o cache: no texto antigo ele recusava
     * com "32-bit absolute addressing is not supported in 64-bit mode".
     */
    @Test
    void theRealMachOAssemblerAcceptsTheCache(@TempDir Path tmp) throws Exception {
        Path zig = machoToolchain();
        org.junit.jupiter.api.Assumptions.assumeTrue(zig != null,
                "the Mach-O toolchain is absent (scripts/provision-macho-toolchain.sh)");
        String macho = dev.kof.compiler.nat.NativeMachO.translate(runtime("macos"));
        StringBuilder fragment = new StringBuilder();
        // o bloco de DADOS do cache: com os símbolos DEFINIDOS o assembler
        // avalia o endereçamento (e recusa a forma absoluta-indexada) — com
        // símbolo indefinido ele aceitaria, e a prova seria cega.
        int dataAt = macho.indexOf(".globl _kof_cache_keys");
        if (dataAt >= 0) {
            int dataEnd = macho.indexOf(".globl _kof_cache_get", dataAt);
            if (dataEnd > dataAt) fragment.append(macho, dataAt, dataEnd);
        }
        // o texto TRADUZIDO tem o prefixo do Mach-O ("_kof_cache_get")
        for (String label : List.of("_kof_cache_get", "_kof_cache_set", "_kof_cache_set_ttl",
                "_kof_cache_find_slot", "_kof_cache_delete", "_kof_cache_clear")) {
            fragment.append(functionBody(macho, label));
        }
        Path asmFile = tmp.resolve("Cache.macho.s");
        Files.writeString(asmFile, fragment.toString());
        Path obj = tmp.resolve("Cache.macho.o");
        ProcessBuilder pb = new ProcessBuilder(
                zig.toString(), "cc", "-target", "x86_64-macos-none",
                "-c", asmFile.toString(), "-o", obj.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes());
        int rc = proc.waitFor();
        assertEquals(0, rc,
                "the Mach-O assembler must accept the cache:\n" + output);
    }

    private static Path machoToolchain() {
        String home = System.getenv("KOF_MACHO_HOME");
        List<Path> candidates = new ArrayList<>();
        if (home != null && !home.isBlank()) {
            candidates.add(Path.of(home, "bin", "zig"));
        }
        candidates.add(Path.of(System.getProperty("user.home", ""),
                ".local", "share", "kof-macho", "bin", "zig"));
        for (Path p : candidates) {
            if (Files.isExecutable(p)) return p;
        }
        return null;
    }
}
