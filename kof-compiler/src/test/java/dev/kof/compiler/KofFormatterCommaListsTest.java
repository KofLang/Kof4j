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

class KofFormatterCommaListsTest {

    private static final String INTERFACES = """
            interface A {
                Int a()
            }

            interface B {
                Int b()
            }

            class C implements A, B {
                Int a() {
                    return 1
                }
                Int b() {
                    return 2
                }
            }

            record P(Int x) implements A, B {
                Int a() {
                    return x
                }
                Int b() {
                    return x * 10
                }
            }

            main() {
                var c = C()
                println(c.a() + c.b())
                var p = P(3)
                println(p.a() + p.b())
            }
            """;

    private static final String GENERICS = """
            class Pair<K, V> {
                K key
                V value
                constructor(K k, V v) {
                    key = k
                    value = v
                }
            }

            first<X, Y>(X x, Y y): X {
                return x
            }

            main() {
                var p = Pair<String, Int>("k", 1)
                println(p.key + p.value)
                println(first<String, Int>("one", 2))
                var m = mapOf<String, Int>()
                m.put("a", 1)
                println(m.get("a"))
            }
            """;

    private static final String THROWS = """
            class ErrA {
                String msg
            }

            class ErrB {
                String msg
            }

            class Box {
                Int v
                constructor(Int x) throw ErrA, ErrB {
                    v = x
                }
                Int get() throw ErrA, ErrB {
                    return v
                }
            }

            check(Int n) throw ErrA, ErrB {
                println(n)
            }

            main() {
                check(Box(5).get())
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void implementsListsKeepTheirCommas() throws Exception {
        String out = formatted(INTERFACES);
        assertTrue(out.contains("class C implements A, B {"), out);
        assertTrue(out.contains("record P(Int x) implements A, B {"), out);
        assertFormattedRunsTheSame("interfaces", INTERFACES, out);
    }

    @Test
    void typeParametersAndTypeArgumentsKeepTheirCommas() throws Exception {
        String out = formatted(GENERICS);
        assertTrue(out.contains("class Pair<K, V> {"), out);
        assertTrue(out.contains("first<X, Y>("), out);
        assertTrue(out.contains("Pair<String, Int>(\"k\", 1)"), out);
        assertTrue(out.contains("first<String, Int>(\"one\", 2)"), out);
        assertTrue(out.contains("mapOf<String, Int>()"), out);
        assertFormattedRunsTheSame("generics", GENERICS, out);
    }

    @Test
    void throwListsKeepTheirCommas() throws Exception {
        String out = formatted(THROWS);
        assertTrue(out.contains("constructor(x: Int) throw ErrA, ErrB {"), out);
        assertTrue(out.contains("Int get() throw ErrA, ErrB = v"), out);
        assertTrue(out.contains("check(n: Int) throw ErrA, ErrB {"), out);
        assertFormattedRunsTheSame("throws", THROWS, out);
    }

    @Test
    void portukofListsKeepTheirCommas() {
        String src = """
                interface A {
                    Int a()
                }

                interface B {
                    Int b()
                }

                classe C implementa A, B {
                    Int a() {
                        retorna 1
                    }
                    Int b() {
                        retorna 2
                    }
                }

                principal() {
                    escrevaln(C().a())
                }
                """;
        String out = KofFormatter.format(src, "Main.ptkf");
        assertNotNull(out);
        assertTrue(out.contains("classe C implementa A, B {"), out);
        assertEquals(out, KofFormatter.format(out, "Main.ptkf"));
    }

    private static String formatted(String src) {
        String out = KofFormatter.format(src, "Main.kf");
        assertNotNull(out, "valid source must take the AST path");
        assertEquals(out, KofFormatter.format(out, "Main.kf"), "formatting the output must not change it");
        return out;
    }

    private void assertFormattedRunsTheSame(String name, String original, String formatted) throws Exception {
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
