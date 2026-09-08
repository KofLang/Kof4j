package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import dev.kof.compiler.TargetMatrix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * kof run — compila o módulo do arquivo (irmãos .kf inclusos) e executa
 * no target jvm|native|js|android. Extraído de Main (REFACTOR-500 Fase 8).
 */
final class CmdRun {

    private CmdRun() {
    }

    static void run(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof run <file.kf> [--target jvm|native|js|android] [--deps] [args...]"); return; }
        if ("--help".equals(args[1]) || "-h".equals(args[1]) || "--version".equals(args[1])) {
            System.out.println("usage: kof run <file.kf> [--target jvm|native|js|android] [--deps] [args...]");
            return;
        }
        // O arquivo é o primeiro arg não-flag; --target/--deps/--release podem
        // vir antes ou depois dele.
        int fileIdx = 1;
        while (fileIdx < args.length && args[fileIdx].startsWith("-")) {
            if ((args[fileIdx].equals("--target") || args[fileIdx].equals("--backend")
                    || args[fileIdx].equals("--frontend")) && fileIdx + 1 < args.length) fileIdx += 2;
            else if (args[fileIdx].startsWith("--target=") || args[fileIdx].startsWith("--backend=")
                    || args[fileIdx].startsWith("--frontend=")) fileIdx += 1;
            else fileIdx += 1;
        }
        if (fileIdx >= args.length) { System.err.println("usage: kof run <file.kf> [--target ...]"); return; }
        Path file = Path.of(args[fileIdx]);
        if (!Files.exists(file)) { System.err.println("file not found: " + file); System.exit(1); return; }

        Target target = Target.JVM;
        Target frontendTarget = null;
        boolean targetFlagged = false;
        boolean release = false;
        boolean useDeps = false;
        String backendFlag = null;
        String frontendFlag = null;
        int argStart = fileIdx + 1;
        for (int i = 1; i < args.length; i++) {
            if (i == fileIdx) continue;
            if (args[i].startsWith("--target=")) {
                target = KofCliSupport.parseTarget(args[i].substring("--target=".length()));
                targetFlagged = true;
                argStart = i + 1;
            } else if (args[i].equals("--target") && i + 1 < args.length) {
                target = KofCliSupport.parseTarget(args[i + 1]);
                targetFlagged = true;
                argStart = i + 2;
                i++;
            } else if (args[i].startsWith("--backend=")) {
                backendFlag = args[i].substring("--backend=".length());
                argStart = i + 1;
            } else if (args[i].equals("--backend") && i + 1 < args.length) {
                backendFlag = args[i + 1];
                argStart = i + 2;
                i++;
            } else if (args[i].startsWith("--frontend=")) {
                frontendFlag = args[i].substring("--frontend=".length());
                argStart = i + 1;
            } else if (args[i].equals("--frontend") && i + 1 < args.length) {
                frontendFlag = args[i + 1];
                argStart = i + 2;
                i++;
            } else if (args[i].equals("--release")) {
                release = true;
            } else if (args[i].equals("--deps")) {
                useDeps = true;
                argStart = i + 1;
            }
        }
        // program args: sempre após o arquivo e após as flags
        if (argStart <= fileIdx) argStart = fileIdx + 1;

        Path tempDir;
        try { tempDir = Files.createTempDirectory("kof-run-"); }
        catch (IOException e) { System.err.println("failed to create temp dir: " + e.getMessage()); System.exit(1); return; }

        CompilerDriver driver = new CompilerDriver();
        if (release) driver.setDebugInfoEnabled(false);
        // módulo = diretório do arquivo de entrada (irmãos .kf incluídos)
        java.util.List<Path> sources = new ArrayList<>();
        sources.add(file.toAbsolutePath().normalize());
        Path siblingDir = file.toAbsolutePath().normalize().getParent();
        if (siblingDir != null) {
            for (Path sib : KofCliSupport.collectShallow(siblingDir)) {
                Path abs = sib.toAbsolutePath().normalize();
                if (!abs.equals(sources.get(0)) && !sources.contains(abs)) sources.add(abs);
            }
        }
        Path runRoot = siblingDir != null ? siblingDir : file.toAbsolutePath().getParent();
        // Fase 1/2 (plataforma): se o arquivo vive num projeto com kof.toml,
        // a raiz do projeto manda (imports cross-directory resolvem).
        Path discovered = driver.resolveModuleRoot(sources);
        if (discovered != null) runRoot = discovered;
        // F2-parte-4 (plataforma): --backend/--frontend sobrepõem o kof.toml.
        // --target (contrato legado, congelado) tem precedência; sem ele, o
        // backend da flag/kof.toml dirige a execução. Combinação validada pela
        // TargetMatrix ANTES de compilar (R6: erro honesto).
        if (!targetFlagged && (backendFlag != null || frontendFlag != null)) {
            try {
                KofCliSupport.Targets sel = KofCliSupport.selectTargets(backendFlag, frontendFlag, runRoot);
                if (sel.backend() != null) target = sel.backend();
                frontendTarget = sel.frontend();
            } catch (IllegalArgumentException e) {
                System.err.println("run: " + e.getMessage());
                KofCliSupport.cleanup(tempDir);
                System.exit(1);
                return;
            }
        }
        if (useDeps) {
            try {
                String depsCp = Deps.classpath();
                if (!depsCp.isBlank()) {
                    java.util.List<Path> entries = new ArrayList<>();
                    for (String part : depsCp.split(java.util.regex.Pattern.quote(
                            System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":"))) {
                        if (!part.isBlank()) entries.add(Path.of(part));
                    }
                    driver.setExternalClasspath(entries);
                }
            } catch (IOException e) {
                System.err.println("run: falha ao ler kofdeps: " + e.getMessage());
                KofCliSupport.cleanup(tempDir);
                System.exit(1);
                return;
            }
        }
        // KofScript: coringa de execução — interpreta a IR no mesmo frontend,
        // sem emitir .class/.native/.js/.wasm/.apk (fase 2 do plano de plataforma).
        if (target == Target.SCRIPT) {
            String[] programArgs = new String[Math.max(0, args.length - argStart)];
            for (int i = argStart; i < args.length; i++) {
                programArgs[i - argStart] = args[i];
            }
            try {
                dev.kof.compiler.KofInterpreter.Result ir =
                        driver.interpret(sources, runRoot, programArgs);
                if (!ir.stdout().isEmpty()) System.out.print(ir.stdout());
                if (!ir.stderr().isEmpty()) System.err.print(ir.stderr());
                KofCliSupport.cleanup(tempDir);
                System.exit(ir.exitCode());
            } catch (dev.kof.compiler.KofInterpretException e) {
                for (Diagnostic d : e.diagnostics().getDiagnostics()) System.err.println(d.format());
                KofCliSupport.cleanup(tempDir);
                System.exit(1);
            }
            return;
        }
        CompilationResult result = driver.compileSources(sources, tempDir, target, runRoot);
        for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
        if (!result.success()) { KofCliSupport.cleanup(tempDir); System.exit(1); return; }

        // F3 (plataforma, APPLICATION_MODEL I2/P2): full-stack run — backend
        // JVM + web/ → compila o bundle e passa os caminhos ao app via env
        // (KOF_WEB_OUT/KOF_STATIC_OUT), que o backend consome com
        // config.env(...) + app.serveDir. Aditivo: sem web/ = run de hoje.
        Map<String, String> appEnv = Map.of();
        if (siblingDir != null) {
            KofCliSupport.Layout layout = KofCliSupport.detectLayout(siblingDir);
            String app001 = KofCliSupport.app001(target, layout.fullStack());
            if (app001 != null) {
                System.err.println("run: " + app001);
                KofCliSupport.cleanup(tempDir);
                System.exit(1);
                return;
            }
        }
        if (target == Target.JVM && siblingDir != null) {
            KofCliSupport.Layout layout = KofCliSupport.detectLayout(siblingDir);
            if (layout.fullStack()) {
                Target feTarget = frontendTarget != null ? frontendTarget : Target.JS;
                if (feTarget != Target.JS) {
                    System.err.println("run: frontend '" + TargetMatrix.name(feTarget)
                            + "' ainda não roda em 'kof run' (só kofjs; script-SSR = Fase 8)");
                    KofCliSupport.cleanup(tempDir);
                    System.exit(1);
                    return;
                }
                KofCliSupport.buildFrontend(driver, layout, feTarget, tempDir);
                appEnv = new java.util.HashMap<>();
                appEnv.put("KOF_WEB_OUT", tempDir.resolve("frontend").toString());
                if (layout.staticDir() != null) {
                    appEnv.put("KOF_STATIC_OUT", tempDir.resolve("static").toString());
                }
            }
        }

        if (target == Target.JS) {
            String entry = KofCliSupport.findJsEntry(tempDir);
            if (entry == null) {
                System.err.println("no JS entry point found");
                KofCliSupport.cleanup(tempDir);
                System.exit(1);
                return;
            }
            // The KofJS target executes the generated module with the embedded
            // JavaScript engine — no Node.js or external runtime required.
            // Windows created with kof.ui open in the system webview.
            int exitCode;
            String[] programArgs = new String[Math.max(0, args.length - argStart)];
            for (int i = argStart; i < args.length; i++) {
                programArgs[i - argStart] = args[i];
            }
            try {
                exitCode = dev.kof.runtime.KofJsRunner.run(java.nio.file.Path.of(entry),
                        System.out, System.in, System.err, true, programArgs);
            } catch (IOException e) {
                System.err.println("failed to execute: " + e.getMessage());
                KofCliSupport.cleanup(tempDir);
                System.exit(1);
                return;
            }
            KofCliSupport.cleanup(tempDir);
            System.exit(exitCode);
            return;
        }

        // Target android: a compilação já gerou o projeto Maven; não há o
        // que executar no desktop — orientar o próximo passo
        if (target == Target.ANDROID) {
            System.out.println("Android project generated (temp): " + tempDir);
            System.out.println("For a persistent project use:");
            System.out.println("  kof build <dir> --target android --output <projeto>");
            System.out.println("Then (ANDROID_HOME apontando pro SDK):");
            System.out.println("  mvn verify              # APK em target/kof-app.apk");
            System.out.println("  adb install target/kof-app.apk");
            return;
        }

        // Target nativo: executa o ELF produzido — não há classes JVM aqui
        if (target == Target.NATIVE) {
            Path bin = tempDir.resolve("Default/Main");
            if (!Files.exists(bin)) {
                System.err.println("no native binary produced");
                KofCliSupport.cleanup(tempDir);
                System.exit(1);
                return;
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(bin.toString());
            for (int i = argStart; i < args.length; i++) cmd.add(args[i]);
            KofCliSupport.executeProcess(cmd, tempDir);
            return;
        }

        String className = KofCliSupport.findMainClass(tempDir);
        if (System.getProperty("kof.trace") != null) {
            System.err.println("LAUNCH className=" + className + " dir=" + tempDir);
        }
        if (className == null) {
            System.err.println("no main class found");
            KofCliSupport.cleanup(tempDir);
            System.exit(1);
            return;
        }
        List<String> javaArgs = new ArrayList<>();
        javaArgs.add(KofCliSupport.javaExecutable());
        javaArgs.add("-Dkof.root=" + file.toAbsolutePath().normalize().getParent());
        javaArgs.add("-cp");
        String jvmCp = tempDir.toString();
        if (useDeps) {
            try {
                String depsCp = Deps.classpath();
                if (!depsCp.isBlank()) jvmCp += java.io.File.pathSeparator + depsCp;
            } catch (IOException ignored) {
            }
        }
        javaArgs.add(jvmCp);
        javaArgs.add(className);
        for (int i = argStart; i < args.length; i++) javaArgs.add(args[i]);
        KofCliSupport.executeProcess(javaArgs, tempDir, appEnv);
    }
}
