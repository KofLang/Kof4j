package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D-MEMORY-SAFETY M1 unidade-1 (06/10): struct homogêneo-flutuante por valor
 * como PARÂMETRO no cross riscv64/aarch64 ({@code VD(Double,Double)},
 * {@code D1(Double)} e {@code VF(Float,Float)}), removendo o {@code FFI001}
 * honesto dessas formas. As duas ABIs concordam no corte medido
 * ({@link AbiLayoutTest} golden): LP64D achata ≤ 2 campos (cada campo
 * flutuante = um ordinal FP próprio {@code fa0..fa7}); AAPCS64 HFA n ≤ 4
 * entrega campo-a-campo em v0.. — e o texto {@code fa{n}} já é traduzido para
 * v* no aarch64 (precedente medido dos escalares {@code exp/ldexp} e da
 * devolutiva {@code ldexp} em fa0). Struct MISTO (float+int) diverge entre as
 * ABIs ({@code [SSE,INTEGER]} achado vs um eightword INTEGER único) e segue
 * FFI001 honesto na linha da declaração (R6). {@code VF} binda no cross mas
 * NÃO no x86-64: na SysV os dois floats cabem no MESMO eightbyte (multi-campo)
 * e o emissor x86 só cobre eightbyte SSE de campo único — assert abaixo.
 *
 * <p>Oráculo regra 5: o MESMO fonte roda no JVM (FFM) e nos dois cross sob
 * qemu; saídas byte-a-byte iguais. Fixture {@code .so} real compilada por
 * arch (host cc + cross-gcc); sem toolchain → skip honesto, nunca falso-verde.
 */
class FfiCrossHfaE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            typedef struct { double x; double y; } VD;
            typedef struct { double x; } D1;
            typedef struct { float x; float y; } VF;
            typedef struct { double d; int i; } MD;
            double hvd(VD p) { return p.x + p.y; }
            double hd1(D1 p) { return p.x; }
            float hvf(VF p) { return p.x + p.y; }
            double hmd(MD p) { return p.d + p.i; }
            """;

    // Subconjunto que o x86-64 (SysV, eightbytes SSE de campo único) já cobre:
    // paridade JVM + x86-64 + riscv64 + aarch64.
    private static final String KOF_D = """
            record VD(Double x, Double y)
            record D1(Double x)

            extern "%1$s" hvd(VD p): Double
            extern "%1$s" hd1(D1 p): Double

            main() {
                println(hvd(VD(1.5, 2.0)))
                println(hd1(D1(1.75)))
            }
            """;

    // Face cross-only: dois floats = dois ordinais FP no LP64D achado e no HFA
    // AAPCS64; na SysV cabem num único eightbyte multi-campo (FFI001 no x86).
    private static final String KOF_F = """
            record VF(Float x, Float y)

            extern "%1$s" hvf(VF p): Float

            main() {
                println(hvf(VF(2.0 as Float, 4.0 as Float)) + 0.0)
            }
            """;

    private static final String GOLDEN_D = String.join("\n", "3.5", "1.75");
    private static final String GOLDEN_F = "6.0";

    private static String cc(String... cands) {
        for (String c : cands) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) { /* tenta o próximo */ }
        }
        return null;
    }

    private static String buildHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "FFI nativo usa um .so real (Linux + cc)");
        Path src = dir.resolve("libkofhfa.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkofhfa.so");
        String c = cc("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(c != null, "sem toolchain C (cc/gcc) para a fixture FFI");
        Process p = new ProcessBuilder(c, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "cc falhou: " + out);
        return so.toString();
    }

    private static String compileCrossLib(Path dir, String arch) throws IOException, InterruptedException {
        Path c = dir.resolve("libkofhfa-" + arch + ".c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofhfa-" + arch + ".so");
        String cross = arch + "-linux-gnu-gcc";
        assumeTrue(cc(cross) != null, "sem cross-gcc " + cross);
        Process p = new ProcessBuilder(cross, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                cross + " falhou ao compilar a fixture cross: " + out);
        return so.toString();
    }

    private String runJvm(Path dir, String kof, String tag) throws IOException, InterruptedException {
        Path src = dir.resolve("hfa-jvm-" + tag + ".kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-hfa-jvm-" + tag);
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM HFA-param compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp", out.toString(), "Default.Main");
        pb.directory(dir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "JVM run exit, output: " + o);
        return o;
    }

    private String runNativeHost(Path dir, String kof, String tag) throws IOException {
        Path src = dir.resolve("hfa-nat-" + tag + ".kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-hfa-nat-" + tag);
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE x86-64 single-field-SSE struct param must bind: "
                + r.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            assertEquals(0, p.waitFor(), () -> "NATIVE x86-64 run exit, output: " + o);
            return o.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private String runQemu(Path dir, String arch, Target t, String kof, String tag)
            throws IOException, InterruptedException {
        Path src = dir.resolve("hfa-" + arch + "-" + tag + ".kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-hfa-" + arch + "-" + tag);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), "M1 unidade-1: struct homogêneo-flutuante param deve bindar em "
                + arch + " (era FFI001 honesto até aqui): " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, out.resolve("Default/Main"));
        pb.environment().put("LD_LIBRARY_PATH", dir.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), arch + " must finish");
        assertEquals(0, p.exitValue(), arch + " exit, output: " + o);
        return o;
    }

    @Test
    void doubleStructParamBindsOnJvmAndNativeHost(@TempDir Path dir) throws Exception {
        String so = buildHostLib(dir);
        String kof = KOF_D.formatted(so);
        assertEquals(GOLDEN_D, runJvm(dir, kof, "d"),
                "oráculo JVM (FFM, eightbytes SSE reais da SysV) deve produzir o golden");
        assertEquals(GOLDEN_D, runNativeHost(dir, kof, "d"),
                "struct homogêneo-double por valor no Native x86-64 (SysV eightbytes SSE)");
    }

    @Test
    void doubleStructParamCrossBindsAndMatchesJvm(@TempDir Path dir) throws Exception {
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            String hostSo = buildHostLib(dir);
            String crossSo = compileCrossLib(dir, arch);
            String jvm = runJvm(dir, KOF_D.formatted(hostSo), "dc-" + arch);
            assertEquals(GOLDEN_D, jvm, "oráculo JVM deve concordar byte-a-byte (regra 5)");
            assertEquals(GOLDEN_D, runQemu(dir, arch, t, KOF_D.formatted(crossSo), "d"),
                    "JVM==" + arch + " (struct-double param: fa0/fa1 LP64D achado, v0/v1 AAPCS64)");
        }
    }

    @Test
    void floatPairStructParamCrossBindsAndMatchesJvm(@TempDir Path dir) throws Exception {
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            String hostSo = buildHostLib(dir);
            String crossSo = compileCrossLib(dir, arch);
            assertEquals(GOLDEN_F, runJvm(dir, KOF_F.formatted(hostSo), "f-" + arch),
                    "oráculo JVM do VF (float,float) deve concordar (regra 5)");
            assertEquals(GOLDEN_F, runQemu(dir, arch, t, KOF_F.formatted(crossSo), "f"),
                    "JVM==" + arch + " VF: dois ordinais FP (LP64D achata; AAPCS64 HFA n=2)");
        }
    }

    @Test
    void x86TwoFloatSameEightbyteParamBinds(@TempDir Path dir) throws Exception {
        // SysV: os dois floats de VF cabem no MESMO eightbyte SSE; a face
        // VF-on-x86-64 foi LANDADA (M1) — o emissor x86 empacota o eightbyte SSE
        // multi-campo (bits em %rax, movq → %xmm). Golden = o MESMO fonte na JVM.
        String so = buildHostLib(dir);
        Path src = dir.resolve("hfa-x86vf.kf");
        Files.writeString(src, KOF_F.formatted(so));
        CompilationResult r = driver.compile(src, dir.resolve("out-hfa-x86vf"), Target.NATIVE);
        assertTrue(r.success(), "VF (eightbyte SSE multi-campo na SysV) deve bindar no x86-64 (M1): "
                + r.diagnostics().getDiagnostics());
        assertEquals(GOLDEN_F, runJvm(dir, KOF_F.formatted(so), "f-x86oracle"),
                "oráculo JVM do VF (float,float) deve concordar (regra 5)");
        assertEquals(GOLDEN_F, runNativeHost(dir, KOF_F.formatted(so), "f"),
                "x86-64 VF param byte-a-byte vs JVM (M1 VF-on-x86-64)");
    }

    @Test
    void mixedFloatIntStructParamStaysFfi001OnCross(@TempDir Path dir) throws IOException {
        // Gate roda ANTES do codegen — não precisa de toolchain. O struct MISTO
        // (double+int) é o caso em que LP64D e AAPCS64 DIVERGEM ([SSE,INTEGER]
        // achado vs um eightword INTEGER único): recusa honesta (R6).
        String kof = """
                record MD(Double d, Int i)

                extern "libc.so.6" hmd(MD p): Double

                main() { println("gap") }
                """;
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            Path src = dir.resolve("HfaMixed-" + t.name() + ".kf");
            Files.writeString(src, kof);
            CompilationResult r = driver.compile(src, dir.resolve("out-hfa-mixed-" + t.name()), t);
            assertFalse(r.success(), "struct MISTO float+int não pode virar silêncio em " + t);
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                    "mixed HFA no cross permanece FFI001 honesto na declaração: "
                            + r.diagnostics().getDiagnostics());
        }
    }
}
