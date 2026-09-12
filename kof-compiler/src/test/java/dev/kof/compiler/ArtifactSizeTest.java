package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #97 / T0 (PLAN-TREE-SHAKING): harness de tamanho + gate anti-regressão.
 *
 * <p>Trava os NÚMEROS MEDIDOS deste host como linha de largada: hoje o
 * runtime nativo é emitido 100% incondicional, então um `hello` carrega o
 * runtime inteiro (crypto/web/mq/vk/… inalcançáveis). O gate é UNILATERAL
 * (molde `ConformanceMatrixDocTest`): um artefato que INCHA &gt;5% quebra o
 * build; encolher é sempre ok — é exatamente a meta dos degraus T1a/T1b/T2.
 * Quando uma poda por alcançabilidade fechar, atualiza-se o baseline para o
 * número novo (e o gate volta a proteger de regressão a partir dali).
 *
 * <p>Puro-Java (parser ELF64 próprio) — não depende de `nm`/`readelf`; o
 * gate cross (riscv/aarch) usa {@link Assumptions} igual os E2E cross: sem
 * toolchain no host, pula (não é regressão silenciosa). JVM é lazy on-demand
 * (class-loading) — não há artefato único p/ travar; documentado no plano T4.
 */
class ArtifactSizeTest {

    private final CompilerDriver driver = new CompilerDriver();

    // Baseline MEDIDO neste host. Pré-S-3 (12/09 manhã): 138.928B/627 syms —
    // o hello carregava o runtime INTEIRO. Pós-S-3 (poda x86, esta sessão):
    // o gate trava o número NOVO (encolher foi a meta; o gate unilateral
    // volta a proteger de regressão a partir daqui).
    private static final long HELLO_X86_BYTES = 32_520L;
    private static final int HELLO_X86_SYMS = 37;
    // Runtime JS integral copiado no hello (kof-runtime.mjs + io).
    private static final long HELLO_JS_BYTES = 177_412L;
    // Hello riscv64 (cross — só medido onde há toolchain).
    private static final long HELLO_RV_BYTES = 144_000L;
    private static final int HELLO_RV_SYMS = 258;

    private static final double TOL = 0.05; // gate de inchaço >5%

    private Path helloNative(Path tmp, Target t) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, "main() {\n    println(\"hello\")\n}\n");
        Path out = tmp.resolve("out-" + t);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), "hello deve compilar p/ " + t + ": " + r.diagnostics().getDiagnostics());
        return out.resolve("Default/Main");
    }

    private static void assertNoBloat(long bytes, int syms, long baseBytes, int baseSyms, String what) {
        long maxBytes = Math.round(baseBytes * (1 + TOL));
        int maxSyms = (int) Math.round(baseSyms * (1 + TOL));
        assertTrue(bytes <= maxBytes,
                what + " inchou: " + bytes + "B > " + maxBytes + "B (baseline " + baseBytes + "B +5%)");
        assertTrue(syms <= maxSyms,
                what + " ganhou símbolos: " + syms + " > " + maxSyms + " (baseline " + baseSyms + " +5%)");
    }

    @Test
    void helloX86NativeSizeWithinBaseline(@TempDir Path tmp) throws IOException {
        Path bin = helloNative(tmp, Target.NATIVE);
        ArtifactSize.ElfSizes e = ArtifactSize.elf(bin);
        assertEquals(e.fileBytes(), Files.size(bin), "fileBytes deve bater stat");
        // o runtime é .text + .bss (heap bump/roots); o hello NÃO deve ser minúsculo
        // (a meta T1a é derrubar isso — hoje é o inchaço que o gate registra).
        assertTrue(e.sectionBytes(".text") > 0, "deve haver .text de runtime");
        // Pós-S-3: o hello NÃO carrega mais o runtime inteiro — a poda por
        // alcançabilidade derrubou 627→~37 símbolos. O gate agora exige o
        // número BAIXO (era o inverso, pré-poda). Se alguém re-introduzir
        // emissão incondicional, isso estoura.
        assertTrue(e.kofSymbols() < 100,
                "S-3 podou o hello a <100 syms; se estourou, alguém voltou a emitir runtime inteiro — symbs=" + e.kofSymbols());
        assertNoBloat(e.fileBytes(), e.kofSymbols(), HELLO_X86_BYTES, HELLO_X86_SYMS, "hello x86_64");
    }

    /**
     * T1a.4 (issue #97 S-3): "programa que usa X ⇒ família Y ausente". Prova
     * NÃO-vaciosa: os nomes abaixo são símbolos REAIS do mapa da S-2 (verificados
     * presentes no `.symtab` de um bin que usa a família) — não strings
     * inventadas que passariam por nunca existirem. Dois programas mínimos:
     * um puxa crypto (e NÃO deve trazer json/mq/vk/random), outro puxa json
     * (e NÃO deve trazer crypto). O seed é por TEXTO do programa (S-3), então
     * `crypto.sha256` → kof_sec_sha256_* entra e o resto fica fora.
     */
    @Test
    void nativeFamilyAbsenceAfterPrune(@TempDir Path tmp) throws IOException {
        ArtifactSize.ElfSizes sec = elfOf(tmp, "sec", "main() {\n    println(crypto.sha256(\"abc\"))\n}\n");
        // família chamada PRESENTE (anti-vácuo: o nome é real e entra)
        assertTrue(sec.definedKof().contains("kof_sec_sha256"),
                "crypto.sha256 deve puxar kof_sec_sha256; syms=" + sec.definedKof());
        // famílias NÃO-chamadas AUSENTES (a poda)
        assertTrue(sec.definedKof().stream().noneMatch(s -> s.startsWith("kof_json")),
                "json deve estar PODADO num programa só-crypto: " + sec.definedKof());
        assertTrue(sec.definedKof().stream().noneMatch(s -> s.startsWith("kof_mq")),
                "mq deve estar PODADO num programa só-crypto: " + sec.definedKof());
        assertTrue(sec.definedKof().stream().noneMatch(s -> s.startsWith("kof_vk")),
                "vk deve estar PODADO num programa só-crypto: " + sec.definedKof());
        assertTrue(sec.definedKof().stream().noneMatch(s -> s.startsWith("kof_random")),
                "random deve estar PODADO num programa só-crypto: " + sec.definedKof());

        ArtifactSize.ElfSizes jsn = elfOf(tmp, "jsn", "main() {\n    println(json.encode(listOf(1, 2)))\n}\n");
        assertTrue(jsn.definedKof().contains("kof_json_encode_int"),
                "json.encode deve puxar kof_json_encode_int; syms=" + jsn.definedKof());
        assertTrue(jsn.definedKof().stream().noneMatch(s -> s.startsWith("kof_sec_sha256")),
                "sha256 deve estar PODADO num programa só-json: " + jsn.definedKof());
    }

    private ArtifactSize.ElfSizes elfOf(Path tmp, String tag, String source) throws IOException {
        Path src = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        Path out = tmp.resolve("out-" + tag);
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), tag + " deve compilar p/ native: " + r.diagnostics().getDiagnostics());
        return ArtifactSize.elf(out.resolve("Default/Main"));
    }

    @Test
    void helloJsRuntimeSizeWithinBaseline(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, "main() {\n    println(\"hello\")\n}\n");
        Path out = tmp.resolve("out-js");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "hello deve compilar p/ JS: " + r.diagnostics().getDiagnostics());
        long js = ArtifactSize.jsBytes(out);
        assertTrue(js > 100_000, "hoje o runtime .mjs é copiado integral (meta T2 ~8-20KB) — jsBytes=" + js);
        long max = Math.round(HELLO_JS_BYTES * (1 + TOL));
        assertTrue(js <= max, "runtime JS inchou: " + js + "B > " + max + "B (baseline " + HELLO_JS_BYTES + "B +5%)");
    }

    @Test
    void helloRiscvSizeWithinBaseline(@TempDir Path tmp) throws IOException {
        assumeCross("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        Path bin = helloNative(tmp, Target.NATIVE_RISCV64);
        ArtifactSize.ElfSizes e = ArtifactSize.elf(bin);
        // riscv não tem GC mark-sweep: o inchaço é .data (fatias) + .bss (heap).
        assertTrue(e.sectionBytes(".bss") > 100_000,
                "riscv reserva heap/statik por fatia no .bss (~260KB) — bss=" + e.sectionBytes(".bss"));
        assertNoBloat(e.fileBytes(), e.kofSymbols(), HELLO_RV_BYTES, HELLO_RV_SYMS, "hello riscv64");
    }

    private static void assumeCross(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) {
                    Assumptions.assumeTrue(false, "toolchain cross ausente (" + c + ") — pulando (NATIVE002/#97)");
                }
            } catch (Exception e) {
                Assumptions.assumeTrue(false, "toolchain cross ausente — pulando");
            }
        }
    }
}
