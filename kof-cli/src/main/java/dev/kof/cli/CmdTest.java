package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import java.io.IOException;
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

    static void run(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof test <file.kf|dir> [--target jvm|native|js|android]"); System.exit(1); return; }
        Path src = Path.of(args[1]);
        Target target = Target.JVM;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--target") && i + 1 < args.length) {
                target = KofCliSupport.parseTarget(args[i + 1]);
                i++;
            }
        }
        if (!Files.exists(src)) { System.err.println("not found: " + src); System.exit(1); return; }
        List<Path> files = Files.isDirectory(src) ? KofCliSupport.collect(src) : List.of(src);
        if (files.isEmpty()) { System.out.println("no .kf files found"); return; }
        CompilerDriver driver = new CompilerDriver();
        int passed = 0;
        int failed = 0;
        // per-file (docs/ecosystem-coverage.md §3.11): cada .kf é um programa
        // independente com seu próprio main() — NUNCA agrupar irmãos num
        // módulo só (PKG002: 2 main()). Cross-file é domínio de kof build.
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
                            Process p = pb.start();
                            output.append(new String(p.getInputStream().readAllBytes()));
                            int ec = p.waitFor();
                            ok = ec == 0;
                            if (!ok) output.append("exit code: ").append(ec).append('\n');
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
                            int ec = dev.kof.runtime.KofJsRunner.run(java.nio.file.Path.of(entry),
                                    System.out, System.in, System.err, false, new String[0]);
                            ok = ec == 0;
                            if (!ok) output.append("exit code: ").append(ec).append('\n');
                        } catch (IOException e) {
                            ok = false;
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
                            Process p = pb.start();
                            output.append(new String(p.getInputStream().readAllBytes()));
                            int ec = p.waitFor();
                            ok = ec == 0;
                            if (!ok) output.append("exit code: ").append(ec).append('\n');
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
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
