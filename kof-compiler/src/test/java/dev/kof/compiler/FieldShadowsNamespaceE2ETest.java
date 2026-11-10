package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1062 — a class field named like a stdlib namespace (`cache`, `buffer`,
 * `config`) was LOWERED AS THE NAMESPACE: the analyzer resolves the bare
 * name through the scope (the field shadows the namespace) but the lowering
 * decided "namespace or value" with `isLocalVarName`, which looks at the IR
 * LOCALS only — a field read through the implicit `this` is not a local, so
 * every `cache.`/`buffer.`/`config.` call took the namespace arm in silence
 * (`none` printed instead of `v`), or crashed (VerifyError on the lambda
 * face, `no lowering for 'buffer.get()'`, broken conditions on `config.`).
 * The contract: a declared field is a local name, the stdlib namespace is
 * the fallback.
 */
class FieldShadowsNamespaceE2ETest {

    @TempDir
    Path tempDir;

    private String runOnJvm(String source) throws Exception {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve("out");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "exit 0, output: " + output);
        return output;
    }

    /** The issue's exact shape: a field `cache` shadows the cache namespace. */
    private static final String CACHE_FIELD = """
            class TtlCache {
                Map<String, String> m = mapOf("k", "v")
                String? get(k: String) = m.get(k)
            }
            class C {
                TtlCache cache = TtlCache()
                String f(k: String) {
                    var v = cache.get(k)
                    if (v == null) { return "none" }
                    return v
                }
            }
            main() { println(C().f("k")) }
            """;

    /** A local shadows too (the already-correct face — parity guard). */
    private static final String LOCAL_SHADOW = """
            main() {
                var cache = TtlCache2()
                println(cache.get("k"))
            }
            class TtlCache2 {
                Map<String, String> m = mapOf("k", "v")
                String? get(k: String) = m.get(k)
            }
            """;

    /** No shadowing: the namespace call is unchanged (parity guard). */
    private static final String NAMESPACE_UNSHADOWED = """
            main() {
                cache.set("a", "1")
                println(cache.get("a"))
            }
            """;

    /** A field named `buffer` with a method call on it. */
    private static final String BUFFER_FIELD = """
            class Buf {
                Int[] data = new Int[2]
                Int at(i: Int) = data[i]
            }
            class Holder {
                Buf buffer = Buf()
                Int pick() { return buffer.at(1) }
            }
            main() { println(Holder().pick()) }
            """;

    @Test
    void fieldNamedCacheShadowsTheNamespaceJvm() throws Exception {
        String out = runOnJvm(CACHE_FIELD);
        assertEquals("v", out.strip(), "the field's method answers, not the stdlib cache: " + out);
    }

    @Test
    void localShadowStillWinsJvm() throws Exception {
        String out = runOnJvm(LOCAL_SHADOW);
        assertEquals("v", out.strip(), "a local named cache keeps shadowing: " + out);
    }

    @Test
    void namespaceUnshadowedStillWorksJvm() throws Exception {
        String out = runOnJvm(NAMESPACE_UNSHADOWED);
        assertEquals("1", out.strip(), "the cache namespace keeps working with no shadowing: " + out);
    }

    @Test
    void fieldNamedBufferCallsItsOwnMethodJvm() throws Exception {
        String out = runOnJvm(BUFFER_FIELD);
        assertEquals("0", out.strip(), "buffer.at(1) is the field's method (data[1] = 0): " + out);
    }

    @Test
    void fieldNamedCacheShadowsTheNamespaceJsToo() throws Exception {
        // The SAME lowerer serves JS — the shadow fix is target-wide.
        Path src = tempDir.resolve("Js.kf");
        Files.writeString(src, CACHE_FIELD);
        Path out = tempDir.resolve("js-out");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        var stderr = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stderr);
        assertEquals(0, exit, "JS exit: stderr=" + stderr);
        assertEquals("v", stdout.toString(java.nio.charset.StandardCharsets.UTF_8).strip(),
                "the field's method answers on JS too: " + stdout);
    }
}
