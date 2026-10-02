package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * spawn — concurrent tasks on the JVM backend (virtual threads) and
 * on the Native backend (pthread — CONC001 fechado 31/08).
 *
 * Semantics: spawn <call> or spawn { ... } runs the task concurrently;
 * the program waits for all spawned tasks before exiting (implicit join).
 */
class SpawnE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private String runJvm(Path source, Path outDir) throws IOException {
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
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private static final String SPAWN_SOURCE = """
            void tarefa(String nome, Int vezes) {
                for (var i = 0; i < vezes; i = i + 1) {
                    println(nome + ":" + i)
                }
            }
            main() {
                println("inicio")
                spawn tarefa("a", 3)
                spawn tarefa("b", 2)
                spawn {
                    println("bloco")
                }
                println("fim")
            }
            """;

    @Test
    void spawnRunsConcurrentlyAndJoins(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SPAWN_SOURCE);
        String output = runJvm(source, tempDir.resolve("out"));
        List<String> lines = output.lines().toList();
        assertTrue(lines.contains("inicio"), "main should print inicio first: " + lines);
        assertTrue(lines.contains("fim"), "main should not block on spawn: " + lines);
        for (String expected : List.of("a:0", "a:1", "a:2", "b:0", "b:1", "bloco")) {
            assertTrue(lines.contains(expected), "missing " + expected + " in: " + lines);
        }
    }

    @Test
    void spawnFunctionWithReturnValueIsDiscarded(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                Int calcula(Int x) {
                    return x * 2
                }
                main() {
                    spawn calcula(21)
                    println("feito")
                }
                """);
        String output = runJvm(source, tempDir.resolve("out"));
        assertTrue(output.contains("feito"), output);
    }

    @Test
    void spawnLambdaCapturesOuterLocal(@TempDir Path tempDir) throws IOException, InterruptedException {
        // spawn { println(x + 1) }: a lambda captura o local `x` — antes o
        // lowering de SpawnStmt usava List.of() (zero capturas) e o corpo
        // resolvia `x` para `this` → VerifyError/ClassFormatError no JVM e
        // valor errado no Native.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var x = 10
                    spawn {
                        println(x + 1)
                    }
                }
                """);
        // JVM: a captura chega pelo construtor da Lambda (field)
        assertEquals("11", runJvm(source, tempDir.resolve("out-jvm")));
        // Native: pthread + captura
        CompilationResult result = driver.compile(source, tempDir.resolve("out-native"), Target.NATIVE);
        assertTrue(result.success(), "Native: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out-native").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit, output: " + output);
        assertEquals("11", output, "captura no Native");
    }

    @Test
    void jsSpawnStmtRunsSequentially(@TempDir Path tempDir) throws Exception {
        // CONC003: JS é single-threaded — `spawn { }` degenera em execução
        // imediata/sequencial do corpo (fire-and-forget sem thread). A ordem
        // de output é preservada (bloco roda ANTES de "after").
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    spawn {
                        println("inside")
                    }
                    println("after")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JS);
        assertTrue(result.success(), "JS spawn stmt deve compilar (sem CONC003): " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(tempDir.resolve("out")), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "exit, output: " + output);
            assertTrue(output.contains("inside"), "corpo rodou: " + output);
            assertTrue(output.contains("after"), "main continuou: " + output);
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

    @Test
    void nativeSpawnStmtRuns(@TempDir Path tempDir) throws IOException, InterruptedException {
        // CONC001 fechado: spawn stmt com pthread + join implícito no fim do main
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    spawn {
                        println("inside")
                    }
                    println("after")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native spawn should compile: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertTrue(output.contains("inside"), "task rodou: " + output);
        assertTrue(output.contains("after"), "main continuou: " + output);
    }

    @Test
    void nativePrintBeforeSpawnDoesNotSegfault(@TempDir Path tempDir) throws IOException, InterruptedException {
        // Regressão: um println/print ANTES do spawn desalinhava a stack
        // (convenção args-by-stack via push empilha um slot a mais) e o
        // pthread_create do kof_spawn segfaultava em pthread_attr_copy.
        // Esperado: os 3 prints saem em qualquer ordem, sem SIGSEGV.
        assumeTrue(isLinux(), "Native target runs on Linux");
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                void tarefa() {
                    println("t")
                }
                main() {
                    println("antes")
                    spawn tarefa()
                    spawn {
                        println("bloco")
                    }
                    println("depois")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "exit code (SIGSEGV=139), output: " + output);
        for (String e : List.of("antes", "depois", "t", "bloco")) {
            assertTrue(output.contains(e), "falta " + e + " em: " + output);
        }
    }

    @Test
    void nativeSpawnAwaitSpawnDoesNotSegfault(@TempDir Path tempDir) throws IOException, InterruptedException {
        // Bug pré-existente SEPARADO (docs/status #2): `spawn → await → spawn`
        // corrompia a pilha/frame da main thread — SIGSEGV no 2º
        // pthread_create (mesma raiz do println-antes-do-spawn: stack chegou
        // desalinhada ao call do pthread_create após o pthread_join do await).
        // Reprodutor mínimo: spawn t1; await; spawn t2. Esperado: sem SIGSEGV.
        assumeTrue(isLinux(), "Native target runs on Linux");
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                Int t1() { println("t1"); return 1 }
                Int t2() { println("t2"); return 2 }
                main() {
                    var r = spawn t1()
                    var v = await r
                    println("res=" + v)
                    spawn t2()
                    println("done")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "exit code (SIGSEGV=139), output: " + output);
        for (String e : List.of("t1", "res=1", "done", "t2")) {
            assertTrue(output.contains(e), "falta " + e + " em: " + output);
        }
    }

    @Test
    void nativeSpawnExprAwait(@TempDir Path tempDir) throws IOException, InterruptedException {
        // CONC001: spawn-expr com Handle tipado + await (join no handle)
        Path source = tempDir.resolve("Main2.kf");
        Files.writeString(source, """
                Int work(Int x) { return x * 2 }
                main() {
                    val r = spawn work(21)
                    println(await r)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out2"), Target.NATIVE);
        assertTrue(result.success(), "Native spawn-expr should compile: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out2").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertTrue(output.contains("42"), "await devolve o valor: " + output);
    }

    // known-bugs #46 — spawn-expr com LAMBDA LITERAL que retorna valor
    // (`spawn { return n * 2 }`) → SIGSEGV no Native (variante do bug 29).
    @Test
    void nativeSpawnExprAwaitLambdaReturn(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path source = tempDir.resolve("Main3.kf");
        Files.writeString(source, """
                main() {
                    var n = 21
                    var h = spawn { return n * 2 }
                    println(await h)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out3"), Target.NATIVE);
        assertTrue(result.success(), "Native spawn-expr lambda return should compile: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out3").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertTrue(output.contains("42"), "await devolve o valor da lambda: " + output);
    }

    // bug 46 (isolamento): spawn { return 42 } SEM captura — determina se o
    // SIGSEGV é da CAPTURA ou do return em si. Se este passa e o com captura
    // falha → a captura é a causa; se ambos falham → o return lambda é a causa.
    @Test
    void nativeSpawnExprAwaitLambdaReturnNoCapture(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path source = tempDir.resolve("Main4.kf");
        Files.writeString(source, """
                main() {
                    var h = spawn { return 42 }
                    println(await h)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out4"), Target.NATIVE);
        assertTrue(result.success(), "Native spawn-expr lambda return (no capture) should compile: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out4").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertTrue(output.contains("42"), "await devolve o valor da lambda: " + output);
    }

    // ---- fase 4.2 (#659, D-MEMORY-SAFETY): a face do §46 com CAPTURA so era
    // travada no Native; a alegacao "interpreter/JVM/JS -> 42" vivia so na
    // prose. Mesma fonte, mesma saida, nos outros tres alvos — paridade
    // provada por bateria, nao por memoria de prosa.

    private static final String SPAWN_RETURN_CAPTURE = """
            main() {
                var n = 21
                var h = spawn { return n * 2 }
                println(await h)
            }
            """;

    @Test
    void jvmSpawnExprAwaitLambdaReturn(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main-jvm42.kf");
        Files.writeString(source, SPAWN_RETURN_CAPTURE);
        assertEquals("42", runJvm(source, tempDir.resolve("out-jvm42")));
    }

    @Test
    void scriptSpawnExprAwaitLambdaReturn(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main-script42.kf");
        Files.writeString(source, SPAWN_RETURN_CAPTURE);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(source),
                tempDir.resolve("out-script42"), new String[0]);
        String output = r.stdout().replace("\r\n", "\n").trim();
        assertEquals(0, r.exitCode(), "SCRIPT exit, output: '" + output + "'");
        assertEquals("42", output, "await no interpretador devolve a captura");
    }

    @Test
    void jsSpawnExprAwaitLambdaReturn(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main-js42.kf");
        Files.writeString(source, SPAWN_RETURN_CAPTURE);
        CompilationResult result = driver.compile(source, tempDir.resolve("out-js42"), Target.JS);
        assertTrue(result.success(), "JS spawn-expr lambda return: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(tempDir.resolve("out-js42")), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit, output: " + output);
            assertEquals("42", output, "await no JS devolve a captura");
        }
    }

    // Face MUTADA da captura em spawn (verifier independente #659): as faces
    // acima leem a captura so — e no JVM leitura sem mutacao NAO passa por
    // CapturedVarBox (LambdaTask0.<init>(I), medido). Esta face sim: o corpo
    // do spawn MUTA o local capturado antes do return. Escopo honesto do
    // diagnostico: o scanner MEM021 lista MUTATORS de OBJETO (add/remove/
    // clear/addAll); reatribuicao ESCALAR fica silenciosa em qualquer lado —
    // corrida escalar mutacao-durante-async e ambiguidade de contrato
    // (issue aberta; regra 6), nao escolha deste teste.
    private static final String SPAWN_MUTATED_CAPTURE = """
            main() {
                var n = 21
                var h = spawn { n = n + 1; return n * 2 }
                println(await h)
            }
            """;

    @Test
    void jvmSpawnExprAwaitMutatedCapture(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main-jvm44.kf");
        Files.writeString(source, SPAWN_MUTATED_CAPTURE);
        assertEquals("44", runJvm(source, tempDir.resolve("out-jvm44")));
    }

    @Test
    void scriptSpawnExprAwaitMutatedCapture(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main-script44.kf");
        Files.writeString(source, SPAWN_MUTATED_CAPTURE);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(source),
                tempDir.resolve("out-script44"), new String[0]);
        String output = r.stdout().replace("\r\n", "\n").trim();
        assertEquals(0, r.exitCode(), "SCRIPT exit, output: '" + output + "'");
        assertEquals("44", output, "mutacao da captura no corpo do spawn (SCRIPT)");
    }

    @Test
    void jsSpawnExprAwaitMutatedCapture(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main-js44.kf");
        Files.writeString(source, SPAWN_MUTATED_CAPTURE);
        CompilationResult result = driver.compile(source, tempDir.resolve("out-js44"), Target.JS);
        assertTrue(result.success(), "JS spawn mutated-capture: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(tempDir.resolve("out-js44")), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit, output: " + output);
            assertEquals("44", output, "mutacao da captura no corpo do spawn (JS)");
        }
    }

    @Test
    void nativeSpawnExprAwaitMutatedCapture(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path source = tempDir.resolve("Main-native44.kf");
        Files.writeString(source, SPAWN_MUTATED_CAPTURE);
        CompilationResult result = driver.compile(source, tempDir.resolve("out-native44"), Target.NATIVE);
        assertTrue(result.success(), "Native spawn mutated-capture: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out-native44").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit, output: " + output);
        assertEquals("44", output, "mutacao da captura no corpo do spawn (Native)");
    }

    // Visibilidade FILHO->PAI da caixa (verifier independente #659, 2a
    // passada): as faces 44 acima so observam o ponto de vista do FILHO —
    // um lowering por SNAPSHOT tambem daria 44. Aqui o PAI le `n` depois do
    // `await`: 44\n22 so passa se a mutacao do filho for visivel ao pai via
    // caixa + join. Sem este pino, regressao para captura-por-valor no spawn
    // passaria 17/17+36/36 verdes.

    private static final String SPAWN_MUTATED_CAPTURE_SHARE = """
            main() {
                var n = 21
                var h = spawn { n = n + 1; return n * 2 }
                println(await h)
                println(n)
            }
            """;

    private static final String SHARE_GOLDEN = "44\n22";

    @Test
    void jvmSpawnMutatedCaptureVisibleToParent(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main-share-jvm.kf");
        Files.writeString(source, SPAWN_MUTATED_CAPTURE_SHARE);
        assertEquals(SHARE_GOLDEN, runJvm(source, tempDir.resolve("out-share-jvm")));
    }

    @Test
    void scriptSpawnMutatedCaptureVisibleToParent(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main-share-script.kf");
        Files.writeString(source, SPAWN_MUTATED_CAPTURE_SHARE);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(source),
                tempDir.resolve("out-share-script"), new String[0]);
        String output = r.stdout().replace("\r\n", "\n").trim();
        assertEquals(0, r.exitCode(), "SCRIPT exit, output: '" + output + "'");
        assertEquals(SHARE_GOLDEN, output, "visibilidade filho->pai no interpretador");
    }

    @Test
    void jsSpawnMutatedCaptureVisibleToParent(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main-share-js.kf");
        Files.writeString(source, SPAWN_MUTATED_CAPTURE_SHARE);
        CompilationResult result = driver.compile(source, tempDir.resolve("out-share-js"), Target.JS);
        assertTrue(result.success(), "JS share-face: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(tempDir.resolve("out-share-js")), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit, output: " + output);
            assertEquals(SHARE_GOLDEN, output, "visibilidade filho->pai no JS");
        }
    }

    @Test
    void nativeSpawnMutatedCaptureVisibleToParent(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path source = tempDir.resolve("Main-share-native.kf");
        Files.writeString(source, SPAWN_MUTATED_CAPTURE_SHARE);
        CompilationResult result = driver.compile(source, tempDir.resolve("out-share-native"), Target.NATIVE);
        assertTrue(result.success(), "Native share-face: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out-share-native").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit, output: " + output);
        assertEquals(SHARE_GOLDEN, output, "visibilidade filho->pai no Native");
    }
}