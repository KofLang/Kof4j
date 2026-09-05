package dev.kof.cli;

import dev.kof.compiler.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
            if (args[fileIdx].equals("--target") && fileIdx + 1 < args.length) fileIdx += 2;
            else if (args[fileIdx].startsWith("--target=")) fileIdx += 1;
            else fileIdx += 1;
        }
        if (fileIdx >= args.length) { System.err.println("usage: kof run <file.kf> [--target ...]"); return; }
        Path file = Path.of(args[fileIdx]);
        if (!Files.exists(file)) { System.err.println("file not found: " + file); System.exit(1); return; }

        Target target = Target.JVM;
        boolean release = false;
        boolean useDeps = false;
        int argStart = fileIdx + 1;
        for (int i = 1; i < args.length; i++) {
            if (i == fileIdx) continue;
            if (args[i].startsWith("--target=")) {
                target = KofCliSupport.parseTarget(args[i].substring("--target=".length()));
                argStart = i + 1;
            } else if (args[i].equals("--target") && i + 1 < args.length) {
                target = KofCliSupport.parseTarget(args[i + 1]);
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
        CompilationResult result = driver.compileSources(sources, tempDir, target, runRoot);
        for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
        if (!result.success()) { KofCliSupport.cleanup(tempDir); System.exit(1); return; }

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
        KofCliSupport.executeProcess(javaArgs, tempDir);
    }
}
