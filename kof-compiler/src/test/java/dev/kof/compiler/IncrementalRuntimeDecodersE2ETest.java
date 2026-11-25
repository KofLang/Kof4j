package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §1187 — a SECOND JVM build into the same output directory kept an
 * older generated runtime. The per-record `kof_json_decode_<pkg>_<Record>`
 * helpers are generated INSIDE `dev/kof/runtime/KofRuntime.class`, and
 * `JvmRuntime.ensureCompiled` returned as soon as that class existed — so a
 * project that gained a record after a first build emitted the new call but
 * the runtime still lacked its helper, and the jar died at runtime with
 * `NoSuchMethodError`.
 *
 * <p>Contract under test: two successive builds of the SAME output directory,
 * the second one adding a record, must both produce a runtime that carries every
 * decoder the emitted bytecode calls. The test asserts on the CLASS FILE of the
 * runtime (the observable contract), not on an internal cache flag.
 */
class IncrementalRuntimeDecodersE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String MAIN_ONE = """
            import app.Alfa
            import kof.json

            main() {
                var alfa = json.decode<Alfa>("{\\"a\\":1}")
                println("alfa=" + String.valueOf(alfa.a))
            }
            """;

    private static final String MAIN_TWO = """
            import app.Alfa
            import app.Beta
            import kof.json

            main() {
                var alfa = json.decode<Alfa>("{\\"a\\":1}")
                var beta = json.decode<Beta>("{\\"b\\":2}")
                println("alfa=" + String.valueOf(alfa.a) + " beta=" + String.valueOf(beta.b))
            }
            """;

    private Path sourceDir() throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src.resolve("app"));
        return src;
    }

    private void compile(Path src, Path main, Path out) {
        CompilationResult r = driver.compileSources(
                java.util.List.of(main),
                out,
                Target.JVM,
                src.toAbsolutePath().normalize());
        assertTrue(r.success(), "compile failed: " + r.diagnostics().getDiagnostics());
    }

    /** The runtime object's bytes carry the named decoder as a String constant. */
    private static boolean runtimeCarries(Path classesDir, String decoder) throws Exception {
        Path runtime = classesDir.resolve("dev/kof/runtime/KofRuntime.class");
        assertTrue(Files.exists(runtime), "the generated runtime must exist at " + runtime);
        return new String(Files.readAllBytes(runtime), java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains(decoder);
    }

    @Test
    void secondBuildAddingARecordRegeneratesItsDecoderJvm() throws Exception {
        Path src = sourceDir();
        Path out = tmp.resolve("out");
        Path main = tmp.resolve("Main.kf");

        Files.writeString(src.resolve("app/Alfa.kf"), """
                package app

                record Alfa(Int a)
                """);
        Files.writeString(main, MAIN_ONE);
        compile(src, main, out);
        assertTrue(runtimeCarries(out, "kof_json_decode_app_Alfa"),
                "the first build must generate the Alfa decoder");

        // Second build into the SAME output dir: a new record appears.
        Files.writeString(src.resolve("app/Beta.kf"), """
                package app

                record Beta(Int b)
                """);
        Files.writeString(main, MAIN_TWO);
        compile(src, main, out);
        assertTrue(runtimeCarries(out, "kof_json_decode_app_Alfa"),
                "the incremental build must keep the Alfa decoder");
        assertTrue(runtimeCarries(out, "kof_json_decode_app_Beta"),
                "known-bugs §1187: the incremental build must generate the NEW record's "
                        + "decoder — a stale KofRuntime.class leaves the call unbacked and the "
                        + "program dies with NoSuchMethodError at runtime");
    }
}
