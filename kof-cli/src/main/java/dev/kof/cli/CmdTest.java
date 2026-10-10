package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.KofProjectConfig;
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
            + " [--tag <tag>[,<tag>...]]";

    static void run(String[] args) {
        if (args.length >= 2 && (args[1].equals("--help") || args[1].equals("-h"))) {
            System.out.println(USAGE);
            return;
        }
        // #708: raiz de testes declarada em [sources] test permite `kof test`
        // sem argumento posicional; a raiz de app ([sources] app, ou o pai do
        // diretório do teste) entra como source path para resolver imports.
        boolean flagMode = args.length < 2 || args[1].startsWith("-");
        Path src = null;
        Path appRoot = null;
        int argStart = 2;
        if (flagMode) {
            Path projectRoot = KofCliSupport.projectRootOf(Path.of("."));
            KofProjectConfig cfg = KofCliSupport.configOf(projectRoot);
            // src pode ficar ausente (sem [sources] test): o erro é decidido
            // DEPOIS de parsear as flags, para que um typo de flag seja
            // reportado como flag desconhecida (R6), não como "sem raiz".
            src = KofProjectConfig.resolveSourceRoot(projectRoot, cfg.sourceTest(), null);
            appRoot = KofProjectConfig.resolveSourceRoot(projectRoot, cfg.sourceApp(), null);
            argStart = 1;
        } else {
            src = Path.of(args[1]);
            // Sem [sources]: se o teste vive sob um kof.toml, a raiz do app
            // (se declarada) ainda é oferecida como source path — aditivo, o
            // modo posicional de hoje não tinha imports cross-root.
            Path projectRoot = KofCliSupport.projectRootOf(src);
            if (projectRoot != null) {
                KofProjectConfig cfg = KofCliSupport.configOf(projectRoot);
                appRoot = KofProjectConfig.resolveSourceRoot(projectRoot, cfg.sourceApp(), null);
            }
        }
        Target target = Target.JVM;
        long timeoutSec = 0;   // 0 = sem limite (comportamento histórico, aditivo)
        String tag = null;     // X8 fatia 3: filtro por tag (compile-time, único p/ 4 alvos);
                               // §7.1: aceita lista separada por vírgula (OR) — "smoke,ui"
        for (int i = argStart; i < args.length; i++) {
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
                        + " (accepts: --target jvm|native|js, --timeout <sec>, --tag <tag>[,<tag>...])");
                System.exit(1);
                return;
            } else {
                System.err.println("test: unexpected argument: " + args[i]
                        + " (accepts: --target jvm|native|js, --timeout <sec>, --tag <tag>[,<tag>...])");
                System.exit(1);
                return;
            }
        }
        if (src == null) {
            System.err.println("test: no test root given and no [sources] test"
                    + " declared in kof.toml (see 'kof test --help')");
            System.exit(1);
            return;
        }
        if (!Files.exists(src)) { System.err.println("not found: " + src); System.exit(1); return; }
        String extErr = KofCliSupport.unsupportedSourceExtension("test", src);
        if (extErr != null) { System.err.println(extErr); System.exit(1); return; }
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
        // Cross native targets são verificados pela suíte E2E do compilador sob
        // qemu, não por `kof test`: o runner executa o binário do HOST direto e o
        // harness cross não linka `kof_process_exit` (o binário nem chega a
        // produzir — morria com um `riscv64-ld: undefined reference` mislabeled
        // COMP001, "suporte" falso). Recusa honesta e cedo (R6/Q7), no mesmo
        // padrão do android.
        if (target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64) {
            System.err.println("test: --target native." + target.nativeArch()
                    + " is not a test target (the runner executes the host"
                    + " binary; the cross harness is verified under qemu by the"
                    + " compiler E2E suite). Test the logic with"
                    + " --target jvm|native|js, or build with"
                    + " 'kof build --target native." + target.nativeArch() + "' and run under qemu");
            System.exit(1);
            return;
        }
        boolean dirMode = Files.isDirectory(src);
        List<Path> files = dirMode ? collectTests(src) : List.of(src);
        if (files.isEmpty()) {
            // R6 (#708): a test root with no Kof source used to print this and
            // exit 0 — indistinguishable from "all tests passed". Fail
            // explicitly (zero discovered tests is not a success).
            System.err.println("test: no .kf/.kof files found in " + src
                    + " (discovery is recursive; run from the directory that"
                    + " holds the test sources)");
            System.exit(1);
            return;
        }
        // §6 provider manifest (D-MAINT-BATCH-0610B/C + the third chat poll
        // 10/10): `kof-test.kofmd` at the project root declares the browser
        // provider + version; the CLI reads it and gates. A browser-tagged run
        // needs BOTH: a declared provider whose binary probes (run), or the
        // honest skip with the named reason (Q7, never a false green). A run
        // with no browser tests is not gated (the manifest is advisory there).
        // the manifest loader walks up on its own (the locate only looks for
        // kof.toml); start from the test source (or its dir), not projectRootOf.
        KofTestManifest testManifest = KofTestManifest.load(dirMode ? src : src.getParent());
        CompilerDriver driver = new CompilerDriver();
        // #708: a raiz de app entra como source path — `import exemplo.Calculo`
        // resolve de src/main/kof sem cópia nem arquivo de entrada gerado.
        if (appRoot != null && Files.isDirectory(appRoot)) {
            driver.setDependencySourceRoots(java.util.List.of(appRoot));
        }
        int passed = 0;
        int failed = 0;
        int skipped = 0;   // §576: arquivos sem teste e sem main (módulos auxiliares)
        int skippedByTag = 0;   // §587: arquivos cujo filtro --tag não casa nenhum teste
        int skippedByProvider = 0;   // §6: arquivos browser tagados sem provider/binário
        java.util.Map<String, String> fileLevel = new java.util.LinkedHashMap<>();
        java.util.Map<String, Boolean> fileResult = new java.util.LinkedHashMap<>();
        boolean browserTagged = tag != null && java.util.Arrays.stream(tag.split(","))
                .map(String::trim).anyMatch("browser"::equals);
        if (browserTagged) {
            if (!testManifest.declaresProvider()) {
                System.err.println("test: browser tests require a provider declared in"
                        + " kof-test.kofmd (add 'provider: playwright' + 'version: <x>')"
                        + " — 0 of " + files.size() + " files ran");
                System.exit(1);
                return;
            }
            String probe = providerProbe(testManifest.provider());
            if (probe == null) {
                System.err.println("test: provider '" + testManifest.provider()
                        + "' declared but its binary is not probeable (install it, e.g."
                        + " 'npx playwright install') — 0 of " + files.size() + " files ran");
                System.exit(1);
                return;
            }
            System.out.println("browser provider: " + testManifest.provider()
                    + (testManifest.version() != null ? " " + testManifest.version() : "")
                    + " (" + probe + ")");
        }
        // X8 fatia 3 ("named suites by directory"): em modo diretório cada
        // subdiretório é uma suíte nomeada (nome = caminho relativo; "." = raiz);
        // os contadores por suíte são somados ao total no fim.
        java.util.Map<String, int[]> suites = new java.util.LinkedHashMap<>();
        // §7.3/§8.5 (kof-testing-platform): tempo por arquivo — a base de "slow-test
        // identification". Só medição: o total e o arquivo mais lento são impressos
        // no fim; nenhuma flag nova, nenhum alvo/filtro tocado.
        long totalMs = 0;
        long slowestMs = -1;
        Path slowestFile = null;
        // §8.5 (kof-testing-platform): o nível de cada arquivo suíte — a
        // derivação honesta pelo tag declarado: qualquer teste do arquivo
        // declarando `unit` → unit; senão `integration` → integration; senão
        // `browser`/`e2e` → e2e; arquivos só-programa (main, sem teste) contam
        // como unit (o nível mais barato). Nenhum nível é inventado além do
        // que o arquivo DECLARA (o relatório agrega o declarado, nunca um
        // chute).
        // per-file (docs/bugs-and-gaps/ecosystem-coverage.md §3.11): cada .kf é um programa
        // independente com seu próprio main() — NUNCA agrupar irmãos num
        // módulo só (PKG002: 2 main()). Cross-file é domínio de kof build.
        if (tag != null) System.setProperty("kof.test.tag", tag);
        // #708: a raiz de testes é a base dos pacotes por diretório. Em modo
        // diretório é o próprio `src` (fonte em exemplo/CalcTest.kf declara
        // `package exemplo`); em modo arquivo, o diretório do arquivo.
        Path testsRoot = dirMode ? src : src.getParent();
        for (Path f : files) {
            long startNanos = System.nanoTime();
            Path tmp;
            try { tmp = Files.createTempDirectory("kof-test-"); }
            catch (IOException e) { System.err.println("failed to create temp dir: " + e.getMessage()); System.exit(1); return; }
            // modo harness: `test "nome" { }` vira função + runner sintetizado;
            // arquivos sem testes compilam idênticos ao modo normal
            CompilationResult result = driver.compileForTests(f, tmp, target, testsRoot);
            boolean ok = result.success();
            // §576: um arquivo que não declara `test` nem `main` não é uma
            // suíte nem um programa — é um módulo auxiliar (funções puras que
            // outro arquivo de teste importa). Compilar e tentar executá-lo
            // produzia "could not resolve main([String])" (ou, no alvo JS, um
            // "PASS" fantasma). Pular com nota honesta; se NENHUM arquivo for
            // executável, o runner falha — zero não é sucesso (#708).
            boolean hasTests = !driver.discoveredTests().isEmpty();
            boolean hasMain = driver.hasMainEntryPoint();
            if (ok && !hasTests && !hasMain) {
                System.out.println("SKIP " + f + " (no tests, no main)");
                skipped++;
                KofCliSupport.cleanup(tmp);
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                totalMs += elapsedMs;
                if (elapsedMs > slowestMs) { slowestMs = elapsedMs; slowestFile = f; }
                continue;
            }
            // §587: um arquivo cujos testes NENHUM casa o filtro --tag não é uma
            // suíte verde — o harness gera um main vazio e sai 0, então contar
            // `ok` como passed imprimia `2 passed, 0 failed` para uma corrida em
            // que 1 dos 2 arquivos rodou zero testes (falso verde). O arquivo é
            // SKIP nomeado (o exit continua 0 — nada falhou, como o no-op de tag
            // já contrata); a checagem vem DEPOIS do run para preservar a saída
            // do harness (`kof test: tag 'x' (0 of N)` / `no tests with tag ...`).
            boolean tagMatch = tag == null || !hasTests || hasTagMatch(driver.discoveredTests(), tag);
            // §8.5: o nível derivado dos tags declarados (unit < integration < e2e)
            String level = "unit";
            if (hasTests) {
                java.util.Set<String> tags = new java.util.HashSet<>();
                for (CompilerDriver.TestInfo ti : driver.discoveredTests()) tags.addAll(ti.tags());
                if (tags.contains("browser") || tags.contains("e2e")) level = "e2e";
                else if (tags.contains("integration")) level = "integration";
            }
            fileLevel.put(f.toString(), level);
            StringBuilder output = new StringBuilder();
            if (ok) {
                for (Diagnostic d : result.diagnostics().getDiagnostics()) output.append(d.format()).append('\n');
                if (!driver.discoveredTests().isEmpty() && tagMatch) {
                    System.out.println("SUITE " + f + " (" + driver.discoveredTests().size() + " tests)");
                }
                if (target == Target.JVM) {
                    String className = KofCliSupport.findMainClass(tmp);
                    if (className == null) {
                        ok = false;
                        output.append("no main class found\n");
                    } else {
                        try {
                            List<String> cmd = new java.util.ArrayList<>();
                            cmd.add(KofCliSupport.javaExecutable());
                            cmd.addAll(KofStdio.capturedJvmFlags());
                            // §556: sem o wrapper de diagnóstico, uma falha de
                            // load/link do runner vira a mensagem falsa do launcher.
                            KofCliSupport.appendJvmLaunch(cmd, tmp.toString(), className);
                            ProcessBuilder pb = new ProcessBuilder(cmd);
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
                                            KofStdio.fromUtf8(System.out), System.in,
                                            KofStdio.fromUtf8(System.err), false, new String[0]);
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
            if (ok && !tagMatch) {
                // §587: o arquivo compilou e rodou, mas ZERO testes casaram o
                // filtro. Não é uma suíte verde — SKIP nomeado, fora de passed
                // e de failed (não entra no tally de suíte verde).
                System.out.print(output);
                System.out.println("SKIP " + f + " (no tests with tag '" + tag + "')");
                skippedByTag++;
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                totalMs += elapsedMs;
                if (elapsedMs > slowestMs) { slowestMs = elapsedMs; slowestFile = f; }
                continue;
            }
            if (dirMode) {
                int[] c = suites.computeIfAbsent(suiteOf(src, f), k -> new int[2]);
                if (ok) c[0]++; else c[1]++;
            }
            fileResult.put(f.toString(), ok);
            if (ok) {
                passed++;
                if (driver.discoveredTests().isEmpty()) {
                    // §578: arquivo só-programa (main, sem teste) — o stdout do
                    // processo é a SAÍDA do programa, não um diagnóstico do
                    // runner. JVM/Native o capturavam e o descartavam (o JS já
                    // o mostrava); imprimir antes do PASS dá paridade de alvos.
                    if (output.length() > 0) System.out.print(output);
                    System.out.println("PASS " + f);
                } else {
                    System.out.print(output);
                }
            } else {
                failed++;
                fileResult.put(f.toString(), ok);
                System.out.println("FAIL " + f);
                System.out.print(output);
            }
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            totalMs += elapsedMs;
            if (elapsedMs > slowestMs) { slowestMs = elapsedMs; slowestFile = f; }
        }
        if (tag != null) System.clearProperty("kof.test.tag");
        if (dirMode) {
            for (java.util.Map.Entry<String, int[]> e : suites.entrySet()) {
                System.out.println("suite " + e.getKey() + ": " + e.getValue()[0]
                        + " passed, " + e.getValue()[1] + " failed");
            }
        }
        int totalSkipped = skipped + skippedByTag;
        String summary = passed + " passed, " + failed + " failed";
        if (totalSkipped > 0) {
            summary += ", " + totalSkipped + " skipped";
            // Preserva o texto histórico exato quando só há skip de auxiliar
            // (§576): CmdTestSuiteTest assere "1 skipped (no tests, no main)".
            if (skippedByTag == 0) {
                summary += " (no tests, no main)";
            } else if (skipped > 0) {
                summary += " (" + skipped + " no tests, no main / "
                        + skippedByTag + " no tests with tag '" + tag + "')";
            } else {
                summary += " (no tests with tag '" + tag + "')";
            }
        }
        System.out.println(summary);
        // §7.3/§8.5: medição de tempo por arquivo — o total e o mais lento dão a
        // base de "slow-test identification" sem flag nova. Só quando ao menos um
        // arquivo foi processado (a saída histórica sem arquivos fica intacta).
        if (slowestFile != null) {
            System.out.println("time: " + totalMs + "ms total, slowest " + slowestFile
                    + " (" + slowestMs + "ms)");
        }
        // §8.5: o relatório por nível — só arquivos suíte (com testes) contam;
        // a linha aparece apenas quando ao menos um nível não-unit foi
        // declarado (a corrida pura unit mantém a saída histórica intacta).
        // §8.5: o relatório por nível — passed/failed reais por nível (o
        // mapeamento arquivo→nível + arquivo→resultado), no formato do plano
        // ("unit: N passed / M failed"). A linha imprime APENAS quando um
        // nível não-unit foi declarado (a corrida pura unit mantém a saída
        // histórica byte-identical).
        java.util.Map<String, int[]> byLevel = new java.util.LinkedHashMap<>();
        for (var e : fileLevel.entrySet()) {
            int[] c = byLevel.computeIfAbsent(e.getValue(), k -> new int[2]);
            if (fileResult.getOrDefault(e.getKey(), false)) c[0]++;
            else c[1]++;
        }
        if (byLevel.containsKey("integration") || byLevel.containsKey("e2e")) {
            StringBuilder lv = new StringBuilder();
            for (var e : byLevel.entrySet()) {
                if (lv.length() > 0) lv.append(", ");
                lv.append(e.getKey()).append(": ").append(e.getValue()[0])
                  .append(" passed / ").append(e.getValue()[1]).append(" failed");
            }
            System.out.println("levels: " + lv);
        }
        if (failed > 0) System.exit(1);
        // §576: nenhum arquivo executável (todos auxiliares) não é sucesso —
        // espelha o #708 ("zero discovered tests is not a success").
        if (skipped > 0 && passed == 0 && failed == 0) {
            System.err.println("test: no runnable test or program file found ("
                    + skipped + " source(s) have neither `test` nor `main`)");
            System.exit(1);
        }
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

    /**
     * §587: algum teste descoberto no arquivo carrega a tag do filtro? O harness
     * só filtra em compile-time (propriedade {@code kof.test.tag}), então o
     * runner precisa saber disso antes para não contar um arquivo sem match como
     * passed. As tags vêm de {@link CompilerDriver.TestInfo#tags()}.
     */
    private static boolean hasTagMatch(List<CompilerDriver.TestInfo> tests, String tag) {
        // §7.1 multi-tag: o filtro é uma lista separada por vírgula e casa por
        // DISJUNÇÃO (OR), espelhando TestHarnessBuilder.matchesAnyTag (mesma
        // regra de trim/ignorar vazio) — o runner precisa do MESMO veredito que
        // o harness compilado para não contar um arquivo sem match como passed.
        for (CompilerDriver.TestInfo t : tests) {
            for (String part : tag.split(",", -1)) {
                String want = part.trim();
                if (!want.isEmpty() && t.tags().contains(want)) return true;
            }
        }
        return false;
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

    /** §6 provider gate: o binário do provider declarado é probeável? Retorna
     *  a versão impressa ou null (o gate recusa com motivo nomeado — nunca um
     *  false green). `npx playwright --version` é a semente; novos providers
     *  entram aqui por nome declarado (nunca um heurístico). */
    private static String providerProbe(String provider) {
        if (!"playwright".equals(provider)) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder("npx", "playwright", "--version");
            pb.environment().computeIfAbsent("PATH",
                    k -> "/usr/local/bin:/usr/bin:/bin:" + System.getProperty("user.home") + "/.local/node/bin");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!done || p.exitValue() != 0) { p.destroyForcibly(); return null; }
            String out = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
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
