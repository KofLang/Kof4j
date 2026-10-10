package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-INTEROP-ERR-TYPE (02/10): erro de interop estrangeiro é um tipo REAL e
 * catchável do idioma — builtin {@code InteropError} com {@code message}/{@code
 * code} (vocabulário {@code INTEROP00x}; {@code INTEROP010} = falha de downcall
 * FFI, novo código da face). O contrato {@code catch (String)} é CONGELADO e
 * continua pegando a falha com o código nomeado na mensagem; {@code catch
 * (InteropError e)} NUNCA engole um throw String comum (aninhado prova). Fora
 * do JVM a face não existe (ffi estrangeira só binda no JVM/JS e o JS bridge
 * não exporta o tipo) — recusa nomeada {@code INTEROP009}, nunca stub
 * silencioso (R6).
 */
class InteropErrorE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    // A chamada extern que falha (símbolo inexistente em libc.so.6) é a mesma
    // que o FfiE2ETest exercita por sucesso; aqui o gap é o caminho de erro.
    private static final String BAD_SYMBOL = """
            extern "libc.so.6" definitely_missing_symbol_x(Int x): Int
            """;

    @Test
    void foreignFailureIsCatchableTypedOnJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ie-catch.kf");
        Files.writeString(src, BAD_SYMBOL + """

                main() {
                    try {
                        var r = definitely_missing_symbol_x(1)
                        println("ok")
                    } catch (InteropError e) {
                        println("code=" + e.code())
                        println("named=" + (e.message().indexOf("INTEROP010") >= 0))
                    }
                }
                """);
        CompilationResult result = driver.compile(src, dir.resolve("out-jvm"), Target.JVM);
        assertTrue(result.success(), "typed InteropError catch must compile on JVM: "
                + result.diagnostics().getDiagnostics());
        assertEquals("code=INTEROP010\nnamed=true", runJvm(dir.resolve("out-jvm")));
    }

    @Test
    void typedCatchHasMessageAndCodePropertiesOnJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ie-props.kf");
        Files.writeString(src, BAD_SYMBOL + """

                main() {
                    try {
                        var r = definitely_missing_symbol_x(1)
                        println("ok")
                    } catch (InteropError e) {
                        println(e.code() == e.message().substring(0, 10))
                    }
                }
                """);
        CompilationResult result = driver.compile(src, dir.resolve("out-jvm"), Target.JVM);
        assertTrue(result.success(), "properties/methods must compile: "
                + result.diagnostics().getDiagnostics());
        assertEquals("true", runJvm(dir.resolve("out-jvm")));
    }

    @Test
    void stringCatchFrozenContractStillSeesNamedFailure(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ie-string.kf");
        Files.writeString(src, BAD_SYMBOL + """

                main() {
                    try {
                        var r = definitely_missing_symbol_x(1)
                        println("ok")
                    } catch (String s) {
                        println("named=" + (s.indexOf("INTEROP010") >= 0))
                    }
                }
                """);
        CompilationResult result = driver.compile(src, dir.resolve("out-jvm"), Target.JVM);
        assertTrue(result.success(), "String-catch contract is frozen: "
                + result.diagnostics().getDiagnostics());
        assertEquals("named=true", runJvm(dir.resolve("out-jvm")));
    }

    @Test
    void typedCatchNeverSwallowsOrdinaryStringThrow(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ie-noswallow.kf");
        Files.writeString(src, """
                main() {
                    try {
                        try {
                            throw "boom"
                        } catch (InteropError e) {
                            println("wrong")
                        }
                    } catch (String s) {
                        println("saw=" + s)
                    }
                }
                """);
        CompilationResult result = driver.compile(src, dir.resolve("out-jvm"), Target.JVM);
        assertTrue(result.success(), "nested catch must compile: "
                + result.diagnostics().getDiagnostics());
        assertEquals("saw=boom", runJvm(dir.resolve("out-jvm")));
    }

    @Test
    void uncaughtForeignFailureIsNamedOnJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ie-uncaught.kf");
        Files.writeString(src, BAD_SYMBOL + """

                main() {
                    var r = definitely_missing_symbol_x(1)
                    println("ok")
                }
                """);
        CompilationResult result = driver.compile(src, dir.resolve("out-jvm"), Target.JVM);
        assertTrue(result.success(), "uncaught failure is a RUNTIME event: "
                + result.diagnostics().getDiagnostics());
        String output = runJvmAllowFailure(dir.resolve("out-jvm"));
        assertTrue(output.contains("InteropError"), "must name the type, got: " + output);
        assertTrue(output.contains("INTEROP010"), "must name the code, got: " + output);
    }

    @Test
    void typedCatchRefusedOnScriptJsAndNativeWithNamedCode(@TempDir Path dir) throws IOException {
        // SCRIPT roda o frontend com target=JVM; a recusa sai via interpret.
        Path kf = dir.resolve("ie-refuse-script.kf");
        Files.writeString(kf, """
                main() {
                    try {
                        println(1)
                    } catch (InteropError e) {
                        println(e.code())
                    }
                }
                """);
        KofInterpretException ex = assertThrows(KofInterpretException.class,
                () -> driver.interpret(java.util.List.of(kf), dir, new String[0]));
        assertTrue(ex.getMessage().contains("INTEROP009"),
                "SCRIPT refusal must carry INTEROP009, got: " + ex.getMessage());

        for (Target t : new Target[] { Target.JS, Target.NATIVE }) {
            Path src = dir.resolve("ie-refuse-" + t.name() + ".kf");
            Files.writeString(src, """
                    main() {
                        try {
                            println(1)
                        } catch (InteropError e) {
                            println(e.code())
                        }
                    }
                    """);
            CompilationResult result = driver.compile(src, dir.resolve("out-" + t.name()), t);
            assertFalse(result.success(), t + " must refuse the unported face, got: "
                    + result.diagnostics().getDiagnostics());
            String diags = result.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("INTEROP009"),
                    t + " refusal must carry INTEROP009, got: " + diags);
        }
    }

    private String runJvm(Path outDir) throws IOException {
        String output = runJvmAllowFailure(outDir);
        assertEquals(0, lastExit, "JVM exit code, output: " + output);
        return output;
    }

    private int lastExit = -1;

    private String runJvmAllowFailure(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            lastExit = p.waitFor();
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
