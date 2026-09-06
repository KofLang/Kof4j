package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofMapSetTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void mapSetJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                var m = mapOf()
                m.put("a", 1)
                m.put("b", 2)
                assert(m.get("a") == 1)
                assert(m.get("b") == 2)
                assert(m.contains("a"))
                assert(!m.contains("c"))
                assert(m.size() == 2)
                assert(m.remove("a") == 1)
                assert(m.size() == 1)
                assert(m.keys().size() == 1)
                assert(m.values().size() == 1)
                m.clear()
                assert(m.isEmpty())
                assert(m.size() == 0)

                var s = setOf()
                s.add(1)
                s.add(2)
                s.add(1)
                assert(s.size() == 2)
                assert(s.contains(1))
                assert(!s.contains(3))
                assert(s.remove(1))
                assert(s.size() == 1)
                s.clear()
                assert(s.isEmpty())
                println("ok")
            }
            """, "ok");
    }

    @Test
    void setMapAsFieldAndReturn(@TempDir Path tmp) throws Exception {
        // REGRESSION (JVM): Set<T>/Map<K,V> como campo de classe, param de
        // construtor e retorno de método — o JvmTypeMapper mapeava Set/Map para
        // kof.Set/kof.Map (NoClassDefFoundError: kof/Set); agora
        // java.util.HashSet/HashMap (runtime real). Cobre também o parse de
        // método de classe com retorno genérico (`Set<Int> all(`).
        String src = """
            class Bag(Set<Int> tags) {
                Set<Int> all() {
                    return tags
                }
            }
            Set<Int> evens() {
                return setOf(2, 4, 6)
            }
            Map<String,Int> counts() {
                var m = mapOf()
                m.put("a", 1)
                return m
            }
            main() {
                var b = Bag(setOf(1, 2, 3))
                println(b.all().size())
                println(b.all().contains(2))
                println(evens().size())
                println(counts().size())
                println(counts().get("a"))
            }
            """;
        String expected = "3\ntrue\n3\n1\n1";
        runJvm(tmp, src, expected);
        runNative(tmp, src, expected);
        runJs(tmp, src, expected);
    }

    @Test
    void mapSetJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                var m = mapOf()
                m.put("x", 10)
                println(m.get("x"))
                println(m.size())
                var s = setOf(1, 2, 3)
                println(s.size())
                println(s.contains(2))
                println("done")
            }
            """, "10\n1\n3\ntrue\ndone");
    }

    @Test
    void memberCallOnNullableInferredFromMapJVM(@TempDir Path tmp) throws Exception {
        // REGRESSION (bug 33): método chamado em receiver de tipo nullable
        // INFERIDO (ex.: `var v = m.get(k)` → V?) — o MethodCallTyper re-
        // inferia o receiver no lowering e o `instanceof ClassType` falhava
        // no NullableType → retorno do método saía `Object` →
        // NoSuchMethodError em runtime. Map/Set era só o caminho que produz
        // o local nullable; `var v = maybe()` (função retornando V?)
        // reproduz sem coleção.
        String src = """
            class View {
                String name
                public constructor(String name) { this.name = name }
                String render() { return "v:" + name }
            }
            View? maybe() { return View("a") }
            main() {
                var v = maybe()
                println(v.render())
                var m = mapOf("k", View("x"))
                var w = m.get("k")
                println(w.render())
            }
            """;
        runJvm(tmp, src, "v:a\nv:x");
    }

    @Test
    void mapSetNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                var m = mapOf()
                m.put("a", 1)
                m.put("b", 2)
                assert(m.get("a") == 1)
                assert(m.contains("b"))
                assert(m.size() == 2)
                assert(m.remove("a") == 1)
                m.clear()
                assert(m.isEmpty())

                var s = setOf(1, 2, 3)
                assert(s.size() == 3)
                assert(s.contains(2))
                assert(s.remove(1))
                println("ok-native")
            }
            """, "ok-native");
    }

    @Test
    void setAsDeclaredTypeJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            Set<String> makeSet(String a, String b) {
                var s = setOf(a, b)
                return s
            }

            class Holder {
                Set<Int> tags
                public constructor() {
                    tags = setOf(1, 2, 3)
                }
                Bool has(Int t) {
                    return tags.contains(t)
                }
                Set<Int> tagsOf() {
                    return tags
                }
            }

            main() {
                var s = makeSet("x", "y")
                assert(s.contains("x"))
                assert(s.size() == 2)
                var h = Holder()
                assert(h.has(2))
                assert(h.tags.size() == 3)
                var t = h.tagsOf()
                assert(t.contains(3))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void setAsDeclaredTypeJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            Set<String> makeSet(String a, String b) {
                var s = setOf(a, b)
                return s
            }
            main() {
                var s = makeSet("x", "y")
                println(s.contains("x"))
                println(s.size())
                println("done")
            }
            """, "true\n2\ndone");
    }

    @Test
    void setAsDeclaredTypeNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            class Holder {
                Set<Int> tags
                public constructor() {
                    tags = setOf(1, 2, 3)
                }
                Bool has(Int t) {
                    return tags.contains(t)
                }
            }
            main() {
                var h = Holder()
                assert(h.has(2))
                assert(h.tags.size() == 3)
                println("ok-native")
            }
            """, "ok-native");
    }

    @Test
    void mapGetNullableReferenceValue(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                var m = mapOf("name", "Mel")
                m.put("city", "SP")
                var name = m.get("name")
                if (name != null) {
                    println(name.length)
                } else {
                    println("null-name")
                }
                var missing = m.get("nope")
                if (missing == null) {
                    println("missing")
                } else {
                    println(missing.length)
                }
                println("done")
            }
            """, "3\nmissing\ndone");
    }

    @Test
    void mapGetNullableReferenceValueJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                var m = mapOf("name", "Mel")
                var name = m.get("name")
                println(name == null)
                println("done")
            }
            """, "false\ndone");
    }

    @Test
    void mapGetNullableReferenceValueNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                var m = mapOf("name", "Mel")
                var missing = m.get("nope")
                if (missing == null) {
                    println("missing")
                } else {
                    println("has")
                }
                println("done")
            }
            """, "missing\ndone");
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
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
            if (expected != null) assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
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
