package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


class ExceptionsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private String runNative(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    private static final String CATCH_SOURCE = """
            main() {
                try {
                    throw "boom"
                    println("unreachable")
                } catch (String e) {
                    println("caught: " + e)
                }
                println("done")
            }
            """;

    @Test
    void catchCatchesThrow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CATCH_SOURCE);
        runJvm(source, tempDir.resolve("out"), "caught: boom\ndone");
    }

    @Test
    void nativeCatchCatchesThrow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CATCH_SOURCE);
        runNative(source, tempDir.resolve("out"), "caught: boom\ndone");
    }

    @Test
    void finallyRunsOnNormalPath(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    println("body")
                } finally {
                    println("finally")
                }
                println("end")
            }
            """);
        runJvm(source, tempDir.resolve("out"), "body\nfinally\nend");
    }

    @Test
    void finallyRunsWithCaughtException(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "x"
                } catch (String e) {
                    println("caught")
                } finally {
                    println("finally")
                }
                println("end")
            }
            """);
        runJvm(source, tempDir.resolve("out"), "caught\nfinally\nend");
    }

    @Test
    void nestedFinallyPropagatesToOuterCatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    try {
                        throw "deep"
                    } finally {
                        println("inner finally")
                    }
                } catch (String e) {
                    println("outer caught: " + e)
                }
                println("end")
            }
            """);
        runJvm(source, tempDir.resolve("out"), "inner finally\nouter caught: deep\nend");
    }

    @Test
    void nativeNestedFinallyPropagatesToOuterCatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    try {
                        throw "deep"
                    } finally {
                        println("inner finally")
                    }
                } catch (String e) {
                    println("outer caught: " + e)
                }
                println("end")
            }
            """);
        runNative(source, tempDir.resolve("out"), "inner finally\nouter caught: deep\nend");
    }

    @Test
    void exceptionAcrossFrames(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            void inner() {
                throw "from-inner"
            }
            String outer() {
                try {
                    inner()
                    return "no"
                } catch (String e) {
                    return "got: " + e
                }
            }
            main() {
                try {
                    try {
                        throw "deep"
                    } finally {
                        println("inner finally")
                    }
                } catch (String e) {
                    println("outer caught: " + e)
                }
                try {
                    println("normal path")
                } finally {
                    println("finally normal")
                }
                println(outer())
                println("end")
            }
            """);
        String expected = "inner finally\nouter caught: deep\nnormal path\nfinally normal\ngot: from-inner\nend";
        runJvm(source, tempDir.resolve("out-jvm"), expected);
        runNative(source, tempDir.resolve("out-native"), expected);
    }

    @Test
    void multipleCatches(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "first"
                } catch (String e) {
                    println("c1: " + e)
                } catch (Exception e2) {
                    println("c2")
                }
                try {
                    throw "second"
                } catch (String e) {
                    println("c3: " + e)
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "c1: first\nc3: second");
    }

    @Test
    void uncaughtInInnerTryReachesOuter(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    try {
                        throw "up"
                    } catch (String e) {
                        println("inner caught")
                    }
                } catch (String e) {
                    println("outer caught")
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "inner caught");
    }

    // Issue #163 — typing `catch (RuntimeException e)` (a java.lang type
    // written by its simple name) left the catch local unqualified:
    // ClassType("", "RuntimeException"). Calling `e.getMessage()` then emitted
    // `LRuntimeException;` in the constant pool default package and loading
    // the class failed with NoClassDefFoundError: RuntimeException. The catch
    // type is now qualified to java.lang (StatementAnalyzer + StatementLowerer).
    @Test
    void typedCatchExceptionMethodCall(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "boom"
                } catch (RuntimeException e) {
                    println(e.getMessage())
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "boom");
    }

    // Issue #211 — same root as #163: the caught-variable type of ANY
    // java.lang throwable written by its simple name (Exception, Throwable,
    // ...) stayed unqualified, so e.getMessage() emitted
    // `invokevirtual Exception.getMessage` (default package) -> COMP002 /
    // NoClassDefFoundError. The catch header is now qualified to java.lang
    // for every throwable in CompilerTypes.JAVA_LANG_THROWABLES.
    @Test
    void typedCatchExceptionAndThrowable(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "boom"
                } catch (Exception e) {
                    println(e.getMessage())
                }
                try {
                    throw "deep"
                } catch (Throwable t) {
                    println(t.getMessage())
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "boom\ndeep");
    }

    // ── ICE do JS (18/09): `catch (Throwable e)` derrubava o JsTryParser
    // (colisão com a catch-all sintética que emula `finally`). Cobertura
    // control-flow apenas — o valor de um Throwable é interop JVM (D-NOT-JAVA),
    // não há representação byte-idêntica no JS. ──

    private String runJs(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "JS compilation should succeed (no ICE): "
                + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String output = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, ec, "JS exit code should be 0, output: '" + output + "'");
        assertEquals(expected, output, "Unexpected JS output");
        return output;
    }

    @Test
    void jsCatchThrowableRunsBody(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "boom"
                } catch (Throwable e) {
                    println("caught")
                }
                println("done")
            }
            """);
        runJs(source, tempDir.resolve("out"), "caught\ndone");
    }

    @Test
    void jsCatchThrowableWithFinally(@TempDir Path tempDir) throws IOException {
        // O caso de maior risco: um catch(Throwable) do USUÁRIO e a catch-all
        // sintética "#excTmp" do finally coexistem no mesmo try — o
        // discriminador precisa separá-los.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "boom"
                } catch (Throwable e) {
                    println("caught")
                } finally {
                    println("finally")
                }
            }
            """);
        runJs(source, tempDir.resolve("out"), "caught\nfinally");
    }

    // ── §765/#980: um throw levantado enquanto o OPERANDO de um `return` é
    // avaliado dentro de um `try` desvinculava o handler ANTES do operando
    // rodar (`StatementLowerer` emitia `KofExcUnlink` antes de
    // `ReturnValueLowerer.emitCoerced`) — Script e nativo x86-64 perdiam o
    // catch certo (ou, pior, um try externo capturava em silêncio e o
    // `finally` interno nunca rodava); JVM/JS sempre corretos. ──

    private String runScript(Path source, String expected) throws IOException {
        KofInterpreter.Result r = driver.interpret(java.util.List.of(source), source.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), "Script exit should be 0, stderr: " + r.stderr());
        String output = r.stdout().replace("\r\n", "\n").trim();
        assertEquals(expected, output, "Unexpected Script output");
        return output;
    }

    private static final String RETURN_OPERAND_THROW_SOURCE = """
            String boom() { throw "boom" }
            String f() {
                try {
                    return "v=" + boom()
                } catch (String m) {
                    return "caught: " + m
                }
            }
            main() { println(f()) }
            """;

    @Test
    void returnOperandThrowInsideTryIsCaughtByTheRightHandlerJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, RETURN_OPERAND_THROW_SOURCE);
        runJvm(source, tempDir.resolve("out"), "caught: boom");
    }

    @Test
    void returnOperandThrowInsideTryIsCaughtByTheRightHandlerNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, RETURN_OPERAND_THROW_SOURCE);
        runNative(source, tempDir.resolve("out"), "caught: boom");
    }

    @Test
    void returnOperandThrowInsideTryIsCaughtByTheRightHandlerJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, RETURN_OPERAND_THROW_SOURCE);
        runJs(source, tempDir.resolve("out"), "caught: boom");
    }

    @Test
    void returnOperandThrowInsideTryIsCaughtByTheRightHandlerScript(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, RETURN_OPERAND_THROW_SOURCE);
        runScript(source, "caught: boom");
    }

    /** O operando sem concat também tinha o bug — não era a concatenação o gatilho. */
    @Test
    void returnOperandThrowWithoutConcatIsCaughtOnAllTargets(@TempDir Path tempDir) throws IOException {
        String source = """
                String boom() { throw "boom" }
                String f() {
                    try {
                        return boom()
                    } catch (String m) {
                        return "caught: " + m
                    }
                }
                main() { println(f()) }
                """;
        Path jvmSrc = tempDir.resolve("jvm/Main.kf");
        Files.createDirectories(jvmSrc.getParent());
        Files.writeString(jvmSrc, source);
        runJvm(jvmSrc, tempDir.resolve("jvm/out"), "caught: boom");

        Path natSrc = tempDir.resolve("nat/Main.kf");
        Files.createDirectories(natSrc.getParent());
        Files.writeString(natSrc, source);
        runNative(natSrc, tempDir.resolve("nat/out"), "caught: boom");

        Path jsSrc = tempDir.resolve("js/Main.kf");
        Files.createDirectories(jsSrc.getParent());
        Files.writeString(jsSrc, source);
        runJs(jsSrc, tempDir.resolve("js/out"), "caught: boom");

        Path scriptSrc = tempDir.resolve("script/Main.kf");
        Files.createDirectories(scriptSrc.getParent());
        Files.writeString(scriptSrc, source);
        runScript(scriptSrc, "caught: boom");
    }

    /** O `finally` do try interno tem que rodar MESMO quando o throw do
     *  operando do `return` escapa pro catch de um try EXTERNO (antes do fix:
     *  o throw era capturado em silêncio pelo handler externo e o `fin`
     *  interno nunca rodava — em nenhum dos quatro alvos). */
    @Test
    void returnOperandThrowStillRunsInnerFinallyWhenCaughtByOuterTry(@TempDir Path tempDir) throws IOException {
        String source = """
                String boom() { throw "boom" }
                String f() {
                    try {
                        try {
                            return "v=" + boom()
                        } finally {
                            println("fin")
                        }
                    } catch (String m) {
                        return "outer caught: " + m
                    }
                }
                main() { println(f()) }
                """;
        String expected = "fin\nouter caught: boom";

        Path jvmSrc = tempDir.resolve("jvm/Main.kf");
        Files.createDirectories(jvmSrc.getParent());
        Files.writeString(jvmSrc, source);
        runJvm(jvmSrc, tempDir.resolve("jvm/out"), expected);

        Path natSrc = tempDir.resolve("nat/Main.kf");
        Files.createDirectories(natSrc.getParent());
        Files.writeString(natSrc, source);
        runNative(natSrc, tempDir.resolve("nat/out"), expected);

        Path jsSrc = tempDir.resolve("js/Main.kf");
        Files.createDirectories(jsSrc.getParent());
        Files.writeString(jsSrc, source);
        runJs(jsSrc, tempDir.resolve("js/out"), expected);

        Path scriptSrc = tempDir.resolve("script/Main.kf");
        Files.createDirectories(scriptSrc.getParent());
        Files.writeString(scriptSrc, source);
        runScript(scriptSrc, expected);
    }
}