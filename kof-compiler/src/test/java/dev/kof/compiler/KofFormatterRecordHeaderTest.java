package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KofFormatterRecordHeaderTest {

    private static final String GENERIC_RECORDS = """
            class Box<T>(T value) {
                T get() {
                    return value
                }
            }

            record Pair<A, B>(A first, B second)

            main() {
                var b = Box(42)
                println(b.get())
                var p = Pair("k", 7)
                println(p.first + p.second)
            }
            """;

    private static final String RECORDS_WITH_SUPERCLASS = """
            abstract class Base<T> {
                abstract T value()
                T twice() {
                    return value()
                }
            }

            class Impl(Int n) extends Base<Int> {
                Int value() {
                    return n
                }
            }

            class Plain {
                static Int LIMIT = 42
            }

            class Sub() extends Plain {
                Int lim() {
                    return LIMIT
                }
            }

            main() {
                Base<Int> x = Impl(4)
                println(x.twice())
                println(Sub().lim())
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void recordTypeParametersAreKept() throws Exception {
        String out = formatted(GENERIC_RECORDS);
        assertTrue(out.contains("record Box<T>(T value) {"), out);
        assertTrue(out.contains("record Pair<A, B>(A first, B second)"), out);
        assertFormattedProgramMatches("generic", GENERIC_RECORDS, out);
    }

    @Test
    void recordWithSuperclassKeepsAParseableHeader() throws Exception {
        String out = formatted(RECORDS_WITH_SUPERCLASS);
        assertTrue(out.contains("class Impl(Int n) extends Base<Int> {"), out);
        assertTrue(out.contains("class Sub() extends Plain {"), out);
        assertFormattedProgramMatches("superclass", RECORDS_WITH_SUPERCLASS, out);
    }

    @Test
    void plainRecordHeaderIsUnchanged() {
        String src = "record Point(Int x, Int y) implements Comparable\n";
        assertEquals(src, KofFormatter.format(src, "Main.kf"));
    }

    private static String formatted(String src) {
        String out = KofFormatter.format(src, "Main.kf");
        assertNotNull(out, "valid source must take the AST path");
        assertEquals(out, KofFormatter.format(out, "Main.kf"), "formatting the output must not change it");
        return out;
    }

    private void assertFormattedProgramMatches(String name, String original, String formatted) throws Exception {
        assertEquals(run(name + "-original", original), run(name + "-formatted", formatted));
    }

    private String run(String name, String src) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve(name + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), name + ": " + r.diagnostics().getDiagnostics() + "\n" + src);
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        String text = buf.toString(StandardCharsets.UTF_8);
        assertFalse(text.isBlank(), name + " printed nothing");
        return text;
    }
}
