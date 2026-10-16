package dev.kof.compiler;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * #848: {@code File.readBytes()} e {@code File.readRange(o, n)} são {@code Int[]}
 * NÃO anuláveis, mas devolviam {@code null} na falha — o primeiro uso dava NPE
 * (JVM: mensagem interna; Script: "null") ou SIGSEGV (Native), e o Native ainda
 * devolvia lixo para um diretório e abortava com offset negativo. Contrato: a
 * falha LANÇA uma String capturável por {@code catch (String e)}, como
 * {@code size()} — {@code file not found: <path>} quando o arquivo não existe,
 * {@code cannot read file: <path>} quando existe mas não pode ser lido (diretório,
 * erro de E/S) ou a faixa é inválida (offset/len negativos). Sucesso, arquivo
 * vazio, leitura curta e EOF ficam como estavam.
 */
class FileBinaryReadFailureE2ETest {

    private static final String BYTES = """
            main() {
                try {
                    var b = File("@/missing.bin").readBytes()
                    println("unexpected " + b.length)
                } catch (String e) {
                    println("missing: " + e)
                }
                try {
                    var b = File("@/sub").readBytes()
                    println("unexpected " + b.length)
                } catch (String e) {
                    println("dir: " + e)
                }
                println("empty=" + File("@/empty.bin").readBytes().length)
                var all = File("@/data.bin").readBytes()
                println("all=" + all.length + " " + all[0] + "," + all[1] + "," + all[2] + "," + all[3])
            }
            """;

    private static final String RANGE = """
            main() {
                var f = File("@/data.bin")
                try {
                    var r = File("@/missing.bin").readRange(0L, 4L)
                    println("unexpected " + r.length)
                } catch (String e) {
                    println("missing: " + e)
                }
                try {
                    var r = File("@/sub").readRange(0L, 4L)
                    println("unexpected " + r.length)
                } catch (String e) {
                    println("dir: " + e)
                }
                try {
                    var r = f.readRange(-1L, 2L)
                    println("unexpected " + r.length)
                } catch (String e) {
                    println("offNeg: " + e)
                }
                try {
                    var r = f.readRange(0L, -1L)
                    println("unexpected " + r.length)
                } catch (String e) {
                    println("lenNeg: " + e)
                }
                var part = f.readRange(1L, 2L)
                println("part=" + part.length + " " + part[0] + "," + part[1])
                println("short=" + f.readRange(2L, 10L).length)
                println("eof=" + f.readRange(10L, 4L).length)
                println("len0=" + f.readRange(0L, 0L).length)
                println("empty=" + File("@/empty.bin").readRange(0L, 4L).length)
                var gone = File("@/gone.bin")
                gone.writeBytes(f.readBytes())
                println("before=" + gone.readRange(0L, 2L).length)
                gone.delete()
                try {
                    var r = gone.readRange(2L, 2L)
                    println("unexpected " + r.length)
                } catch (String e) {
                    println("gone: " + e)
                }
            }
            """;

    private static final String UNCAUGHT = """
            main() {
                println("before")
                var b = File("@/missing.bin").readBytes()
                println("after " + b.length)
            }
            """;

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void readBytesFailureThrowsTheContractMessage(Target target, @TempDir Path dir) throws Exception {
        String p = fixtures(dir);
        Run r = run(target, dir, BYTES.replace("@", p));
        assertEquals(0, r.exit, target + ": " + r.out);
        assertEquals("missing: file not found: " + p + "/missing.bin\n"
                + "dir: cannot read file: " + p + "/sub\n"
                + "empty=0\n"
                + "all=4 0,127,128,255", r.out, target.toString());
    }

    /** JS não tem binding de readRange (IOJS001, DomainGapCodesTest) — só os alvos que o suportam. */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI", "JS"}, mode = EnumSource.Mode.EXCLUDE)
    void readRangeFailureThrowsAndSuccessFacesAreUnchanged(Target target, @TempDir Path dir) throws Exception {
        String p = fixtures(dir);
        Run r = run(target, dir, RANGE.replace("@", p));
        assertEquals(0, r.exit, target + ": " + r.out);
        assertEquals("missing: file not found: " + p + "/missing.bin\n"
                + "dir: cannot read file: " + p + "/sub\n"
                + "offNeg: cannot read file: " + p + "/data.bin\n"
                + "lenNeg: cannot read file: " + p + "/data.bin\n"
                + "part=2 127,128\n"
                + "short=2\n"
                + "eof=0\n"
                + "len0=0\n"
                + "empty=0\n"
                + "before=2\n"
                + "gone: file not found: " + p + "/gone.bin", r.out, target.toString());
    }

    /** Sem catch, a falha encerra o programa com erro e a mensagem — nunca segue com um array nulo. */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI", "SCRIPT"}, mode = EnumSource.Mode.EXCLUDE)
    void uncaughtReadFailureStopsWithTheMessage(Target target, @TempDir Path dir) throws Exception {
        String p = fixtures(dir);
        Run r = run(target, dir, UNCAUGHT.replace("@", p));
        assertNotEquals(0, r.exit, target + ": " + r.out);
        assertTrue(r.out.startsWith("before"), target + ": " + r.out);
        assertTrue(r.out.contains("file not found: " + p + "/missing.bin"), target + ": " + r.out);
        assertFalse(r.out.contains("after"), target + ": " + r.out);
    }

    private static String fixtures(Path dir) throws Exception {
        Files.write(dir.resolve("empty.bin"), new byte[0]);
        Files.write(dir.resolve("data.bin"), new byte[] {0, 127, (byte) 128, (byte) 255});
        Files.createDirectories(dir.resolve("sub"));
        return dir.toAbsolutePath().toString().replace('\\', '/');
    }

    private record Run(int exit, String out) {}

    private static Run run(Target target, Path dir, String source) throws Exception {
        if (target.isNative()) {
            assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"), "Linux ELF required");
            if (target != Target.NATIVE) {
                assumeTrue(NativeRiscv64E2ETest.hasToolchain(target.nativeArch()), "cross toolchain/QEMU required");
            }
        }
        Path file = dir.resolve("Main.kf");
        Files.writeString(file, source);
        CompilerDriver driver = new CompilerDriver();
        if (target == Target.SCRIPT) {
            KofInterpreter.Result result = driver.interpret(List.of(file), dir, new String[0]);
            return new Run(result.exitCode(), clean(result.stdout()));
        }
        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(file, out, target);
        assertTrue(result.success(), target + ": " + result.diagnostics().getDiagnostics());
        if (target == Target.JS) {
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), captured,
                    new ByteArrayInputStream(new byte[0]), captured);
            return new Run(exit, clean(captured.toString(StandardCharsets.UTF_8)));
        }
        ProcessBuilder command;
        if (target == Target.JVM) {
            // carga por reflexão: o launcher mascara falha de load como "JavaFX ausente" (§556)
            Path runner = dir.resolve("Run.java");
            Files.writeString(runner, """
                    class Run {
                        public static void main(String[] args) throws Throwable {
                            try {
                                Class.forName("Default.Main").getMethod("main", String[].class)
                                    .invoke(null, (Object) new String[0]);
                            } catch (java.lang.reflect.InvocationTargetException e) {
                                System.out.println(e.getCause().getMessage());
                                System.exit(1);
                            }
                        }
                    }
                    """);
            command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Xverify:all", "--class-path", out.toString(), runner.toString());
        } else if (target == Target.NATIVE) {
            command = new ProcessBuilder(out.resolve("Default/Main").toString());
        } else {
            command = NativeRiscv64E2ETest.qemu(target.nativeArch(), out.resolve("Default/Main"));
        }
        Path log = dir.resolve("process.log");
        Process process = command.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), target + " execution timed out");
            return new Run(process.exitValue(), clean(new String(Files.readAllBytes(log), StandardCharsets.UTF_8)));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    /** Normaliza CRLF e descarta a linha de diagnóstico do QEMU (ruído do emulador, não do programa). */
    private static String clean(String s) {
        return s.replace("\r\n", "\n").lines().filter(l -> !l.startsWith("qemu:"))
                .reduce((a, b) -> a + "\n" + b).orElse("").strip();
    }
}
