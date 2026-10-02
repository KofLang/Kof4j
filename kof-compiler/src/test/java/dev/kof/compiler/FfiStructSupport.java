package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Suporte do FFI struct ABI ({@code FfiStructE2ETest}): a fonte C do shim, o
 * compile do host `.so`, localização do toolchain C e os runners JVM/Native/JS.
 * Vive fora da classe de teste para mantê-la abaixo do limite de 500 linhas de
 * teste (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os
 * testes e o nome da classe seguem no {@code FfiStructE2ETest} — zero drift de
 * citação.
 */
abstract class FfiStructSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    protected static final String C_SRC = """
            struct Point { int x; int y; };
            int sumpoint(struct Point p) { return p.x + p.y; }
            double scale(struct Point p, double f) { return (p.x + p.y) * f; }
            struct Point mkpoint(int x, int y) { struct Point p; p.x = x; p.y = y; return p; }
            struct Big { int a; int b; int c; };
            struct Big bigret(int a, int b, int c) { struct Big g; g.a = a; g.b = b; g.c = c; return g; }
            struct Mix { double d; int i; };
            struct Mix mixret(double d, int i) { struct Mix m; m.d = d; m.i = i; return m; }
            struct ParamMix { long l; double d; int i; };
            double parammix(struct ParamMix m) { return m.l + m.d + m.i; }
            struct ParamMix parammixret(long l, double d, int i) {
                struct ParamMix m; m.l = l; m.d = d; m.i = i; return m;
            }
            struct MixIF { int i; float f; };
            double mixif(struct MixIF m) { return m.i + m.f; }
            struct Time { long t; double d; };
            double timesum(struct Time t) { return (double)t.t + t.d; }
            struct Big3 { int a; int b; int c; int d; int e; };
            int bigsum(struct Big3 g) { return g.a + g.b + g.c + g.d + g.e; }
            """;

    protected static String compileHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "struct host lib usa um .so nativo (Linux)");
        Path c = dir.resolve("libkofpoint.c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofpoint.so");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de struct");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou ao compilar o host de struct: " + out);
        return so.toString();
    }

    protected static String firstPresent(String... candidates) {
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) {
                // tenta o próximo candidato
            }
        }
        return null;
    }

    protected String runJs(Path outDir) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, "JS exit code, output: " + out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    /** Compila p/ NATIVE, executa o binário e devolve stdout+stderr combinados. */
    protected String runNative(Path dir, String base, String kof) throws IOException {
        Path src = dir.resolve(base + "-nat.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-" + base + "-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE compile " + base + " (3.7 struct register path): "
                + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary " + bin + " deve existir");
        try {
            Process p = new ProcessBuilder(bin.toString())
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, () -> "NATIVE run " + base + " exit code, output: " + o);
            return o;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    protected String runJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
