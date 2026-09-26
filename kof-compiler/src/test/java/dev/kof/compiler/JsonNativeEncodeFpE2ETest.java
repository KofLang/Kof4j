package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §512 — regressão do bug que colapsava elementos Double/Long crus no
 * {@code json.encode} nativo: {@code listTag} devolvia 0 para eles e o loop
 * {@code kof_json_encode_list} despejava {@code kof_json_encode_int} sobre os
 * bits IEEE (List<Double> virava lixo determinístico). Prova JVM≡x86 com o
 * oráculo JVM medido, incluindo {@code Map<String,Double>} (mesmo walker,
 * tag 3). Cutucado pelo motor Python do kof.interop (X2, 26/09).
 */
class JsonNativeEncodeFpE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir
    Path tmp;

    private String runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compila: " + diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return buf.toString(StandardCharsets.UTF_8);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("THROW: " + e.getCause(), e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private String runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "NATIVE compila: " + diags(r));
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "nativo saiu com erro:\n" + o);
        return o;
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : r.diagnostics().getDiagnostics()) {
            sb.append(d.code()).append(": ").append(d.message()).append('\n');
        }
        return sb.toString();
    }

    private static final String SRC = """
            main() {
                println(json.encode(listOf(1.5, 2.25)))
                println(json.encode(listOf(7L, 8L)))
                println(json.encode(mapOf("x", 1.5)))
                println(json.encode(listOf(1, 2)))
                println(json.encode(listOf("a", "b")))
                println(json.encode(listOf(true)))
            }
            """;

    @Test
    void doubleLongListAndDoubleMapEncodeMatchJvmOracle() throws Exception {
        Path src = tmp.resolve("fp.kf");
        Files.writeString(src, SRC);
        String jvm = runJvm(src, tmp.resolve("o-jvm"));
        String nat = runNative(src, tmp.resolve("o-nat"));
        assertEquals("[1.5,2.25]\n[7,8]\n{\"x\":1.5}\n[1,2]\n[\"a\",\"b\"]\n[true]\n", jvm,
                "oráculo JVM (linhas 1-3 são o caso do §512; 4-6 o vizinho que sempre funcionou)");
        assertEquals(jvm, nat, "x86≡JVM no encode de listas de Double/Long e map de Double (§512)");
    }
}
