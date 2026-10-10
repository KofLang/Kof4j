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
 * D-MEMORY-SAFETY M1 unidade-2 (09/10): struct homogêneo-flutuante por valor
 * como RETORNO no cross riscv64/aarch64. A unidade-1 abriu o PARÂMETRO
 * ({@link FfiCrossHfaE2ETest}); aqui o retorno: LP64D devolve ≤ 2 campos em
 * {@code fa0}/{@code fa1}, AAPCS64 entrega o HFA campo-a-campo em
 * {@code v0..v3} — o mesmo ordinal por campo. O emissor lê os bits com
 * {@code fmv.x.*} (INTEGER, soft-float-safe) e grava cada campo no slot Kof.
 *
 * <p>Oráculo regra 5: o MESMO fonte roda no JVM (FFM) e nos dois cross sob
 * qemu; saídas byte-a-byte iguais. No cross a fixture é montada com o {@code as}
 * (o host não tem cc cruzado, como em {@code FfiCrossStructParamE2ETest}); no
 * host, cc real. Sem toolchain → skip honesto, nunca falso-verde.
 */
class FfiCrossHfaReturnE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            typedef struct { double x; double y; } VD;
            typedef struct { float x; float y; } VF;
            VD mkvd(double a, double b) { VD r; r.x = a; r.y = b; return r; }
            VF mkvf(float a, float b) { VF r; r.x = a; r.y = b; return r; }
            """;

    // VD (dois doubles) binda no JVM, no x86-64 (SysV xmm0/xmm1) e nos dois cross.
    private static final String KOF_VD = """
            record VD(Double x, Double y)

            extern "%1$s" mkvd(Double a, Double b): VD

            main() {
                val v = mkvd(1.5, 2.0)
                println(v.x())
                println(v.y())
            }
            """;

    // VF (dois floats): na SysV cabem num único eightbyte multi-campo → FFI001
    // no x86-64; no JVM e no cross (LP64D dois ordinais FP / AAPCS64 HFA) binda.
    private static final String KOF_VF = """
            record VF(Float x, Float y)

            extern "%1$s" mkvf(Float a, Float b): VF

            main() {
                val f = mkvf(2.0 as Float, 4.0 as Float)
                println(f.x() + 0.0)
                println(f.y() + 0.0)
            }
            """;

    private static final String GOLDEN_VD = String.join("\n", "1.5", "2.0");
    private static final String GOLDEN_VF = String.join("\n", "2.0", "4.0");

    // Fixture montada com o as cross (sem cc): o retorno HFA usa os MESMOS
    // registradores FP dos argumentos (LP64D fa0/fa1; AAPCS64 d0/d1 ou s0/s1),
    // então um `ret` puro devolve exatamente o par recebido — a prova de que o
    // emissor lê o ordinal de retorno certo em cada campo.
    private static final String FIXTURE_RISCV = """
            .text
            .globl mkvd
            mkvd:
                ret
            .globl mkvf
            mkvf:
                ret
            """;

    private static final String FIXTURE_AARCH = """
            .text
            .globl mkvd
            mkvd:
                ret
            .globl mkvf
            mkvf:
                ret
            """;

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
        Path src = dir.resolve("libkofhfare.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkofhfare.so");
        String c = cc("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(c != null, "sem toolchain C (cc/gcc) para a fixture FFI");
        Process p = new ProcessBuilder(c, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "cc falhou: " + out);
        return so.toString();
    }

    private String runJvm(Path dir, String kof, String tag) throws IOException, InterruptedException {
        Path src = dir.resolve("hfare-jvm-" + tag + ".kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-hfare-jvm-" + tag);
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM HFA-return compile: " + r.diagnostics().getDiagnostics());
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
        Path src = dir.resolve("hfare-nat-" + tag + ".kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-hfare-nat-" + tag);
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE x86-64 double-HFA-return must bind: "
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

    private void runQemuCrossFixture(Path dir, String arch, Target t, String fixtureAsm, String kof, String tag)
            throws IOException, InterruptedException {
        Path s = dir.resolve("hfarefix-" + arch + ".s");
        Path o = dir.resolve("hfarefix-" + arch + ".o");
        Files.writeString(s, fixtureAsm);
        Process as = new ProcessBuilder(arch + "-linux-gnu-as", "-o", o.toString(), s.toString())
                .redirectErrorStream(true).start();
        String asOut = new String(as.getInputStream().readAllBytes());
        assertTrue(as.waitFor(60, TimeUnit.SECONDS) && as.exitValue() == 0,
                "as " + arch + " falhou: " + asOut);

        Path src = dir.resolve("hfare-" + arch + "-" + tag + ".kf");
        Files.writeString(src, kof.formatted(o.toString()));
        Path out = dir.resolve("out-hfare-" + arch + "-" + tag);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), "M1 unidade-2: retorno homogêneo-flutuante deve bindar em "
                + arch + " (era FFI001 honesto até aqui): " + r.diagnostics().getDiagnostics());
        String outStr = NativeRiscv64E2ETest.runQemuWithLibPath(dir, arch, out.resolve("Default/Main"));
        assertEquals(tag.equals("vf") ? GOLDEN_VF : GOLDEN_VD, outStr,
                "JVM==" + arch + " (retorno HFA por ordinal FP)");
    }

    @Test
    void doubleStructReturnBindsOnJvmAndNativeHost(@TempDir Path dir) throws Exception {
        String so = buildHostLib(dir);
        String kof = KOF_VD.formatted(so);
        assertEquals(GOLDEN_VD, runJvm(dir, kof, "vd"),
                "oráculo JVM (FFM, retorno de struct por valor) deve produzir o golden");
        assertEquals(GOLDEN_VD, runNativeHost(dir, kof, "vd"),
                "struct homogêneo-double por valor no retorno do Native x86-64 (SysV xmm0/xmm1)");
    }

    @Test
    void floatStructReturnBindsOnJvm(@TempDir Path dir) throws Exception {
        String so = buildHostLib(dir);
        assertEquals(GOLDEN_VF, runJvm(dir, KOF_VF.formatted(so), "vf"),
                "oráculo JVM do retorno VF (float,float)");
    }

    @Test
    void doubleStructReturnCrossBindsAndMatchesJvm(@TempDir Path dir) throws Exception {
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64", FIXTURE_RISCV},
                                         {"aarch64", "NATIVE_AARCH64", FIXTURE_AARCH}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            String hostSo = buildHostLib(dir);
            assertEquals(GOLDEN_VD, runJvm(dir, KOF_VD.formatted(hostSo), "vd-" + arch),
                    "oráculo JVM deve concordar byte-a-byte (regra 5)");
            runQemuCrossFixture(dir, arch, t, a[2], KOF_VD, "vd");
        }
    }

    @Test
    void floatStructReturnCrossBindsAndMatchesJvm(@TempDir Path dir) throws Exception {
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64", FIXTURE_RISCV},
                                         {"aarch64", "NATIVE_AARCH64", FIXTURE_AARCH}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            String hostSo = buildHostLib(dir);
            assertEquals(GOLDEN_VF, runJvm(dir, KOF_VF.formatted(hostSo), "vf-" + arch),
                    "oráculo JVM do VF deve concordar (regra 5)");
            runQemuCrossFixture(dir, arch, t, a[2], KOF_VF, "vf");
        }
    }

    @Test
    void x86TwoFloatReturnStaysFfi001Honest(@TempDir Path dir) throws Exception {
        // SysV: os dois floats de VF cabem no MESMO eightbyte (multi-campo) e o
        // emissor x86 só cobre eightbyte SSE de campo único — FFI001 honesto na
        // declaração (R6), face própria x86 que NÃO é esta unidade cross.
        String so = buildHostLib(dir);
        Path src = dir.resolve("hfare-x86vf.kf");
        Files.writeString(src, KOF_VF.formatted(so));
        CompilationResult r = driver.compile(src, dir.resolve("out-hfare-x86vf"), Target.NATIVE);
        assertFalse(r.success(), "VF return (eightbyte SSE multi-campo na SysV) não pode bindar no x86-64");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "x86-64 mantém FFI001 honesto para o retorno VF: "
                        + r.diagnostics().getDiagnostics());
    }
}
