package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #1123 — on JS a {@code List<Double>} printed without the {@code .0} of whole
 * values ({@code [2, 1.5]} instead of {@code [2.0, 1.5]}), because a JS number
 * carries no Int/Double distinction and the element formatter had no type. The
 * fix passes the container's element kind from the compiler to
 * {@code kofFormatTyped}, which routes each Double/Float through the same
 * {@code kofNumFmt} a scalar {@code println(2.0)} uses.
 *
 * <p>The JVM is the oracle, measured — the same shape as the §685 collection
 * concat test.
 */
class JsListDoubleFormatE2ETest extends ShellSupport {

    private static final String SRC = """
            main() {
                var xs = listOf(2.0, 1.5)
                println(xs)
                println("" + xs)
                var ys = listOf(1.0, 2.0, 3.0)
                println(ys)
                var ints = listOf(1, 2, 3)
                println(ints)
                var longs = listOf(1L, 2L)
                println(longs)
                var mixed = listOf("a", "b")
                println(mixed)
            }
            """;

    @Test
    void jvmIsTheContractForListDoubleFormat() throws Exception {
        Files.writeString(tmp.resolve("D.kf"), SRC);
        Run jvm = runJvm(tmp.resolve("D.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM falhou: " + jvm.output());
        String out = jvm.output().trim().replace("\r\n", "\n");
        assertTrue(out.contains("[2.0, 1.5]"), () -> "JVM oracle must keep the .0: " + out);
        assertTrue(out.contains("[1.0, 2.0, 3.0]"), () -> "JVM oracle: " + out);
    }

    @Test
    void jsMatchesTheJvmOnListDoubleFormat() throws Exception {
        Files.writeString(tmp.resolve("D.kf"), SRC);
        Run jvm = runJvm(tmp.resolve("D.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM falhou: " + jvm.output());
        String oracle = jvm.output().trim().replace("\r\n", "\n");

        Run js = runJs(tmp.resolve("D.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS falhou: " + js.output());
        assertEquals(oracle, js.output().trim().replace("\r\n", "\n"),
                "JS≡JVM na formatação de List<Double> (#1123)");
    }
}
