package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs / issue #1174 — `json.decode<String>(payload)` DIVERGED three ways
 * (one 1.0-block): JVM/Script returned the raw JSON text, JS returned the
 * PARSED value (`[object Object]` once concatenated) and the Native returned a
 * SILENT EMPTY.
 *
 * <p>THE CONTRACT (pinned by measuring the JVM oracle on the boundaries):
 * a QUOTED JSON string decodes to its UNESCAPED CONTENT (`"x"` → `x`); every
 * OTHER value (object, array, number, bool, null) returns the RAW JSON TEXT
 * trimmed at both ends. The record path (`json.decode<Pt>`) and the scalar
 * string path were already healthy on all four targets and are re-pinned here
 * as the non-regression control.
 *
 * <p>THE ROOTS (measured 19/11): the Native `kof_json_decode_string` returned
 * the builder EMPTY whenever the first non-space char was not `"` (its
 * `.Lkof_json_ds_open` jumped straight to done); the JS `JsJsonOps` fell into
 * `JSON.parse` for the String target (returning the parsed object). The fixes:
 * a raw-span append on both native backends (x86 `RuntimeJsonDecode2`, riscv
 * `NativeRiscvAsmJsonStr`, trim at both ends) and a `kofJsonDecodeString`
 * helper in the JS runtime registered by the dispatch.
 *
 * <p>RED-first: pre-fix the JS face printed `[object Object]` and the native
 * faces printed nothing for the object/array/scalar shapes.
 *
 * <p>BOUNDARY (measured 19/11): the RECORD path (`json.decode<Pt>`) is pinned
 * by {@link #jvmPinsTheRecordPathToo} on the JVM only — the cross link lacks
 * `kof_json_find_value`/`kof_json_require` (a pre-existing native-cross gap,
 * NOT part of this fix), so the record shape is excluded from the cross faces
 * here and stays covered by the JVM oracle test.
 */
class JsonDecodeStringOracleE2ETest extends NativeCrossSupport {

    private static final String SRC = """
            main() {
                println("[" + json.decode<String>("{\\"a\\":1}") + "]")
                println("[" + json.decode<String>("  {\\"a\\":1}  ") + "]")
                println("[" + json.decode<String>("[1,2]") + "]")
                println("[" + json.decode<String>("42") + "]")
                println("[" + json.decode<String>("true") + "]")
                println("[" + json.decode<String>("null") + "]")
                println("[" + json.decode<String>("\\"x\\"") + "]")
                println("[" + json.decode<String>("\\"a\\\\tb\\"") + "]")
            }
            """;

    /** The JVM oracle contract, byte-for-byte on every target. */
    private static final String GOLDEN = String.join("\n",
            "[{\"a\":1}]",
            "[{\"a\":1}]",
            "[[1,2]]",
            "[42]",
            "[true]",
            "[null]",
            "[x]",
            "[a\tb]");

    private Path writeSrc(Path tmp, String tag) throws Exception {
        Path p = tmp.resolve("Jds-" + tag + ".kf");
        Files.writeString(p, SRC, StandardCharsets.UTF_8);
        return p;
    }

    private String runScript(Path tmp, String tag) throws Exception {
        Path main = writeSrc(tmp, tag);
        KofInterpreter.Result r = driver.interpret(List.of(main), main.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), () -> "script: " + r.stderr());
        return r.stdout().strip();
    }

    private String runJs(Path tmp, String tag) throws Exception {
        Path main = writeSrc(tmp, tag);
        Path out = tmp.resolve("out-" + tag);
        CompilationResult r = driver.compile(main, out, Target.JS);
        assertTrue(r.success(), () -> "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    @Test
    void jvmPinsTheOracleContract(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runJvm(writeSrc(tmp, "jvm"), tmp.resolve("out-jvm")),
                "the JVM is the oracle — the contract below is measured from it");
    }

    @Test
    void scriptMatchesTheOracle(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runScript(tmp, "script"));
    }

    @Test
    void jsMatchesTheOracle(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runJs(tmp, "js"),
                "pre-fix the JS returned the PARSED object ('[object Object]')");
    }

    @Test
    void nativeX86MatchesTheOracle(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain x86-64 absent");
        assertEquals(GOLDEN, runNative(writeSrc(tmp, "nat"), tmp.resolve("out-nat")),
                "pre-fix the native returned SILENT EMPTY for the non-string shapes");
    }

    @Test
    void jvmPinsTheRecordPathToo(@TempDir Path tmp) throws Exception {
        // The record decode is HEALTHY on the JVM (the issue's own delimitation);
        // this control keeps it from regressing while the String face is fixed.
        Path src = tmp.resolve("Jrec.kf");
        String kof = "record Pt(Int x, Int y)\n"
                + "main() {\n"
                + "    var rec = json.decode<Pt>(\"{\\\"x\\\":9,\\\"y\\\":8}\")\n"
                + "    println(\"rec:\" + rec.x + \"/\" + rec.y)\n"
                + "}\n";
        Files.writeString(src, kof, StandardCharsets.UTF_8);
        assertEquals("rec:9/8", runJvm(src, tmp.resolve("out-rec")),
                "the documented record path stays healthy");
    }

    @Test
    void riscv64MatchesTheOracle(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("qemu-riscv64"), "qemu-riscv64 absent — environmental skip");
        assertEquals(GOLDEN, runCross(writeSrc(tmp, "riscv"), tmp.resolve("out-riscv"),
                "qemu-riscv64", Target.NATIVE_RISCV64), "the cross rides its own raw-span");
    }

    @Test
    void aarch64MatchesTheOracle(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("qemu-aarch64"), "qemu-aarch64 absent — environmental skip");
        assertEquals(GOLDEN, runCross(writeSrc(tmp, "aarch"), tmp.resolve("out-aarch"),
                "qemu-aarch64", Target.NATIVE_AARCH64), "the cross rides its own raw-span");
    }
}
