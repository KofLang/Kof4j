package dev.kof.compiler.nat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Link dinâmico SOB DEMANDA (link-by-use) cross riscv64/aarch64 — diretriz da
 * mantenedora (15/09): "liga dinamicamente". Prova que a infra
 * {@link NativeCrossLink} liga um binário com libc ({@code snprintf}/{@code write})
 * via {@code -dynamic-linker + --sysroot + -lc}, e que o binário RESOLVE libc sob
 * qemu (loader dinâmico + reloc PLT), imprimindo o resultado.
 *
 * <p>O caminho ESTÁTICO (sem consumidor libc) permanece o de hoje — coberto pelos
 * 84 E2E cross. Este teste só exige o sysroot cross; sem ele, PULA (a
 * produção mantém estático, ver {@link NativeCrossLink#sysrootFor}).
 */
class NativeCrossDynamicLinkTest {

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /** _start dinâmico: snprintf("v=%d\n", 42) + write(1,...) — 2 símbolos libc
     *  (formatação + I/O), ambos resolvidos pelo loader em runtime. */
    private static final String HARNESS_LIBC = """
            .option arch, rv64g
            .section .rodata
            .Ldyn_fmt: .asciz "v=%d\\n"
            .section .text
            .globl _start
            _start:
                andi sp, sp, -16
                addi sp, sp, -128
                mv   a0, sp
                li   a1, 64
                la   a2, .Ldyn_fmt
                li   a3, 42
                call snprintf
                mv   s0, a0
                li   a0, 1
                mv   a1, sp
                mv   a2, s0
                call write
                li   a0, 0
                call exit
            """;

    // ---- unit: detecção de consumidor libc + forma dos args do ld ----

    @Test
    void needsLibcDetectsCallsButNotComments() {
        assertFalse(NativeCrossLink.needsLibc("_start:\n    call kof_println_string\n    call kof_alloc\n"),
                "runtime asm puro não tem libc → estático");
        assertTrue(NativeCrossLink.needsLibc("    call snprintf\n"),
                "call snprintf → dinâmico");
        assertFalse(NativeCrossLink.needsLibc("    # call snprintf no comentário\n"),
                "comentário não conta");
        assertFalse(NativeCrossLink.needsLibc("    call kof_strtod\n"),
                "prefixo kof_ não é libc");
    }

    @Test
    void ldArgsStaticHasNoLcAndDynamicDoes() {
        Path bin = Path.of("/tmp/x");
        Path obj = Path.of("/tmp/x.o");
        String[] st = NativeCrossLink.ldArgs("riscv64-linux-gnu-ld", bin, obj, "riscv64", false, null);
        assertFalse(List.of(st).contains("-lc"), "estático não leva -lc: " + List.of(st));
        assertTrue(List.of(st).contains("--no-relax"), "riscv estático mantém --no-relax");
        String[] dyn = NativeCrossLink.ldArgs("riscv64-linux-gnu-ld", bin, obj, "riscv64", true, "/tmp/opencode/x");
        assertTrue(List.of(dyn).contains("-lc"), "dinâmico leva -lc: " + List.of(dyn));
        assertTrue(List.of(dyn).contains("--allow-shlib-undefined"));
        assertTrue(List.of(dyn).contains("--sysroot=/tmp/opencode/x"));
        assertTrue(List.of(dyn).contains("/lib/ld-linux-riscv64-lp64d.so.1"));
        String[] dynA = NativeCrossLink.ldArgs("aarch64-linux-gnu-ld", bin, obj, "aarch64", true, null);
        assertTrue(List.of(dynA).contains("/lib/ld-linux-aarch64.so.1"));
        assertFalse(List.of(dynA).contains("--no-relax"), "aarch64 sem --no-relax");
    }

    // ---- unit: forma do arg de link p/ `library()` de extern (#431) ----

    @Test
    void ffiLinkArgMapsSonamesAndKeepsObjectPaths() {
        assertEquals("-l:libc.so.6", NativeCrossLink.ffiLinkArg("libc.so.6"));
        assertEquals("-l:libm.so.6", NativeCrossLink.ffiLinkArg("/usr/lib/libm.so.6"),
                "soname com path → basename em -l: (lib do sysroot cross)");
        assertEquals("-lm", NativeCrossLink.ffiLinkArg("m"), "nome cru → -l<nome>");
        assertEquals("/tmp/fixture.o", NativeCrossLink.ffiLinkArg("/tmp/fixture.o"),
                "objeto montado p/ a arch entra posicional preservando o path (fatia C4-x)");
        assertEquals("rel/fixture.o", NativeCrossLink.ffiLinkArg("rel/fixture.o"),
                "path relativo de objeto também é preservado");
    }

    // ---- unit: detecção do consumidor SQLite + -lsqlite3 (DB001) ----

    @Test
    void needsSqliteDetectsCallsButNotComments() {
        assertFalse(NativeCrossLink.needsSqlite("_start:\n    call kof_println_string\n    call kof_alloc\n"),
                "runtime puro não consome sqlite → estático");
        assertTrue(NativeCrossLink.needsSqlite("    call sqlite3_open\n"),
                "call sqlite3_open → dinâmico + -lsqlite3");
        assertTrue(NativeCrossLink.needsSqlite("    call sqlite3_prepare_v2\n"));
        assertFalse(NativeCrossLink.needsSqlite("    # call sqlite3_open no comentário\n"),
                "comentário não conta");
        assertFalse(NativeCrossLink.needsSqlite("    call kof_db_connect\n"),
                "wrapper kof_db_* não é símbolo libsqlite3 (a detecção é do .so real)");
    }

    @Test
    void ldArgsSqliteAddsLsqlite3() {
        Path bin = Path.of("/tmp/x");
        Path obj = Path.of("/tmp/x.o");
        // The sqlite link arg is a function of the sysroot the host resolves
        // (sqliteLinkArg reads sysrootFor, not the ldArgs sysroot param):
        // `-lsqlite3` when a dev `.so` is present, else `-l:libsqlite3.so.0`
        // when only the soname is installed (#699). Asserting one hardcoded
        // form makes the test host-dependent, so derive the expected arg from
        // the impl helper and assert BOTH forms are absent without a consumer.
        String expectedArg = NativeCrossLink.sqliteLinkArg("riscv64");
        String[] plain = NativeCrossLink.ldArgs("riscv64-linux-gnu-ld", bin, obj, "riscv64", true, "/tmp/opencode/x");
        assertFalse(List.of(plain).contains("-lsqlite3"),
                "sem consumidor sqlite não leva -lsqlite3: " + List.of(plain));
        assertFalse(List.of(plain).contains("-l:libsqlite3.so.0"),
                "sem consumidor sqlite não leva -l:libsqlite3.so.0: " + List.of(plain));
        String[] sq = NativeCrossLink.ldArgs("riscv64-linux-gnu-ld", bin, obj, "riscv64", true, "/tmp/opencode/x", true);
        assertTrue(List.of(sq).contains("-lc"), "sqlite implica libc: " + List.of(sq));
        assertTrue(List.of(sq).contains(expectedArg),
                "consumidor sqlite leva o arg sysroot-appropriate " + expectedArg + ": " + List.of(sq));
    }

    // ---- E2E: o binário dinâmico roda sob qemu e imprime via libc ----

    private String runCapture(java.util.Map<String, String> env, String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (env != null) env.forEach((k, v) -> pb.environment().put(k, v));
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            int ec = p.waitFor();
            assertEquals(0, ec, "comando falhou (" + ec + "): " + String.join(" ", cmd) + "\n" + out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out;
    }

    /** Sabotagem: com o link ESTÁTICO (dynamic=false) o mesmo harness não
     *  resolve snprintf/write → o ld falha. Prova que a detecção é
     *  load-bearing (não basta "ligar dinâmico sempre"). */
    @Test
    void staticLinkOfLibcHarnessFails(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld"),
                "toolchain riscv64 ausente — pulando");
        Path asm = tempDir.resolve("sab.s");
        Path obj = tempDir.resolve("sab.o");
        Path bin = tempDir.resolve("sab");
        Files.writeString(asm, HARNESS_LIBC);
        runCapture(null, "riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        ProcessBuilder pb = new ProcessBuilder(NativeCrossLink.ldArgs("riscv64-linux-gnu-ld", bin, obj,
                "riscv64", false, null)).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        assertNotEquals(0, ec, "link estático de harness libc DEVERIA falhar (undefined snprintf)");
        assertTrue(out.contains("snprintf") || out.contains("undefined"),
                "erro deveria citar o símbolo libc ausente: " + out);
    }

    @Test
    void riscv64DynamicLinksLibcAndRuns(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "toolchain riscv64 ausente — pulando");
        Assumptions.assumeTrue(NativeCrossLink.sysrootFor("riscv64") != null,
                "libc riscv64-cross ausente (KOF_CROSS_SYSROOT) — pulando link dinâmico");
        Path asm = tempDir.resolve("dyn.s");
        Path obj = tempDir.resolve("dyn.o");
        Path bin = tempDir.resolve("dyn");
        Files.writeString(asm, HARNESS_LIBC);
        runCapture(null, "riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        runCapture(null, NativeCrossLink.ldArgs("riscv64-linux-gnu-ld", bin, obj,
                "riscv64", true, NativeCrossLink.sysrootFor("riscv64")));
        bin.toFile().setExecutable(true);
        String out = runCapture(java.util.Map.of("QEMU_LD_PREFIX", NativeCrossLink.qemuPrefixFor("riscv64")),
                "qemu-riscv64", bin.toString());
        assertEquals("v=42", out, "libc dinâmica deveria formatar via snprintf: " + out);
    }

    @Test
    void aarch64DynamicLinksLibcAndRuns(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "toolchain aarch64 ausente — pulando");
        Assumptions.assumeTrue(NativeCrossLink.sysrootFor("aarch64") != null,
                "libc aarch64-cross ausente (KOF_CROSS_SYSROOT) — pulando link dinâmico");
        StringBuilder arm = new StringBuilder();
        for (String line : HARNESS_LIBC.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        Path asm = tempDir.resolve("dyna.s");
        Path obj = tempDir.resolve("dyna.o");
        Path bin = tempDir.resolve("dyna");
        Files.writeString(asm, arm.toString());
        runCapture(null, "aarch64-linux-gnu-as", "-o", obj.toString(), asm.toString());
        runCapture(null, NativeCrossLink.ldArgs("aarch64-linux-gnu-ld", bin, obj,
                "aarch64", true, NativeCrossLink.sysrootFor("aarch64")));
        bin.toFile().setExecutable(true);
        String out = runCapture(java.util.Map.of("QEMU_LD_PREFIX", NativeCrossLink.qemuPrefixFor("aarch64")),
                "qemu-aarch64", bin.toString());
        assertEquals("v=42", out, "libc dinâmica deveria formatar via snprintf: " + out);
    }

    // ---- E2E DB001: o binário resolve libsqlite3 (-lsqlite3) sob qemu ----

    /** _start que chama sqlite3_libversion() e imprime o retorno via libc
     *  (strlen+write): prova o link `-lc -lsqlite3` e a resolução da .so. */
    private static final String HARNESS_SQLITE = """
            .option arch, rv64g
            .section .text
            .globl _start
            _start:
                andi sp, sp, -16
                addi sp, sp, -16
                call sqlite3_libversion
                mv   s0, a0
                mv   a0, s0
                call strlen
                mv   a2, a0
                li   a0, 1
                mv   a1, s0
                call write
                li   a0, 0
                call exit
            """;

    /** Versão esperada = a que a PRÓPRIA .so linkada reporta, lida do binário
     *  (string NUL-delimitada de {@code sqlite3_libversion}): o sysroot do
     *  host/CI tem a versão que tiver (3.45.1 no CI, 3.46.1 no host de
     *  22/09) — o contrato é "o binário resolve a .so e imprime a versão
     *  dela", nunca "a .so é uma versão fixa" (golden = medição real). */
    private static String sqliteVersionFromLib(String arch) throws IOException {
        String path = NativeCrossLink.sqliteLibFor(arch);
        assertNotNull(path, "libsqlite3 presente (assumeTrue acima)");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\x00(\\d+\\.\\d+\\.\\d+)\\x00")
                .matcher(new String(bytes, StandardCharsets.ISO_8859_1));
        assertTrue(m.find(), "versão embutida na libsqlite3: " + path);
        return m.group(1);
    }

    @Test
    void riscv64DynamicLinksSqliteAndRuns(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "toolchain riscv64 ausente — pulando");
        Assumptions.assumeTrue(NativeCrossLink.sqliteAvailable("riscv64"),
                "libsqlite3 riscv64-cross ausente (KOF_CROSS_SYSROOT) — pulando");
        assertTrue(NativeCrossLink.needsSqlite(HARNESS_SQLITE), "harness deve detectar sqlite3_*");
        Path asm = tempDir.resolve("sq.s");
        Path obj = tempDir.resolve("sq.o");
        Path bin = tempDir.resolve("sq");
        Files.writeString(asm, HARNESS_SQLITE);
        runCapture(null, "riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        runCapture(null, NativeCrossLink.ldArgs("riscv64-linux-gnu-ld", bin, obj,
                "riscv64", true, NativeCrossLink.sysrootFor("riscv64"), true));
        bin.toFile().setExecutable(true);
        String out = runCapture(java.util.Map.of("QEMU_LD_PREFIX", NativeCrossLink.qemuPrefixFor("riscv64")),
                "qemu-riscv64", bin.toString());
        assertEquals(sqliteVersionFromLib("riscv64"), out,
                "libsqlite3 dinâmica deveria devolver a versão da própria .so linkada: " + out);
    }

    @Test
    void aarch64DynamicLinksSqliteAndRuns(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "toolchain aarch64 ausente — pulando");
        Assumptions.assumeTrue(NativeCrossLink.sqliteAvailable("aarch64"),
                "libsqlite3 aarch64-cross ausente (KOF_CROSS_SYSROOT) — pulando");
        StringBuilder arm = new StringBuilder();
        for (String line : HARNESS_SQLITE.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        Path asm = tempDir.resolve("sqa.s");
        Path obj = tempDir.resolve("sqa.o");
        Path bin = tempDir.resolve("sqa");
        Files.writeString(asm, arm.toString());
        runCapture(null, "aarch64-linux-gnu-as", "-o", obj.toString(), asm.toString());
        runCapture(null, NativeCrossLink.ldArgs("aarch64-linux-gnu-ld", bin, obj,
                "aarch64", true, NativeCrossLink.sysrootFor("aarch64"), true));
        bin.toFile().setExecutable(true);
        String out = runCapture(java.util.Map.of("QEMU_LD_PREFIX", NativeCrossLink.qemuPrefixFor("aarch64")),
                "qemu-aarch64", bin.toString());
        assertEquals(sqliteVersionFromLib("aarch64"), out,
                "libsqlite3 dinâmica deveria devolver a versão da própria .so linkada: " + out);
    }
}
