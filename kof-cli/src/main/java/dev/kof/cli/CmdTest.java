package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * kof test — roda cada .kf como programa independente (per-file, PKG002):
 * compila no modo harness (test "nome" { } vira runner sintetizado) e
 * reporta PASS/FAIL por arquivo. Extraído de Main (REFACTOR-500 Fase 8).
 */
final class CmdTest {

    private CmdTest() {
    }

    private static final String USAGE =
            "usage: kof test <file.kf|dir> [--target jvm|native|js] [--timeout <sec>]"
            + " [--tag <tag>]";

    static void run(String[] args) {
        if (args.length < 2) { System.err.println(USAGE); System.exit(1); return; }
        if (args[1].equals("--help") || args[1].equals("-h")) {
            System.out.println(USAGE);
            return;
        }
        Path src = Path.of(args[1]);
        Target target = Target.JVM;
        long timeoutSec = 0;   // 0 = sem limite (comportamento histórico, aditivo)
        String tag = null;     // X8 fatia 3: filtro por tag (compile-time, único p/ 4 alvos)
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("--target=")) {
                target = KofCliSupport.parseTarget(args[i].substring("--target=".length()));
            } else if (args[i].equals("--target") && i + 1 < args.length) {
                target = KofCliSupport.parseTarget(args[i + 1]);
                i++;
            } else if (args[i].startsWith("--timeout=")) {
                Long t = parseTimeout(args[i].substring("--timeout=".length()));
                if (t == null) { badTimeout(); return; }
                timeoutSec = t;
            } else if (args[i].equals("--timeout") && i + 1 < args.length) {
                Long t = parseTimeout(args[i + 1]);
                if (t == null) { badTimeout(); return; }
                timeoutSec = t;
                i++;
            } else if (args[i].startsWith("--tag=")) {
                tag = args[i].substring("--tag=".length());
                if (tag.isEmpty()) { badTag(); return; }
            } else if (args[i].equals("--tag") && i + 1 < args.length) {
                tag = args[i + 1];
                if (tag.isEmpty()) { badTag(); return; }
                i++;
            } else if (args[i].equals("--help") || args[i].equals("-h")) {
                System.err.println(USAGE);
                return;
            } else if (args[i].startsWith("-")) {
                // R6: an unknown flag (or --target/--timeout without its value) must
                // never be silently ignored — the user/CI would believe it took effect.
                System.err.println("test: unknown or incomplete flag: " + args[i]
                        + " (accepts: --target jvm|native|js, --timeout <sec>, --tag <tag>)");
                System.exit(1);
                return;
            } else {
                System.err.println("test: unexpected argument: " + args[i]
                        + " (accepts: --target jvm|native|js, --timeout <sec>, --tag <tag>)");
                System.exit(1);
                return;
            }
        }
        if (!Files.exists(src)) { System.err.println("not found: " + src); System.exit(1); return; }
        // android é empacotamento (APK/AAB), não um alvo de execução: `kof test`
        // não produz binário standalone. Recusa honesta e cedo (R6) em vez do
        // enganoso "no binary produced" depois de compilar o projeto inteiro.
        if (target == Target.ANDROID) {
            System.err.println("test: --target android is not a test target"
                    + " (android is packaging). Test the logic with"
                    + " --target jvm|native|js; use 'kof build --target android'"
                    + " to build the APK");
            System.exit(1);
            return;
        }
        boolean dirMode = Files.isDirectory(src);
        List<Path> files = dirMode ? collectTests(src) : List.of(src);
        if (files.isEmpty()) { System.out.println("no .kf/.kof files found"); return; }
        CompilerDriver driver = new CompilerDriver();
        int passed = 0;
        int failed = 0;
        // X8 fatia 3 ("named suites by directory"): em modo diretório cada
        // subdiretório é uma suíte nomeada (nome = caminho relativo; "." = raiz);
        // os contadores por suíte são somados ao total no fim.
        java.util.Map<String, int[]> suites = new java.util.LinkedHashMap<>();
        // per-file (docs/bugs-and-gaps/ecosystem-coverage.md §3.11): cada .kf é um programa
        // independente com seu próprio main() — NUNCA agrupar irmãos num
        // módulo só (PKG002: 2 main()). Cross-file é domínio de kof build.
        if (tag != null) System.setProperty("kof.test.tag", tag);
        for (Path f : files) {
            Path tmp;
            try { tmp = Files.createTempDirectory("kof-test-"); }
            catch (IOException e) { System.err.println("failed to create temp dir: " + e.getMessage()); System.exit(1); return; }
            // modo harness: `test "nome" { }` vira função + runner sintetizado;
            // arquivos sem testes compilam idênticos ao modo normal
            CompilationResult result = driver.compileForTests(f, tmp, target);
            boolean ok = result.success();
            StringBuilder output = new StringBuilder();
            if (ok) {
                for (Diagnostic d : result.diagnostics().getDiagnostics()) output.append(d.format()).append('\n');
                if (!driver.discoveredTests().isEmpty()) {
                    System.out.println("SUITE " + f + " (" + driver.discoveredTests().size() + " tests)");
                }
                if (target == Target.JVM) {
                    String className = KofCliSupport.findMainClass(tmp);
                    if (className == null) {
                        ok = false;
                        output.append("no main class found\n");
                    } else {
                        try {
                            ProcessBuilder pb = new ProcessBuilder(KofCliSupport.javaExecutable(), "-cp", tmp.toString(), className);
                            pb.redirectErrorStream(true);
                            Integer ec = boundedRun(pb, timeoutSec, output);
                            if (ec == null) {
                                ok = false;
                                output.append("timeout after ").append(timeoutSec).append("s — process killed (--timeout)\n");
                            } else {
                                ok = ec == 0;
                                if (!ok) output.append("exit code: ").append(ec).append('\n');
                            }
                        } catch (IOException | InterruptedException e) {
                            ok = false;
                            output.append("failed to execute: ").append(e.getMessage()).append('\n');
                        }
                    }
                } else if (target == Target.JS) {
                    String entry = KofCliSupport.findJsEntry(tmp);
                    if (entry == null) {
                        ok = false;
                        output.append("no JS entry point found\n");
                    } else {
                        try {
                            // JS roda in-process (KofJsRunner): sem subprocesso para
                            // matar, o --timeout é best-effort — o join sai, o FAIL é
                            // reportado, a thread é daemon (o CLI termina sem ela).
                            final int[] code = {1};
                            final boolean[] over = {false};
                            Thread js = new Thread(() -> {
                                try {
                                    code[0] = dev.kof.runtime.KofJsRunner.run(java.nio.file.Path.of(entry),
                                            System.out, System.in, System.err, false, new String[0]);
                                } catch (IOException e) {
                                    code[0] = 1;
                                }
                            });
                            js.setDaemon(true);
                            js.start();
                            if (timeoutSec > 0) {
                                js.join(timeoutSec * 1000L);
                                if (js.isAlive()) {
                                    js.interrupt();
                                    over[0] = true;
                                }
                            } else {
                                js.join();
                            }
                            if (over[0]) {
                                ok = false;
                                output.append("timeout after ").append(timeoutSec)
                                        .append("s — JS harness roda in-process (best-effort kill;")
                                        .append(" prefira --target jvm em CI)\n");
                            } else {
                                ok = code[0] == 0;
                                if (!ok) output.append("exit code: ").append(code[0]).append('\n');
                            }
                        } catch (InterruptedException e) {
                            ok = false;
                            Thread.currentThread().interrupt();
                            output.append("failed to execute: ").append(e.getMessage()).append('\n');
                        }
                    }
                } else {
                    Path bin = tmp.resolve("Default/Main");
                    if (!Files.exists(bin)) {
                        ok = false;
                        output.append("no binary produced\n");
                    } else {
                        try {
                            ProcessBuilder pb = new ProcessBuilder(bin.toString());
                            pb.redirectErrorStream(true);
                            Integer ec = boundedRun(pb, timeoutSec, output);
                            if (ec == null) {
                                ok = false;
                                output.append("timeout after ").append(timeoutSec).append("s — process killed (--timeout)\n");
                            } else {
                                ok = ec == 0;
                                if (!ok) output.append("exit code: ").append(ec).append('\n');
                            }
                        } catch (IOException | InterruptedException e) {
                            ok = false;
                            output.append("failed to execute: ").append(e.getMessage()).append('\n');
                        }
                    }
                }
            } else {
                for (Diagnostic d : result.diagnostics().getDiagnostics()) output.append(d.format()).append('\n');
            }
            KofCliSupport.cleanup(tmp);
            if (dirMode) {
                int[] c = suites.computeIfAbsent(suiteOf(src, f), k -> new int[2]);
                if (ok) c[0]++; else c[1]++;
            }
            if (ok) {
                passed++;
                if (driver.discoveredTests().isEmpty()) System.out.println("PASS " + f);
                else System.out.print(output);
            } else {
                failed++;
                System.out.println("FAIL " + f);
                System.out.print(output);
            }
        }
        if (tag != null) System.clearProperty("kof.test.tag");
        if (dirMode) {
            for (java.util.Map.Entry<String, int[]> e : suites.entrySet()) {
                System.out.println("suite " + e.getKey() + ": " + e.getValue()[0]
                        + " passed, " + e.getValue()[1] + " failed");
            }
        }
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    /**
     * X8 fatia 3 ("named suites by directory"): recursão — `kof test <dir>`
     * descobre os .kf/.kof também em subdiretórios (cada diretório = uma suíte
     * nomeada). Aditivo: a descoberta de build/run segue não-recursiva
     * (`KofCliSupport.collect`), porque lá um diretório é um pacote (PKG002).
     */
    private static void badTag() {
        System.err.println("test: --tag requires a non-empty value");
        System.exit(1);
    }

    private static List<Path> collectTests(Path dir) {
        List<Path> files = new java.util.ArrayList<>();
        try (var s = Files.walk(dir)) {
            s.filter(KofCliSupport::isKofSource).forEach(files::add);
        } catch (IOException e) { System.err.println("error: " + e.getMessage()); }
        files.sort(java.util.Comparator.comparing(Path::toString));
        return files;
    }

    /** Nome da suíte de um arquivo: diretório-pai relativo à raiz ("." = raiz). */
    private static String suiteOf(Path root, Path file) {
        Path rel = root.relativize(file).getParent();
        return rel == null ? "." : rel.toString();
    }

    private static Long parseTimeout(String v) {
        try {
            long n = Long.parseLong(v.trim());
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void badTimeout() {
        System.err.println("test: --timeout expects a positive integer of seconds");
        System.exit(1);
    }

    /**
     * Roda o processo do do harness capturando stdout+stderr SEM bloquear a
     * leitura (gotejamento em thread daemon): com `timeoutSec > 0` mata o processo
     * ao estourar (devolve null = timeout); com 0 preserva o comportamento
     * histórico (espera infinita). (X8-A / G6 "timeouts".)
     */
    private static Integer boundedRun(ProcessBuilder pb, long timeoutSec, StringBuilder output)
            throws IOException, InterruptedException {
        Process p = pb.start();
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        Thread pump = new Thread(() -> {
            try { p.getInputStream().transferTo(buf); } catch (IOException ignored) { }
        });
        pump.setDaemon(true);
        pump.start();
        boolean finished;
        if (timeoutSec > 0) {
            finished = p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        } else {
            p.waitFor();
            finished = true;
        }
        pump.join(5000);
        output.append(new String(buf.toByteArray(), StandardCharsets.UTF_8));
        if (!finished) return null;
        return p.exitValue();
    }
}
