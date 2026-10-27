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

class KofFormatterDeclarationsTest {

    private static final String INTERFACES = """
            interface Named {
                String name()
            }

            interface Sized {
                Int size()
            }

            interface Holder<T> extends Named, Sized {
                T first()
            }

            class IntHolder implements Holder<Int> {
                Int v
                constructor(Int x) {
                    v = x
                }
                String name() {
                    return "ints"
                }
                Int size() {
                    return 1
                }
                Int first() {
                    return v
                }
            }

            main() {
                Holder<Int> h = IntHolder(7)
                println(h.name())
                println(h.size())
                println(h.first())
            }
            """;

    private static final String RECORD_BODY = """
            record Point(Int x, Int y) {
                Int sum() {
                    return x + y
                }
                Point moved(Int dx) {
                    var nx = x + dx
                    return Point(nx, y)
                }
            }

            main() {
                var p = Point(1, 2)
                println(p.sum())
                println(p.moved(3).x)
            }
            """;

    private static final String APPLICATION = """
            application {
                onStart {
                    println("start")
                }
                onShutdown {
                    println("stop")
                }
            }

            main() {
                println("main")
            }
            """;

    private static final String INFRA = """
            import kof.makealive

            infra "prod" {
                resource("service", "web")
                prop("web", "region", "us-east")
                requires("web", "db")
                resource("db", "db")
            }

            main() {
                var p = plan(design(), State())
                println("creates=" + p.creates.size())
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void interfacesAreFormattedNotDumped() throws Exception {
        String out = formatted(INTERFACES);
        assertTrue(out.contains("interface Holder<T> extends Named, Sized {\n    T first();\n}"), out);
        assertSameProgram("interfaces", INTERFACES, out);
    }

    @Test
    void recordBodyIsFormattedNotDumped() throws Exception {
        String out = formatted(RECORD_BODY);
        assertTrue(out.contains("record Point(Int x, Int y) {\n    Int sum() = x + y\n"), out);
        assertSameProgram("record", RECORD_BODY, out);
    }

    @Test
    void applicationBlockIsFormattedNotDumped() throws Exception {
        String out = formatted(APPLICATION);
        assertTrue(out.contains("application {\n    onStart {\n        println(\"start\")\n    }\n"
                + "    onShutdown {\n        println(\"stop\")\n    }\n}"), out);
        assertSameProgram("application", APPLICATION, out);
    }

    @Test
    void infraBlockIsFormattedNotDumped() throws Exception {
        String out = formatted(INFRA);
        assertTrue(out.contains("infra \"prod\" {\n    resource(\"service\", \"web\")\n"), out);
        assertSameProgram("infra", INFRA, out);
    }

    @Test
    void classMembersKeepTheirShape() {
        String src = """
                class Counter {
                    Int n = 0
                    constructor(Int start) {
                        n = start
                    }
                    Int next() {
                        n = n + 1
                        return n
                    }
                    Int peek() {
                        return n
                    }
                }
                """;
        assertEquals("""
                class Counter {
                    Int n = 0
                    constructor(start: Int) {
                        n = start
                    }
                    Int next() {
                        n = n + 1
                        return n
                    }
                    Int peek() = n
                }
                """, KofFormatter.format(src, "Main.kf"));
    }

    @Test
    void portukofInterfaceAndApplicationUseTheProfileWords() {
        String src = """
                interface Nomeado {
                    texto nome()
                }

                interface Rotulo estende Nomeado {
                    texto rotulo()
                }

                aplicacao {
                    onStart {
                        escrevaln("inicio")
                    }
                }

                principal() {
                    escrevaln("ok")
                }
                """;
        String out = KofFormatter.format(src, "Main.ptkf");
        assertNotNull(out);
        assertNoDump(out);
        assertTrue(out.contains("interface Rotulo estende Nomeado {\n    texto rotulo();\n}"), out);
        assertTrue(out.contains("aplicacao {\n    onStart {\n        escrevaln(\"inicio\")\n"), out);
        assertFalse(out.contains("extends"), out);
        assertFalse(out.contains("application"), out);
        assertEquals(out, KofFormatter.format(out, "Main.ptkf"));
    }

    private static String formatted(String src) {
        String out = KofFormatter.format(src, "Main.kf");
        assertNotNull(out, "valid source must take the AST path");
        assertNoDump(out);
        assertEquals(out, KofFormatter.format(out, "Main.kf"), "formatting the output must not change it");
        return out;
    }

    private static void assertNoDump(String out) {
        assertFalse(out.contains("SourcePosition"), out);
        assertFalse(out.contains("Node["), out);
    }

    private void assertSameProgram(String name, String original, String formatted) throws Exception {
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
