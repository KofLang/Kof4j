package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * #431 (Native FFI, fatia 2 — riscv64 + aarch64): o `extern` escalar BINDA
 * nos alvos cross. ABI: LP64 (riscv: a0-a7 int-class, f0-f7 float-class,
 * derramados na pilha) e AAPCS64 no aarch64 — que é a tradução
 * linha-a-linha do texto riscv (um shim, duas archs — mesma regra dos
 * testes de coleção §359). Link: a `library()` do extern vira input do ld
 * cross via sysroot (`-l:libc.so.6`/`-l:libm.so.6`) e força o link
 * dinâmico (DB001 é o precedente do `call sym`@PLT resolvido pelo ld.so).
 *
 * <p>Oráculo: REGRA 5 — o golden é MEDIÇÃO real, não memória; aqui ele é
 * fixado pela execução sob qemu nas DUAS archs concordando entre si e com
 * a saída do mesmo programa no oráculo JVM (dlopen já provado fatia 1).
 * Ferramentas ausentes → {@code assumeTrue} (skip honesto, nunca falso-verde).
 *
 * <p>Provas desta fatia: Int/Long/Double com multi-arg MIXED-CLASS
 * (ldexp: float antes de int — conta por classe), String→char* (puts/
 * strlen/getenv), retorno String via helper (cópia na fronteira — buffer C
 * nunca free'd), void implícito (return Int descartado como stmt não —
 * todos os externs aqui usam o retorno), 0-arg (getpid), edge vazio
 * (strlen "") e edge negativo (abs(-42)). NÃO provável no cross sem
 * toolchain C (cc cruzado ausente no host — fixture .so não compila):
 * derramamento (≥9 args) e Float/Bool — o caminho é o MESMO código
 * compartilhado (FfiSignature/marshaling) com x86-64, que tem golden de
 * 9-arg/spill/mix na fatia 1; o limite é de ferramenta, documentado no
 * ledger (§365). Callback e `String[]` (array de ponteiros) e struct com campo
 * float/HFA ou &gt; 16 B: FFI001 honesto (gate, linha da declaração); array
 * escalar `T[]`→ptr binda no cross desde D-MEM-FFI-CROSS-FULL (30/09,
 * `kof_ffi_pack_array`); struct RETURN com campos INTEGER
 * (≤ 16 B) binda na fatia 3 (prova `div()` abaixo).
 */
class FfiNativeCrossE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PROGRAM = """
            extern "libc.so.6" abs(Int x): Int
            extern "libc.so.6" strlen(String s): Long
            extern "libc.so.6" puts(String s): Int
            extern "libm.so.6" ldexp(Double m, Int e): Double
            extern "libc.so.6" getenv(String name): String
            extern "libc.so.6" getpid(): Long
            main() {
                println(abs(-42))
                println(abs(7))
                println(strlen(""))
                println(puts("cross-ffi"))
                println(ldexp(2.0, 3))
                println(getenv("KF_FFI_HOME"))
                println(getpid() > 0)
            }
            """;

    /** MEDIÇÃO 19/09, qemu nas duas archs — saídas BYTE-IDENTICAS riscv/aarch
     *  (travado em crossTargetsAgreeOnSameProgram): abs(-42)=42, abs(7)=7,
     *  strlen("")=0, puts devolve 10 (9 + newline) e sua linha "cross-ffi"
     *  sai POR ÚLTIMO — stdio da C é full-buffered no pipe e o _start cru
     *  não tem atexit(); o #431 adicionou o `call fflush` antes do
     *  exit_group exatamente p/ essa linha sobreviver (antes: perdia-se).
     *  ldexp(2.0,3)=16.0 — glibc riscv64 devolve FP em fa0 (medido na cauda
     *  do `exp`; o strtod do runtime cross já lê fa0), e no aarch o tradutor
     *  mapeia fa0→f0→d0 = retorno AAPCS64 ✓. getenv = env fixo, pid>0=true. */
    private static final String GOLDEN = String.join("\n",
            "42", "7", "0", "10", "16.0", "cross-ffi-ok", "true", "cross-ffi");

    private static boolean ready(String arch) {
        return NativeRiscv64E2ETest.hasToolchain(arch);
    }

    private String runCross(String arch, Target t, Path tempDir, String source) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out-" + arch);
        CompilationResult r = driver.compile(src, outDir, t);
        assertTrue(r.success(), "Compilation should succeed: " + r.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist for " + arch);
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binFile);
        pb.environment().put("KF_FFI_HOME", "cross-ffi-ok");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running " + arch + " binary", e);
        }
        assertEquals(0, ec, "Exit code should be 0 (" + arch + "), output: '" + output + "'");
        return output;
    }

    @Test
    void riscv64ExternScalarAbi(@TempDir Path tempDir) throws IOException {
        assumeTrue(ready("riscv64"), "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross("riscv64", Target.NATIVE_RISCV64, tempDir, PROGRAM),
                "riscv64 extern ABI (LP64: int-class, mixed float+int, char*, retorno String) "
                        + "must match the measured golden");
    }

    @Test
    void aarch64ExternScalarAbi(@TempDir Path tempDir) throws IOException {
        assumeTrue(ready("aarch64"), "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross("aarch64", Target.NATIVE_AARCH64, tempDir, PROGRAM),
                "aarch64 extern ABI (AAPCS64, tradutor linha-a-linha) must match the measured golden");
    }

    @Test
    void crossTargetsAgreeOnSameProgram(@TempDir Path tempDir) throws IOException {
        assumeTrue(ready("riscv64") && ready("aarch64"),
                "ambas as archs + qemu necessárias para o teste de paridade");
        assertEquals(runCross("riscv64", Target.NATIVE_RISCV64, tempDir, PROGRAM),
                runCross("aarch64", Target.NATIVE_AARCH64, tempDir, PROGRAM),
                "regra 5: mesmo programa, mesma saída nos dois cross (sem divergência silenciosa)");
    }

    /** Q0/regra 1 (bug latente ACHADO na lane do #431, 19/09): o _start do
     *  aarch64 saía com exit(93) — só a thread chamadora morre; a thread do
     *  scheduler criada por `time.interval` (e, no mundo real, as threads
     *  internas de uma lib C tipo GLFW/raylib) mantinham o processo VIVO para
     *  sempre (hang sob qemu). O riscv/x86 já usavam exit_group (M32.3).
     *  Prova com espera limitada: sem o fix, waitFor estoura e o teste falha
     *  por TIMEOUT; com o fix, "end" aparece e o exit chega em <20s. */
    private void exitGroupKillsScheduler(Path tempDir, String arch, Target t) throws Exception {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, """
                main() {
                    time.interval(50, () -> println("tick"))
                    println("end")
                }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("out-hang-" + arch), t);
        assertTrue(r.success(), "compile " + arch + ": " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch,
                tempDir.resolve("out-hang-" + arch + "/Default/Main"));
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean done = false;
        try {
            done = p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            if (!done) p.destroyForcibly();
        }
        assertTrue(done, arch + " hung at exit — exit(93) left the scheduler thread alive"
                + " (must be exit_group/94 as in riscv/x86)");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), arch + " exit code, output: '" + out + "'");
        assertTrue(out.contains("end"), arch + " should print end before exiting: " + out);
    }

    @Test
    void aarch64ExitGroupKillsScheduler(@TempDir Path tempDir) throws Exception {
        assumeTrue(ready("aarch64"), "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        exitGroupKillsScheduler(tempDir, "aarch64", Target.NATIVE_AARCH64);
    }

    @Test
    void riscv64ExitGroupKillsScheduler(@TempDir Path tempDir) throws Exception {
        assumeTrue(ready("riscv64"), "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        exitGroupKillsScheduler(tempDir, "riscv64", Target.NATIVE_RISCV64);
    }

    /** 3.7 fatia 3: `record` devolvido por valor com campos INTEGER (≤ 16 B)
     *  BINDA no cross. Prova sem fixture C: `div()` da libc devolve
     *  `div_t { int quot; int rem; }` (8 B) num registrador inteiro conforme a
     *  ABI — riscv: `a0`; aarch64: `x0`. O golden `3\n1` é o MESMO medido no
     *  oráculo JVM em `FfiStructE2ETest.structReturnViaLibcDivJvm` (regra 5). */
    private static final String DIV_PROGRAM = """
            record Div(Int quot, Int rem)

            extern "libc.so.6" div(Int a, Int b): Div

            main() {
                val d = div(7, 2)
                println(d.quot())
                println(d.rem())
            }
            """;

    @Test
    void riscv64StructReturnViaLibcDiv(@TempDir Path tempDir) throws IOException {
        assumeTrue(ready("riscv64"), "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals("3\n1", runCross("riscv64", Target.NATIVE_RISCV64, tempDir, DIV_PROGRAM),
                "riscv64 div_t por valor (LP64: 1 doubleword empacotado em a0)");
    }

    @Test
    void aarch64StructReturnViaLibcDiv(@TempDir Path tempDir) throws IOException {
        assumeTrue(ready("aarch64"), "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals("3\n1", runCross("aarch64", Target.NATIVE_AARCH64, tempDir, DIV_PROGRAM),
                "aarch64 div_t por valor (AAPCS64: 1 eightword em x0)");
    }

    @Test
    void crossStructReturnAgreesBetweenArchs(@TempDir Path tempDir) throws IOException {
        assumeTrue(ready("riscv64") && ready("aarch64"),
                "ambas as archs + qemu necessárias para a paridade");
        assertEquals(runCross("riscv64", Target.NATIVE_RISCV64, tempDir, DIV_PROGRAM),
                runCross("aarch64", Target.NATIVE_AARCH64, tempDir, DIV_PROGRAM),
                "regra 5: struct return por valor, mesma saída nos dois cross");
    }

    @Test
    void riscv64StructReturnWithFloatFieldStaysFfi001(@TempDir Path tempDir) throws IOException {
        // Fatia 3 (primeiro corte): só o register path INTEGER binda. Um struct
        // com campo float (classe SSE/HFA) segue FFI001 honesto (R6) — o gate
        // roda ANTES do codegen, então não precisa de toolchain.
        Path src = tempDir.resolve("FloatRet.kf");
        Files.writeString(src, """
                record Pair(Double d, Int i)

                extern "libc.so.6" mk(): Pair

                main() { println("gap") }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("out-fret"), Target.NATIVE_RISCV64);
        assertFalse(r.success(), "struct return com campo float não pode virar silêncio no cross");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "float/HFA no cross permanece FFI001 honesto na declaração: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void riscv64ScalarArrayAndStringArrayBind(@TempDir Path tempDir) throws IOException {
        assumeTrue(ready("riscv64"), "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        // D-MEM-FFI-CROSS-FULL (30/09): array escalar `T[]`→ptr agora binda no
        // cross (copy-in `kof_ffi_pack_array`), provado com execução em
        // FfiNativeArrayE2ETest; aqui pina só o gate na declaração.
        Path ok = tempDir.resolve("ScalarArray.kf");
        Files.writeString(ok, """
                extern "libc.so.6" sum(Int[] xs): Int
                main() { println("bound") }
                """);
        CompilationResult r = driver.compile(ok, tempDir.resolve("out-ok"), Target.NATIVE_RISCV64);
        assertTrue(r.success(), "scalar array extern must bind on riscv64: "
                + r.diagnostics().getDiagnostics());

        // D-MEM-FFI-CROSS-FULL face 2 (30/09): `String[]`→`char**` agora binda no
        // cross (a prova por execução está em FfiNativeStringArrayE2ETest); aqui
        // pina só o gate na declaração.
        Path strArr = tempDir.resolve("StringArray.kf");
        Files.writeString(strArr, """
                extern "libc.so.6" sum(String[] xs): Int
                main() { println("bound") }
                """);
        CompilationResult rg = driver.compile(strArr, tempDir.resolve("out-strarr"), Target.NATIVE_RISCV64);
        assertTrue(rg.success(), "String[] must bind on cross riscv64: "
                + rg.diagnostics().getDiagnostics());
    }
}
