package dev.kof.compiler;

import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixture compartilhada dos testes de {@code Set}/{@code equals}/ {@code hashCode}
 * ({@code KofSetEqualityTest}): as fontes Kof reutilizáveis ({@code PT}/{@code HC}/
 * {@code PLAIN}/{@code ONLY_EQ}, classes de apoio dos programas) e os runners
 * JVM/JS ({@code both}/{@code runJvm}/{@code runJs}/{@code findJsEntry}). Vive
 * fora da classe de teste para mantê-la abaixo do limite de 500 linhas de teste
 * (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); nenhum
 * teste mudou de lugar e o {@code KofSetEqualityTest} segue com os 21 casos.
 */
abstract class KofSetEqualitySupport {
    protected final CompilerDriver driver = new CompilerDriver();

    /** equals + hashCode consistentes, por valor. */
    protected static final String PT = """
        class Pt {
            Int x
            Int y
            public constructor(Int x, Int y) {
                this.x = x
                this.y = y
            }
            override Bool equals(Object o) {
                if (o instanceof Pt) {
                    var p = o as Pt
                    return p.x == x && p.y == y
                }
                return false
            }
            override Int hashCode() {
                return x * 31 + y
            }
        }
        """;

    /** hashCode controlado pelo teste; equals olha só {@code v}. */
    protected static final String HC = """
        class Hc {
            Int v
            Int h
            public constructor(Int v, Int h) {
                this.v = v
                this.h = h
            }
            override Bool equals(Object o) {
                if (o instanceof Hc) {
                    var c = o as Hc
                    return c.v == v
                }
                return false
            }
            override Int hashCode() {
                return h
            }
        }
        """;

    /** Sem equals/hashCode: identidade. */
    protected static final String PLAIN = """
        class Plain {
            Int v
            public constructor(Int v) {
                this.v = v
            }
        }
        """;

    /** equals sobrescrito SEM hashCode — viola o contrato de propósito. */
    protected static final String ONLY_EQ = """
        class OnlyEq {
            Int v
            public constructor(Int v) {
                this.v = v
            }
            override Bool equals(Object o) {
                if (o instanceof OnlyEq) {
                    var c = o as OnlyEq
                    return c.v == v
                }
                return false
            }
        }
        """;

    /** Roda o mesmo programa em JVM e JS e exige saída idêntica ao esperado. */
    protected void both(Path tmp, String source, String expected) throws Exception {
        runJvm(tmp, source, expected);
        runJs(tmp, source, expected);
    }

    protected String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    protected String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            assertEquals(expected, output, "JS output");
            return output;
        }
    }

    protected static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
        }
    }
}
