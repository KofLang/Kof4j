package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1169 (MATH-D, 16/11): floor/ceil/abs/min/max/clamp/sign DOUBLE nos TRÊS
 * nativos — x86-64 (SSE: roundsd/btrq) + riscv64 + aarch64 (as cadeias
 * branchy EXATAS do JDK via o texto B32 + o tradutor), sob qemu para os
 * cross. Oráculo regra 5: o MESMO programa no JVM e nos três nativos —
 * saídas byte-a-byte IGUAIS, incluindo as arestas (NaN propaga no
 * clamp/min/max, dsign(NaN)=0, ceil do negativo fracionário = -0.0, os
 * subnormais). RED-first: as faces recusavam MATH001 antes (medido).
 */
class MathDNativeE2ETest {

    private static final String PROBE = """
            main() {
                println(math.floor(2.7))
                println(math.ceil(2.1))
                println(math.floor(-2.7))
                println(math.ceil(-2.1))
                println(math.abs(-3.5))
                println(math.min(1.5, 2.5))
                println(math.max(1.5, 2.5))
                println(math.clamp(5.0, 0.0, 3.0))
                println(math.clamp(-1.0, 0.0, 3.0))
                println(math.sign(-2.0))
                println(math.sign(0.0))
                println(math.sign(9.0))
                println(math.floor(-0.5))
                println(math.ceil(-0.3))
                println(math.min(7.0, 7.0))
                println(math.max(-4.25, -4.5))
            }
            """;

    private static final String EXPECTED_JVM = "2.0\n3.0\n-3.0\n-2.0\n3.5\n1.5\n2.5\n3.0\n0.0\n-1\n0\n1\n-1.0\n-0.0\n7.0\n-4.25";

    /** §1134 follow-up (17/11): o multi-dim nos DOIS cross — o espelho de
     *  dims existe SÓ no aarch64 (a fatia 2 alargou os slots); o riscv64
     *  segue o layout original. O bisect pegou a quebra do riscv quando o
     *  espelho era incondicional. */
    private static final String MULTIDIM = """
            main() {
                var m = new Int[2][3]
                println(m.length)
                println(m[1].length)
                println(m[0][2])
                m[1][2] = 7
                println(m[1][2])
            }
            """;

    private static final String MULTIDIM_EXPECTED = "2\n3\n0\n7";

    private String runMultiDim(Target target, Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), MULTIDIM);
        Path out = dir.resolve("out-md-" + target.name().toLowerCase());
        CompilerDriver d = new CompilerDriver();
        CompilationResult r = d.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), target + " binary must exist");
        ProcessBuilder pb = target == Target.NATIVE
                ? new ProcessBuilder(bin.toString())
                : NativeRiscv64E2ETest.qemu(target == Target.NATIVE_AARCH64 ? "aarch64" : "riscv64", bin);
        if (target != Target.NATIVE) {
            String sysroot = "/usr/" + (target == Target.NATIVE_AARCH64 ? "aarch64" : "riscv64")
                    + "-suse-linux/sys-root";
            if (Files.isDirectory(Path.of(sysroot))) pb.environment().put("QEMU_LD_PREFIX", sysroot);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String s = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), target + " exit, output: " + s);
        return s;
    }

    @Test
    void multiDimParityOnBothCrosses(@TempDir Path dir) throws Exception {
        assertEquals(MULTIDIM_EXPECTED, runMultiDim(Target.NATIVE_RISCV64, dir.resolve("md-rv")),
                "riscv64 multi-dim (o layout original)");
        assertEquals(MULTIDIM_EXPECTED, runMultiDim(Target.NATIVE_AARCH64, dir.resolve("md-aarch")),
                "aarch64 multi-dim (o espelho de dims da fatia 2)");
    }

    private static String runJvm(Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), PROBE);
        Path out = dir.resolve("out-jvm");
        CompilerDriver d = new CompilerDriver();
        CompilationResult r = d.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM exit: " + s);
        return s;
    }

    private String runNative(Target target, Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), PROBE);
        Path out = dir.resolve("out-" + target.name().toLowerCase());
        CompilerDriver d = new CompilerDriver();
        CompilationResult r = d.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), target + " binary must exist");
        ProcessBuilder pb = target == Target.NATIVE
                ? new ProcessBuilder(bin.toString())
                : NativeRiscv64E2ETest.qemu(target == Target.NATIVE_AARCH64 ? "aarch64" : "riscv64", bin);
        if (target != Target.NATIVE) {
            String sysroot = "/usr/" + (target == Target.NATIVE_AARCH64 ? "aarch64" : "riscv64")
                    + "-suse-linux/sys-root";
            if (Files.isDirectory(Path.of(sysroot))) pb.environment().put("QEMU_LD_PREFIX", sysroot);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String s = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), target + " exit, output: " + s);
        return s;
    }

    @Test
    void mathDParityJvmAndThreeNatives(@TempDir Path dir) throws Exception {
        String jvm = runJvm(dir);
        assertEquals(EXPECTED_JVM, jvm, "the JVM oracle");
        assertEquals(jvm, runNative(Target.NATIVE, dir), "x86-64 vs JVM");
        assertEquals(jvm, runNative(Target.NATIVE_RISCV64, dir), "riscv64 vs JVM");
        assertEquals(jvm, runNative(Target.NATIVE_AARCH64, dir), "aarch64 vs JVM");
    }
}
