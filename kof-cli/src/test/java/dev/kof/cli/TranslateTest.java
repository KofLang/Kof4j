package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof translate — Java subset → Kof (docs/future/TRANSLATOR.md, Fase F).
 * Static main becomes a top-level main(); println/equals map to idiomatic Kof.
 */
class TranslateTest {

    @Test
    void helloWorldMainBecomesTopLevelMain(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                """);

        assertTrue(kof.contains("main()"), "should emit top-level main():\n" + kof);
        assertTrue(kof.contains("println(\"Hello\")"), "should map println:\n" + kof);
        assertFalse(kof.contains("System.out"), "must drop System.out:\n" + kof);

        assertCompiles(dir, kof, "Hello");
    }

    @Test
    void methodsAndFieldsTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class Calc {
                    int total = 0;
                    public int add(int a, int b) {
                        return a + b;
                    }
                    public boolean isPositive(int x) {
                        return x > 0;
                    }
                }
                """);

        assertTrue(kof.contains("class Calc"), "should emit class Calc:\n" + kof);
        assertTrue(kof.contains("Int total"), "field should become typed field:\n" + kof);
        assertTrue(kof.contains("Int add"), "method return type should be Int:\n" + kof);
        assertTrue(kof.contains("Int add(Int a, Int b) = a + b"), "single-return method becomes expression body:\n" + kof);
        assertTrue(kof.contains("Bool isPositive"), "boolean → Bool:\n" + kof);
        assertFalse(kof.contains("public"), "must drop public modifier:\n" + kof);

        assertCompiles(dir, kof, null);
    }

    @Test
    void equalsAndControlFlowTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class Flow {
                    public static void main(String[] args) {
                        String s = "hello";
                        if (s.equals("hello")) {
                            System.out.println("match");
                        } else {
                            System.out.println("no");
                        }
                        int i = 0;
                        while (i < 3) {
                            i = i + 1;
                        }
                        System.out.println(i);
                    }
                }
                """);

        assertTrue(kof.contains("s == \"hello\""), "equals should become ==:\n" + kof);
        assertTrue(kof.contains("while (i < 3)"), "while loop preserved:\n" + kof);
        assertTrue(kof.contains("if ("), "if preserved:\n" + kof);

        assertCompiles(dir, kof, "match\n3");
    }

    @Test
    void recordTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public record Point(int x, int y) {
                }
                """);

        assertTrue(kof.contains("record Point(Int x, Int y)"), "record Java vira record Kof:\n" + kof);
        assertFalse(kof.contains("class Point"), "não deve virar class:\n" + kof);

        assertCompiles(dir, kof, null);
    }

    @Test
    void enumTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public enum Color { RED, GREEN, BLUE }
                """);

        assertTrue(kof.contains("enum Color"), "enum Java vira enum Kof:\n" + kof);
        assertTrue(kof.contains("RED"), "constante RED:\n" + kof);
        assertTrue(kof.contains("GREEN"), "constante GREEN:\n" + kof);
        assertTrue(kof.contains("BLUE"), "constante BLUE:\n" + kof);

        assertCompiles(dir, kof, null);
    }

    @Test
    void ternaryTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class T {
                    public static int max(int a, int b) { return a > b ? a : b; }
                }
                """);

        assertTrue(kof.contains("if (a > b) a else b"),
                "ternário deve virar if-expression:\n" + kof);
    }

    @Test
    void stringLengthProperty(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class S {
                    public static int size(String s) { return s.length(); }
                }
                """);

        assertTrue(kof.contains("s.length"), "length() vira propriedade .length:\n" + kof);
        assertFalse(kof.contains("s.length()"), "não deve manter length():\n" + kof);
    }

    private void assertCompiles(Path dir, String kof, String expected) throws Exception {
        Path src = dir.resolve("T.kf");
        Files.writeString(src, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "translated Kof must compile:\n" + kof + "\n" + result.diagnostics().getDiagnostics());

        if (expected != null) {
            ProcessBuilder pb = new ProcessBuilder(
                    javaHomeJava(), "-cp", dir.resolve("out").toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, proc.waitFor(), "run exit code\n" + output);
            assertEquals(expected.trim(), output.trim(), "runtime output mismatch");
        }
    }

    private static String javaHomeJava() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}