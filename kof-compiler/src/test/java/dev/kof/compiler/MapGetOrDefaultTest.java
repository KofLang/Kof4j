package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #386 slice 1/3 — `Map.getOrDefault(K, V): V`. The issue whitelist was
 * put/get/remove/containsKey/contains/size/clear/isEmpty/keys/values and
 * getOrDefault died with SEM025 even though the shape is the single most
 * requested map idiom (absence with a caller-provided default). Lowered to
 * kof_map_get_or_default across the shared IR: JVM delegates to
 * java.util.Map.getOrDefault (INVOKEINTERFACE), script to the host map, JS
 * to kofMapGetOrDefault over the kofMapKeyIdx canonical key, native to a
 * kof_map_find + values-slot read with the DEFAULT on the miss path (not
 * 0 — that is the whole point of the method). A default that pollutes the
 * pinned value type is rejected (same §126 wall as put's value).
 */
class MapGetOrDefaultTest {

    private static final String PROGRAM = """
            main() {
                val m: Map<String, Int> = mapOf()
                m.put("a", 1)
                println(m.getOrDefault("a", 7))
                println(m.getOrDefault("b", 0))
                val s: Map<String, String> = mapOf()
                println(s.getOrDefault("k", "fb"))
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + name + t), t);
    }

    @Test
    void getOrDefaultRunsOnJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "V", PROGRAM, Target.JVM);
        assertTrue(r.success(), "#386 verbatim must compile: " + r.diagnostics().getDiagnostics());
        String javaCmd = TestJdk.javaBin();
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out-VJVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run must exit 0, got:\n" + out);
        assertEquals("1\n0\nfb", out, "hit / primitive-miss default / reference-miss default");
    }

    @Test
    void objectValuedSlotPrimitiveDefaultRunsOnJvm(@TempDir Path tempDir) throws Exception {
        // §432 — Map<String,Object> with a primitive default: the emitter
        // resolved the slot V from the call-site argument (Double) and the
        // result path unboxed to double, so the consumer expecting Object
        // died with VerifyError: Type double_2nd is not assignable to Object.
        // V of the owner must govern the RESULT; the argument only boxes the
        // default. JVM-only face (Native/Script/JS are green, §352).
        CompilationResult r = compile(tempDir, "O", """
                main() {
                    val o: Map<String, Object> = mapOf()
                    o.put("d", 2.5)
                    println(o.getOrDefault("d", 9.5))
                    println(o.getOrDefault("x", 9.5))
                }
                """, Target.JVM);
        assertTrue(r.success(), "#432 verbatim must compile: " + r.diagnostics().getDiagnostics());
        String javaCmd = TestJdk.javaBin();
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out-OJVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run must exit 0 (VerifyError fails verification), got:\n" + out);
        assertEquals("2.5\n9.5", out, "hit returns the stored Object; miss returns the boxed primitive default");
    }

    @Test
    void primitiveKeyGetOrDefaultRunsOnJvm(@TempDir Path tempDir) throws Exception {
        // A PRIMITIVE key: the default is pushed after the key, so boxing the
        // key *after* the default consumed the default as the `int` operand of
        // Integer.valueOf → VerifyError when the key is Int/Long/Double/Bool.
        // The box order must follow put/putIfAbsent (default first, key last).
        CompilationResult r = compile(tempDir, "K", """
                main() {
                    val m: Map<Int, String> = mapOf()
                    m.put(1, "a")
                    println(m.getOrDefault(1, "z"))
                    println(m.getOrDefault(9, "z"))
                    val l: Map<Long, String> = mapOf()
                    l.put(2L, "b")
                    println(l.getOrDefault(2L, "z"))
                    println(l.getOrDefault(8L, "z"))
                    val d: Map<Double, String> = mapOf()
                    d.put(1.5, "c")
                    println(d.getOrDefault(1.5, "z"))
                    println(d.getOrDefault(2.5, "z"))
                    val bo: Map<Bool, String> = mapOf()
                    bo.put(true, "t")
                    println(bo.getOrDefault(true, "z"))
                    println(bo.getOrDefault(false, "z"))
                }
                """, Target.JVM);
        assertTrue(r.success(), "primitive-key getOrDefault must compile: "
                + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                tempDir.resolve("out-KJVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run must exit 0 (VerifyError fails verification), got:\n" + out);
        assertEquals("a\nz\nb\nz\nc\nz\nt\nz", out,
                "each primitive key type: hit returns the stored value, miss the default");
    }

    @Test
    void getOrDefaultRunsOnJs(@TempDir Path tempDir) throws Exception {
        // Script parity lives in kof-script KofScriptStdlibParityTest (the
        // interpreter runner is not on kof-compiler's classpath by design);
        // here JS runs through the in-JVM KofJsRunner like KofJsE2ETest.
        Path src = tempDir.resolve("J.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outJ");
        CompilationResult r = driver.compile(src, outDir, Target.JS);
        assertTrue(r.success(), "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String txt = out.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals("1\n0\nfb", txt, "js output");
    }

    @Test
    void getOrDefaultRunsOnNative(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outN");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(outDir.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run exit, output:\n" + out);
        assertEquals("1\n0\nfb", out, "native output");
    }

    @Test
    void defaultPollutingValuePinnedTypeIsRejected(@TempDir Path tempDir) throws Exception {
        // §126 wall mirrored from put: Long default into a Map<String,Int>
        // would poison the slot and die at unbox — SEM056 at compile time.
        CompilationResult r = compile(tempDir, "G", """
                main() {
                    val m: Map<String, Int> = mapOf()
                    m.put("a", 1)
                    println(m.getOrDefault("b", 5L))
                }
                """, Target.JVM);
        assertFalse(r.success(), "polluting default must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM056")),
                "SEM056 expected: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void objectValuedMapGetOrDefaultRunsOnJvm(@TempDir Path tempDir) throws Exception {
        // §432: a `Map<String, Object>` with a PRIMITIVE default. The owner's V
        // is Object but the default's arg type is Double — the JVM emitter used
        // to overwrite the slot's V with the arg type, box/checkcast+unbox the
        // result as Double while the printer called String.valueOf(Object),
        // producing `VerifyError: Type double_2nd is not assignable to Object`.
        // The default must be boxed as its own type; the RESULT is the owner's V.
        CompilationResult r = compile(tempDir, "O", """
                main() {
                    val o: Map<String, Object> = mapOf()
                    o.put("d", 2.5)
                    println(o.getOrDefault("d", 9.5))
                    println(o.getOrDefault("missing", 9.5))
                }
                """, Target.JVM);
        assertTrue(r.success(), "object-valued map must compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                tempDir.resolve("out-OJVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run must exit 0 (no VerifyError), got:\n" + out);
        assertEquals("2.5\n9.5", out, "hit value / primitive default on a miss (V=Object)");
    }

    @Test
    void remainingTrioFlippedToWork(@TempDir Path tempDir) throws Exception {
        // #386 slice 2: containsValue landed (4 targets, slice 3). The
        // old guard asserted SEM025 here "until slice 2" — per its own
        // comment, this test flips in the commit that implements them.
        // putIfAbsent's full matrix lives in CollectionMethodsStdlibE2ETest.
        CompilationResult r = compile(tempDir, "R", """
                main() {
                    val m: Map<String, Int> = mapOf()
                    m.put("a", 1)
                    println(m.containsValue(1))
                    println(m.containsValue(2))
                }
                """, Target.JVM);
        assertTrue(r.success(), "containsValue must compile now: " + r.diagnostics().getDiagnostics());
        String javaCmd = TestJdk.javaBin();
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out-RJVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run exit 0, got:\n" + out);
        assertEquals("true\nfalse", out, "hit / miss on values");
    }
}
