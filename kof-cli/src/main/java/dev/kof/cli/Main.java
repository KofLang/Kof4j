package dev.kof.cli;

import dev.kof.compiler.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    public static void main(String[] args) {
        if (args.length == 0) { printUsage(); return; }
        switch (args[0]) {
            case "build" -> build(args);
            case "run" -> run(args);
            case "serve" -> serve(args);
            case "check" -> check(args);
            case "test" -> test(args);
            case "bench" -> System.exit(Bench.run(args));
            case "profile" -> System.exit(Profile.run(args));
            case "inspect" -> System.exit(Inspect.run(args));
            case "decompile" -> System.exit(Decompile.run(args));
            case "translate" -> System.exit(Translate.run(args));
            case "compare" -> System.exit(Compare.run(args));
            case "migrate" -> System.exit(Migrate.run(args));
            case "debug" -> System.exit(KofDebug.run(args));
            case "info" -> info(args);
            case "lsp" -> lsp();
            case "install" -> install(args);
            case "script" -> System.exit(script(args));
            case "repl" -> System.exit(repl(args));
            case "init" -> System.exit(init(args));
            case "deps" -> System.exit(Deps.run(args));
            case "c" -> c(args);
            case "fmt" -> System.exit(Fmt.run(args));
            case "config" -> config(args);
            case "version" -> System.out.println("kof " + KofVersion.version());
            default -> { System.err.println("unknown: " + args[0]); printUsage(); }
        }
    }

    private static void run(String[] args) {
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
                target = parseTarget(args[i].substring("--target=".length()));
                argStart = i + 1;
            } else if (args[i].equals("--target") && i + 1 < args.length) {
                target = parseTarget(args[i + 1]);
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
            for (Path sib : collectShallow(siblingDir)) {
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
                cleanup(tempDir);
                System.exit(1);
                return;
            }
        }
        CompilationResult result = driver.compileSources(sources, tempDir, target, runRoot);
        for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
        if (!result.success()) { cleanup(tempDir); System.exit(1); return; }

        if (target == Target.JS) {
            String entry = findJsEntry(tempDir);
            if (entry == null) {
                System.err.println("no JS entry point found");
                cleanup(tempDir);
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
                cleanup(tempDir);
                System.exit(1);
                return;
            }
            cleanup(tempDir);
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
                cleanup(tempDir);
                System.exit(1);
                return;
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(bin.toString());
            for (int i = argStart; i < args.length; i++) cmd.add(args[i]);
            executeProcess(cmd, tempDir);
            return;
        }

        String className = findMainClass(tempDir);
        if (System.getProperty("kof.trace") != null) {
            System.err.println("LAUNCH className=" + className + " dir=" + tempDir);
        }
        if (className == null) {
            System.err.println("no main class found");
            cleanup(tempDir);
            System.exit(1);
            return;
        }
        List<String> javaArgs = new ArrayList<>();
        javaArgs.add(javaExecutable());
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
        executeProcess(javaArgs, tempDir);
    }

    private static Process servedProcess;

    private static void executeProcess(List<String> command, Path tempDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            Process p = pb.start();
            servedProcess = p;
            int exitCode = p.waitFor();
            if (tempDir != null) cleanup(tempDir);
            System.exit(exitCode);
        } catch (IOException e) {
            System.err.println("failed to execute: " + e.getMessage());
            if (tempDir != null) cleanup(tempDir);
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (tempDir != null) cleanup(tempDir);
            System.exit(1);
        }
    }

    private static String findJsEntry(Path dir) {
        Path defaultEntry = dir.resolve("Default.mjs");
        if (Files.exists(defaultEntry)) return defaultEntry.toString();
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .filter(p -> !p.toString().contains("kof-runtime"))
                    .findFirst()
                    .map(p -> p.toString())
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

private static void build(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof build <source-dir> [--target jvm|native|js|android] [--output <dir>] [--release] [--apk] [--classpath <jars>] [--keystore <ks> [--storepass <p>] [--keypass <p>] [--alias <a>]]");
        if ("--help".equals(args[1]) || "-h".equals(args[1]) || "--version".equals(args[1])) {
            System.out.println("usage: kof build <source-dir> [--target jvm|native|js|android] [--output <dir>] [--release] [--apk] [--classpath <jars>] [--keystore <ks> [--storepass <p>] [--keypass <p>] [--alias <a>]]");
            return;
        } return; }
        Path src = Path.of(args[1]);
        Target target = Target.JVM;
        Path out = Path.of("build/classes");
        boolean release = false;
        boolean apk = false;
        String classpath = null;
        String keystore = null;
        String storepass = null;
        String keypass = null;
        String keyalias = null;
        boolean useDeps = false;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--target=")) {
                target = parseTarget(arg.substring("--target=".length()));
            } else if (arg.startsWith("--output=")) {
                out = Path.of(arg.substring("--output=".length()));
            } else if (arg.equals("--target") && i + 1 < args.length) {
                target = parseTarget(args[i + 1]);
                i++;
            } else if (arg.equals("--output") && i + 1 < args.length) {
                out = Path.of(args[i + 1]);
                i++;
            } else if (arg.equals("--release")) {
                release = true;
            } else if (arg.equals("--apk")) {
                apk = true;
            } else if (arg.equals("--deps")) {
                useDeps = true;
            } else if (arg.startsWith("--classpath=")) {
                classpath = arg.substring("--classpath=".length());
            } else if (arg.equals("--classpath") && i + 1 < args.length) {
                classpath = args[++i];
            } else if (arg.startsWith("--keystore=")) {
                keystore = arg.substring("--keystore=".length());
            } else if (arg.equals("--keystore") && i + 1 < args.length) {
                keystore = args[++i];
            } else if (arg.startsWith("--storepass=")) {
                storepass = arg.substring("--storepass=".length());
            } else if (arg.equals("--storepass") && i + 1 < args.length) {
                storepass = args[++i];
            } else if (arg.startsWith("--keypass=")) {
                keypass = arg.substring("--keypass=".length());
            } else if (arg.equals("--keypass") && i + 1 < args.length) {
                keypass = args[++i];
            } else if (arg.startsWith("--alias=")) {
                keyalias = arg.substring("--alias=".length());
            } else if (arg.equals("--alias") && i + 1 < args.length) {
                keyalias = args[++i];
            }
        }
        CompilerDriver driver = new CompilerDriver();
        if (release) driver.setDebugInfoEnabled(false);
        // dependências externas (android.jar etc.) geridas pelo Kof via
        // ExternalClasspath — separadas por ':' ou ';'
        if (classpath != null && !classpath.isBlank()) {
            List<Path> entries = new ArrayList<>();
            for (String part : classpath.split("[:;]")) {
                if (!part.isBlank()) entries.add(Path.of(part));
            }
            driver.setExternalClasspath(entries);
        }
        // kofdeps: dependências Maven resolvidas no cache ~/.kof/deps
        if (useDeps) {
            try {
                String depsCp = Deps.classpath();
                if (!depsCp.isBlank()) {
                    List<Path> entries = new ArrayList<>();
                    for (String part : depsCp.split(java.util.regex.Pattern.quote(
                            System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":"))) {
                        if (!part.isBlank()) entries.add(Path.of(part));
                    }
                    driver.setExternalClasspath(entries);
                }
            } catch (IOException e) {
                System.err.println("build: falha ao ler kofdeps: " + e.getMessage());
                return;
            }
        }
        List<Path> files = collect(src);
        if (files.isEmpty()) { System.out.println("no .kf files found"); return; }
        files.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        // convenção Go-like: TODOS os .kf do diretório formam UM módulo
        // (raiz = diretório passado ao build; imports de pacotes resolvem daí)
        CompilationResult module = driver.compileSources(files, out, target,
                src.toAbsolutePath().normalize());
        for (Diagnostic d : module.diagnostics().getDiagnostics()) System.out.println(d.format());
        if (!module.success()) System.exit(1);
        // target android + --apk: pipeline direto (sem Maven) usando o SDK
        if (target == Target.ANDROID && apk) {
            runApkPipeline(out, keystore, storepass, keypass, keyalias);
        }
    }

    /**
     * Pipeline APK standalone (#6/#7): chama os binários oficiais do SDK
     * direto — d8 → aapt2 → zip → zipalign → apksigner. Sem --keystore,
     * gera debug keystore local na primeira vez; com --keystore, assina
     * com o keystore do usuário (release signing parametrizável).
     */
    private static void runApkPipeline(Path projDir, String keystore, String storepass,
                                       String keypass, String keyalias) {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome == null || androidHome.isBlank()) {
            System.err.println("--apk: ANDROID_HOME não definido; gere o projeto e use 'mvn verify'");
            return;
        }
        Path bt = Path.of(androidHome, "build-tools", "34.0.0");
        Path platformJar = Path.of(androidHome, "platforms", "android-34", "android.jar");
        if (!Files.isExecutable(bt.resolve("aapt2"))) {
            System.err.println("--apk: build-tools 34.0.0 não encontrado em " + bt);
            return;
        }
        Path build = projDir.resolve("target");
        Path apkDir = build.resolve("apk");
        boolean userKs = keystore != null && !keystore.isBlank();
        try {
            Files.createDirectories(apkDir);
            // debug keystore local (só quando o usuário não passou --keystore)
            Path ks = userKs ? Path.of(keystore) : build.resolve("debug.keystore");
            if (!userKs && !Files.exists(ks)) {
                run(List.of("keytool", "-genkeypair", "-keystore", ks.toString(),
                        "-alias", "androiddebugkey", "-storepass", "android",
                        "-keypass", "android", "-keyalg", "RSA", "-validity", "9999",
                        "-dname", "CN=Kof Debug,O=Kof,C=BR"), projDir);
            }
            run(List.of(bt.resolve("aapt2").toString(), "compile", "--dir",
                    projDir.resolve("src/main/res").toString(),
                    "-o", apkDir.resolve("res.zip").toString()), projDir);
            run(List.of(bt.resolve("aapt2").toString(), "link",
                    "-o", apkDir.resolve("base.apk").toString(),
                    "-I", platformJar.toString(),
                    "--manifest", projDir.resolve("src/main/AndroidManifest.xml").toString(),
                    "-A", projDir.resolve("src/main/assets").toString(),
                    "-R", apkDir.resolve("res.zip").toString()), projDir);
            run(List.of(bt.resolve("d8").toString(), "--release",
                    "--lib", platformJar.toString(), "--min-api", "24",
                    "--output", apkDir.toString(),
                    projDir.resolve("libs/kof-app.jar").toString()), projDir);
            run(List.of("jar", "uf", apkDir.resolve("base.apk").toString(),
                    "-C", apkDir.toString(), "classes.dex"), projDir);
            run(List.of(bt.resolve("zipalign").toString(), "-f", "4",
                    apkDir.resolve("base.apk").toString(),
                    apkDir.resolve("aligned.apk").toString()), projDir);
            String sp = userKs && storepass != null ? storepass : "android";
            String kp = userKs && keypass != null ? keypass : sp;
            List<String> sign = new ArrayList<>(List.of(
                    bt.resolve("apksigner").toString(), "sign",
                    "--ks", ks.toString(), "--ks-pass", "pass:" + sp,
                    "--key-pass", "pass:" + kp));
            if (userKs && keyalias != null && !keyalias.isBlank()) {
                sign.add("--ks-key-alias");
                sign.add(keyalias);
            }
            sign.add("--out");
            sign.add(build.resolve("kof-app.apk").toString());
            sign.add(apkDir.resolve("aligned.apk").toString());
            run(sign, projDir);
            System.out.println("APK gerado: " + build.resolve("kof-app.apk"));
        } catch (Exception e) {
            System.err.println("pipeline apk falhou: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(List<String> cmd, Path cwd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile()).inheritIO();
        Process proc = pb.start();
        int code = proc.waitFor();
        if (code != 0) throw new IOException("exit " + code + ": " + cmd.get(0));
    }

    private static Target parseTarget(String value) {
        return switch (value) {
            case "jvm" -> Target.JVM;
            case "native" -> Target.NATIVE;
            case "native.risc", "native.riscv64", "native.riscv" -> Target.NATIVE_RISCV64;
            case "native.arm", "native.aarch64", "native.aarch" -> Target.NATIVE_AARCH64;
            case "js" -> Target.JS;
            case "android" -> Target.ANDROID;
            default -> {
                System.err.println("unknown target: " + value);
                System.exit(1);
                yield Target.JVM;
            }
        };
    }

    /** Irmãos .kf do MESMO diretório (não-recursivo) — inclusão no módulo do run. */
    private static List<Path> collectShallow(Path dir) {
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(p -> p.toString().endsWith(".kf")).forEach(files::add);
        } catch (IOException e) { System.err.println("error: " + e.getMessage()); }
        files.sort(java.util.Comparator.comparing(Path::toString));
        return files;
    }

    private static List<Path> collect(Path dir) {
        // convenção Go-like: um diretório = UM pacote → não-recursivo
        // (subdirs como tests/ são pacotes independentes)
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) { s.filter(p -> p.toString().endsWith(".kf")).forEach(files::add); }
        catch (IOException e) { System.err.println("error: " + e.getMessage()); }
        files.sort(java.util.Comparator.comparing(Path::toString));
        return files;
    }

    private static void cleanup(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private static String javaExecutable() {
        String install = System.getProperty("kof.install.dir", "");
        if (!install.isEmpty()) {
            String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
            Path jdk = Path.of(install, "jdk", "bin", exe);
            if (Files.isExecutable(jdk)) return jdk.toString();
        }
        return "java";
    }

    private static String findMainClass(Path dir) {
        try (var s = Files.walk(dir)) {
            List<String> candidates = s.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> dir.relativize(p).toString()
                            .replace(".class", "")
                            .replace("/", ".")
                            .replace("\\", "."))
                    .toList();
            for (String c : candidates) {
                if (c.endsWith(".Main") || c.equals("Main")) return c;
            }
            return candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
        } catch (IOException e) {
            return null;
        }
    }

    private static void printUsage() {
        System.out.println("usage: kof <command>");
        System.out.println("  build <dir> [--target jvm|native|js|android] [--output <dir>] [--release] [--apk]");
        System.out.println("  run <file.kf> [--target jvm|native|js|android] [--release] [args...]");
        System.out.println("  serve <file.kf> [--port <port>] [--host <host>]");
        System.out.println("  check <file.kf|dir>          type-check without emitting output");
        System.out.println("  script <file.ks|kf> [--target jvm|native|js]   execução direta KofScript (JVM/Native/JS, diagnostics com file:line)");
        System.out.println("  repl                         REPL incremental KofScript (type 'exit' to quit)");
        System.out.println("  test <file.kf|dir> [--target jvm|native]   run programs, PASS/FAIL by exit code");
        System.out.println("  bench [paths...] [--target jvm|native|js|android] [--iterations N] [--warmup N] [--baseline <file>]");
        System.out.println("                          [--update-baseline <file>] [--threshold <ratio>] [--json] [--quick]");
        System.out.println("                          [--fail-on-regression]");
        System.out.println("                          compile, run, validate output, collect metrics, compare baseline");
        System.out.println("  profile <file.kf> [--target jvm|native|js|android] [args...]   run + execution metrics (CPU, RSS, GC)");
        System.out.println("  inspect <file.kf> [--json]   IR statistics: ops before/after optimization");
        System.out.println("  decompile <file.class> [--output <file.kf>]   structural Kof skeleton from a .class");
        System.out.println("  translate <file.java> [--output <file.kf>]    Java subset -> Kof source");
        System.out.println("  compare <legacy.class|jar> <file.kf> [--stdin <s>] [--arg <v>] [--json]   differential test");
        System.out.println("  migrate <file.class|java> [--output <file.kf>] [--json]   migrate + traceable report");
        System.out.println("  config gen <file.kf|dir> [--target jvm|native|js] [--output <arquivo>]");
        System.out.println("                          gera template kof.config a partir das chaves config.* do código");
        System.out.println("  info [--json]                environment and platform report");
        System.out.println("  lsp                          Language Server (stdio, LSP protocol)");
        System.out.println("  install <dir>                install this build as a distribution");
        System.out.println("  deps <init|add|remove|list|resolve>   package manager (kofdeps)");
        System.out.println("  version");
        System.out.println();
        System.out.println("note: the js target is in development (alpha); it runs on Kof's embedded JS engine");
    }

    private static void install(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof install <dir>"); System.exit(1); return; }
        Path prefix = Path.of(args[1]);
        try {
            Path jar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(jar)) {
                System.err.println("install: run from the packaged kof.jar (bin/kof install <dir>)");
                System.exit(1);
                return;
            }
            Files.createDirectories(prefix.resolve("bin"));
            Files.createDirectories(prefix.resolve("lib"));
            Files.copy(jar, prefix.resolve("lib/kof.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Path launcher = prefix.resolve("bin/kof");
            Files.writeString(launcher, """
                    #!/usr/bin/env bash
                    set -euo pipefail
                    DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
                    HOME_DIR="$(dirname "$DIR")"
                    EMBEDDED=false
                    JAVA="$(command -v java || true)"
                    if [ -x "$HOME_DIR/jdk/bin/java" ]; then
                        JAVA="$HOME_DIR/jdk/bin/java"
                        EMBEDDED=true
                    fi
                    if [ -z "$JAVA" ]; then echo "kof: no java found" >&2; exit 1; fi
                    exec "$JAVA" -Dkof.install.dir="$HOME_DIR" -Dkof.embedded.jdk="$EMBEDDED" \\
                        -jar "$HOME_DIR/lib/kof.jar" "$@"
                    """);
            launcher.toFile().setExecutable(true);
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                Files.writeString(prefix.resolve("bin/kof.bat"),
                        "@echo off\r\nset KOF_HOME=%~dp0..\r\njava -Dkof.install.dir=\"%KOF_HOME%\" -jar \"%KOF_HOME%\\lib\\kof.jar\" %*\r\n");
            }
            Files.writeString(prefix.resolve("VERSION"), KofVersion.version() + "\n");
            String installDir = System.getProperty("kof.install.dir", "");
            Path src = installDir.isEmpty() ? Path.of("").toAbsolutePath() : Path.of(installDir);
            if (Files.exists(src.resolve("editor"))) copyTree(src.resolve("editor"), prefix.resolve("editor"));
            if (Files.exists(src.resolve("tooling"))) copyTree(src.resolve("tooling"), prefix.resolve("tooling"));
            System.out.println("kof installed at " + prefix.toAbsolutePath());
            System.out.println("add " + prefix.resolve("bin") + " to your PATH and run: kof info");
        } catch (Exception e) {
            System.err.println("install: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (var s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path rel = from.relativize(p);
                Path target = to.resolve(rel.toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void lsp() {
        LspServer server = new LspServer(System.in, System.out);
        try {
            server.run();
        } catch (IOException e) {
            System.err.println("lsp: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * kof config gen <file.kf|dir> [--target jvm|native|js] [--output <arquivo>]
     * (docs/stdlib-config.md §8.2 P3): compila (JVM, só análise) e gera um
     * template kof.config com as chaves descobertas em compile-time.
     * Defaults viram comentário; required sem default vira linha ativa.
     */
    private static void config(String[] args) {
        if (args.length < 3 || !"gen".equals(args[1])) {
            System.err.println("usage: kof config gen <file.kf|dir> [--target jvm|native|js] [--output <arquivo>]");
            System.exit(1);
            return;
        }
        Path src = Path.of(args[2]);
        if (!Files.exists(src)) { System.err.println("not found: " + src); System.exit(1); return; }
        Target target = Target.JVM;
        Path output = null;
        for (int i = 3; i < args.length; i++) {
            if ("--target".equals(args[i]) && i + 1 < args.length) {
                target = parseTarget(args[++i]);
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                output = Path.of(args[++i]);
            }
        }
        // compile-only: chaves são coletadas em compile-time, nada é executado
        Path tmp;
        try { tmp = Files.createTempDirectory("kof-config-gen-"); }
        catch (IOException e) { System.err.println("temp dir: " + e.getMessage()); System.exit(1); return; }
        try {
            CompilerDriver driver = new CompilerDriver();
            List<Path> files = Files.isDirectory(src) ? collect(src) : List.of(src);
            if (files.isEmpty()) { System.out.println("no .kf files found"); return; }
            // multi-arquivo: um módulo só (chaves são do programa inteiro)
            CompilationResult result = files.size() == 1
                    ? driver.compile(files.get(0), tmp, target)
                    : driver.compileSources(files, tmp, target, Files.isDirectory(src) ? src : src.getParent());
            if (!result.success()) {
                System.err.println("compilation failed:");
                for (var d : result.diagnostics().getDiagnostics()) {
                    System.err.println("  " + d);
                }
                System.exit(1);
                return;
            }
            String template = driver.generateConfigTemplate();
            if (output != null) {
                Files.writeString(output, template);
                System.out.println("wrote " + output);
            } else {
                System.out.print(template);
            }
            if (driver.discoveredConfigKeys().isEmpty()) {
                System.err.println("(nenhuma chave config.* literal encontrada no código)");
            }
        } catch (IOException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        } finally {
            cleanup(tmp);
        }
    }

    private static void test(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof test <file.kf|dir> [--target jvm|native|js|android]"); System.exit(1); return; }
        Path src = Path.of(args[1]);
        Target target = Target.JVM;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--target") && i + 1 < args.length) {
                target = parseTarget(args[i + 1]);
                i++;
            }
        }
        if (!Files.exists(src)) { System.err.println("not found: " + src); System.exit(1); return; }
        List<Path> files = Files.isDirectory(src) ? collect(src) : List.of(src);
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
                    String className = findMainClass(tmp);
                    if (className == null) {
                        ok = false;
                        output.append("no main class found\n");
                    } else {
                        try {
                            ProcessBuilder pb = new ProcessBuilder(javaExecutable(), "-cp", tmp.toString(), className);
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
                    String entry = findJsEntry(tmp);
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
            cleanup(tmp);
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



    /** kof init [dir] — scaffold mínimo: hello.kf + estrutura padrão. */
    private static int init(String[] args) {
        String dirName = args.length > 1 ? args[1] : ".";
        Path dir = Path.of(dirName);
        try {
            Files.createDirectories(dir);
            Path src = dir.resolve("main.kf");
            if (Files.exists(src)) {
                System.err.println("init: " + src + " já existe");
                return 1;
            }
            Files.writeString(src, """
                    // Projeto Kof — rode com: kof run main.kf
                    main() {
                        println("Hello, Kof!")
                    }
                    """);
            Files.createDirectories(dir.resolve("tests"));
            Files.writeString(dir.resolve("tests/smoke.kf"), """
                    main() {
                        assert(1 + 1 == 2)
                        println("smoke ok")
                    }
                    """);
            Files.writeString(dir.resolve(".gitignore"), "target/\n*.log\n");
            System.out.println("criado em " + dir.toAbsolutePath().normalize()
                    + ":\n  main.kf\n  tests/smoke.kf\n  .gitignore\n\npróximos passos:\n  kof run " + src + "\n  kof test " + dir.resolve("tests"));
            return 0;
        } catch (Exception e) {
            System.err.println("init: " + e.getMessage());
            return 1;
        }
    }

    /**
     * kof script — execução direta de KofScript (.ks/.kf): para .ks,
     * declarações (fn/enum/class) viram top-level e o resto cai num main()
     * sintético; para .kf compila direto. Suporta --target jvm|native|js
     * e diagnostics com file:line via Diagnostic.format().
     */
    private static int script(String[] args) {
        if (args.length < 2 || "--help".equals(args[1]) || "-h".equals(args[1])) {
            System.out.println("usage: kof script <file.ks|kf> [--target jvm|native|js] [--watch] [--inspect] [args...]");
            System.out.println("       kof script --repl  (ou: kof repl)");
            System.out.println("       --watch   re-executa ao salvar o arquivo");
            System.out.println("       --inspect mostra IR stats sem executar");
            return 0;
        }
        if ("--repl".equals(args[1])) return repl(args);
        Path src = Path.of(args[1]);
        if (!Files.exists(src)) { System.err.println("not found: " + src); return 1; }
        Target target = Target.JVM;
        boolean watch = false;
        boolean inspect = false;
        java.util.List<String> progArgs = new java.util.ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--target=")) target = parseTarget(a.substring("--target=".length()));
            else if (a.equals("--target") && i + 1 < args.length) target = parseTarget(args[++i]);
            else if (a.equals("--watch")) watch = true;
            else if (a.equals("--inspect")) inspect = true;
            else if (a.equals("--")) { for (int j = i + 1; j < args.length; j++) progArgs.add(args[j]); break; }
            else if (a.startsWith("-")) { System.err.println("unknown option: " + a); return 1; }
            else progArgs.add(a);
        }
        if (inspect) {
            try {
                String stats = dev.kof.script.KofScript.inspect(src);
                System.out.println(stats);
                return 0;
            } catch (Exception e) { System.err.println("inspect: " + e.getMessage()); return 1; }
        }
        if (watch) return watchScript(src, target, progArgs.toArray(new String[0]));
        // .kf: compila direto (preserva file:line); .ks: wrap decls/stmts como antes
        try {
            if (src.toString().endsWith(".kf")) {
                var r = dev.kof.script.KofScript.runFile(src, target, progArgs.toArray(new String[0]));
                if (!r.success()) {
                    if (!r.stderr().isBlank()) System.err.print(r.stderr());
                    if (!r.stdout().isBlank()) System.out.print(r.stdout());
                    return 1;
                }
                if (!r.stdout().isBlank()) System.out.print(r.stdout());
                if (!r.stderr().isBlank()) System.err.print(r.stderr());
                return r.exitCode();
            }
            // .ks handling
            List<String> lines = Files.readAllLines(src);
            StringBuilder decls = new StringBuilder();
            StringBuilder stmts = new StringBuilder();
            StringBuilder cur = new StringBuilder();
            boolean curIsDecl = false;
            for (String raw : lines) {
                String t = raw.strip();
                // KofScript sugar: let/const -> var/val, async fn -> fn
                String tNorm = t.replaceFirst("^async\\s+", "");
                if (t.isEmpty() || t.startsWith("//")) continue;
                cur.append(raw).append('\n');
                boolean declStart = tNorm.startsWith("fn ") || tNorm.startsWith("enum ")
                        || tNorm.startsWith("class ") || tNorm.startsWith("record ")
                        || DECL_TYPE.matcher(tNorm).find();
                if (cur.length() == raw.length() + 1) curIsDecl = declStart;
                if (balance(cur.toString()) > 0) continue;
                String block = cur.toString().strip();
                // Normalize block for decls (so async fn becomes fn for the compiler)
                String blockNorm = block.replaceFirst("(?m)^async\\s+fn\\b", "fn");
                blockNorm = blockNorm.replaceAll("\\blet\\b", "var").replaceAll("\\bconst\\b", "val");
                if (curIsDecl) decls.append(blockNorm).append('\n');
                else {
                    String stmtNorm = block.replaceAll("\\blet\\b", "var").replaceAll("\\bconst\\b", "val");
                    // keep async inside stmt? async fn already handled
                    stmts.append(stmtNorm).append('\n');
                }
                cur.setLength(0);
            }
            if (!cur.isEmpty()) {
                if (curIsDecl) decls.append(cur); else stmts.append(cur);
            }
            if (stmts.isEmpty() && decls.isEmpty()) return 0;
            Path tmp = Files.createTempDirectory("kof-script-");
            StringBuilder program = new StringBuilder();
            program.append(decls);
            program.append("main() {\n").append(stmts).append("\n}\n");
            // Preserve original file name for diagnostics (bad.ks -> bad.kf)
            String kfName = src.getFileName().toString().replaceFirst("\\.ks$", ".kf");
            if (!kfName.endsWith(".kf")) kfName = "Script.kf";
            Path kf = tmp.resolve(kfName);
            Files.writeString(kf, program.toString());
            var r = dev.kof.script.KofScript.runFile(kf, target, progArgs.toArray(new String[0]));
            // Propaga diagnostics com file:line (KofScript já usa d.format())
            if (!r.success()) {
                if (!r.stderr().isBlank()) System.err.print(r.stderr());
                if (!r.stdout().isBlank()) System.out.print(r.stdout());
                cleanup(tmp);
                return 1;
            }
            if (!r.stdout().isBlank()) System.out.print(r.stdout());
            if (!r.stderr().isBlank()) System.err.print(r.stderr());
            cleanup(tmp);
            return r.exitCode();
        } catch (Exception e) {
            System.err.println("kof script: " + e.getMessage());
            return 1;
        }
    }

    private static int repl(String[] args) {
        try {
            dev.kof.script.KofScript.repl(System.in, System.out);
            return 0;
        } catch (Exception e) {
            System.err.println("kof repl: " + e.getMessage());
            return 1;
        }
    }

    private static int watchScript(Path src, Target target, String[] progArgs) {
        Path abs = src.toAbsolutePath().normalize();
        Path dir = abs.getParent();
        if (dir == null) dir = Path.of(".").toAbsolutePath();
        System.out.println("watching " + abs + " --target " + target + " (Ctrl+C to stop)");
        // initial run
        try {
            var r = dev.kof.script.KofScript.runFile(abs, target, progArgs);
            if (!r.success()) { if (!r.stderr().isBlank()) System.err.print(r.stderr()); }
            else { if (!r.stdout().isBlank()) System.out.print(r.stdout()); }
        } catch (Exception e) { System.err.println("watch: " + e.getMessage()); }
        try (var ws = FileSystems.getDefault().newWatchService()) {
            dir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY);
            while (true) {
                var key = ws.take();
                for (var ev : key.pollEvents()) {
                    Path changed = dir.resolve((Path) ev.context());
                    if (changed.equals(abs) || changed.toString().endsWith(".ks") || changed.toString().endsWith(".kf")) {
                        // debounce 200ms
                        Thread.sleep(200);
                        System.out.println("\n--- " + changed.getFileName() + " changed, re-running ---");
                        try {
                            var r = dev.kof.script.KofScript.runFile(abs, target, progArgs);
                            if (!r.success()) { if (!r.stderr().isBlank()) System.err.print(r.stderr()); if (!r.stdout().isBlank()) System.out.print(r.stdout()); }
                            else { if (!r.stdout().isBlank()) System.out.print(r.stdout()); if (!r.stderr().isBlank()) System.err.print(r.stderr()); }
                        } catch (Exception e) { System.err.println("watch: " + e.getMessage()); }
                        break;
                    }
                }
                if (!key.reset()) break;
            }
        } catch (Exception e) {
            System.err.println("watch: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    /** fn/enum/class/record ou retorno tipado ("Int nome(", "String nome("...) */
    private static final java.util.regex.Pattern DECL_TYPE =
            java.util.regex.Pattern.compile("^(Int|Long|Bool|String|Float|Double|List<[^>]+>|Map<[^>]+>)\\s+\\w+\\s*\\(");

    private static void c(String[] args) {
        if (args.length < 2 || "--help".equals(args[1]) || "-h".equals(args[1])) {
            System.out.println("usage: kof c <file.c> [--output <bin>] [--run]");
            System.out.println("  Compiles C subset to native ELF64 via KofCcompiler (no JVM)");
            return;
        }
        Path src = Path.of(args[1]);
        if (!Files.exists(src)) { System.err.println("not found: " + src); System.exit(1); return; }
        Path outDir = null;
        boolean run = false;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--output") && i + 1 < args.length) { outDir = Path.of(args[++i]); }
            else if (args[i].startsWith("--output=")) { outDir = Path.of(args[i].substring("--output=".length())); }
            else if (args[i].equals("--run")) { run = true; }
        }
        try {
            if (outDir == null) outDir = Files.createTempDirectory("kof-c-");
            else Files.createDirectories(outDir);
            var res = dev.kof.c.KofCCompiler.compile(src, outDir);
            if (!res.success()) { System.err.println(res.diagnostics()); System.exit(1); return; }
            System.out.println("KofC built " + res.binary());
            if (run) {
                ProcessBuilder pb = new ProcessBuilder(res.binary().toString());
                pb.inheritIO();
                Process p = pb.start();
                int ec = p.waitFor();
                System.exit(ec);
            } else if (outDir.toString().contains("kof-c-")) {
                // temp dir: run immediately for feedback
                ProcessBuilder pb = new ProcessBuilder(res.binary().toString());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes());
                int ec = p.waitFor();
                if (!out.isBlank()) System.out.print(out);
                if (ec != 0) System.err.println("exit code: " + ec);
            }
        } catch (Exception e) {
            System.err.println("kof c: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** Saldo de { } para agrupar blocos multilinha no KofScript. */
    private static int balance(String text) {
        int depth = 0;
        boolean inStr = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inStr) { if (c == '"') inStr = false; continue; }
            if (c == '"') inStr = true;
            else if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        return Math.max(depth, 0);
    }

    private static void check(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof check <file.kf|dir>"); System.exit(1); return; }
        if ("--help".equals(args[1]) || "-h".equals(args[1]) || "--version".equals(args[1])) {
            System.out.println("usage: kof check <file.kf|dir>");
            return;
        }
        Path src = Path.of(args[1]);
        if (!Files.exists(src)) { System.err.println("not found: " + src); System.exit(1); return; }
        List<Path> files = Files.isDirectory(src) ? collect(src) : List.of(src);
        if (files.isEmpty()) { System.out.println("no .kf files found"); return; }
        CompilerDriver driver = new CompilerDriver();
        boolean ok = true;
        int count = files.size();
        Path tmp;
        try { tmp = Files.createTempDirectory("kof-check-"); }
        catch (IOException e) { System.err.println("failed to create temp dir: " + e.getMessage()); System.exit(1); return; }
        // diretório = um módulo (mesmo modelo do build/run); arquivo único = isolado
        CompilationResult r = files.size() > 1
                ? driver.compileSources(files.stream()
                        .map(p -> p.toAbsolutePath().normalize()).distinct()
                        .collect(java.util.stream.Collectors.toList()), tmp, Target.JVM,
                        src.toAbsolutePath().normalize())
                : driver.compile(files.get(0), tmp, Target.JVM);
        for (Diagnostic d : r.diagnostics().getDiagnostics()) System.out.println(d.format());
        cleanup(tmp);
        if (!r.success()) ok = false;
        if (!ok) System.exit(1);
        System.out.println("checked " + count + " file(s) — no errors");
    }

    private static void info(String[] args) {
        boolean json = args.length > 1 && "--json".equals(args[1]);
        String installDir = System.getProperty("kof.install.dir", "");
        if (installDir.isEmpty()) {
            try {
                Path jar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                installDir = jar.toString();
            } catch (Exception e) {
                installDir = Path.of("").toAbsolutePath().toString();
            }
        }
        boolean embedded = Boolean.parseBoolean(System.getProperty("kof.embedded.jdk", "false"));
        String jvm = System.getProperty("java.vendor", "unknown") + " " + System.getProperty("java.version", "unknown");
        String channel = releaseChannel(KofVersion.version());

        if (json) {
            System.out.println("{\"kof\":\"" + jsonEscape(KofVersion.version())
                    + "\",\"releaseChannel\":\"" + channel
                    + "\",\"toolingApi\":" + KofVersion.toolingApi()
                    + ",\"os\":\"" + KofVersion.os()
                    + "\",\"arch\":\"" + KofVersion.arch()
                    + "\",\"target\":\"" + KofVersion.target()
                    + "\",\"jvm\":\"" + jsonEscape(jvm)
                    + "\",\"embeddedJdk\":" + embedded
                    + ",\"compiler\":\"" + KofVersion.compiler()
                    + "\",\"runtime\":\"" + KofVersion.runtime()
                    + "\",\"stdlib\":\"" + KofVersion.stdlib()
                    + "\",\"targets\":[\"jvm\",\"native\",\"js\"]"
                    + ",\"lsp\":true"
                    + ",\"editorSupport\":true"
                    + ",\"install\":\"" + jsonEscape(installDir) + "\"}");
            return;
        }

        System.out.println("Kof " + KofVersion.version());
        System.out.println("Release channel: " + channel);
        System.out.println("Tooling API: " + KofVersion.toolingApi());
        System.out.println("OS: " + KofVersion.os());
        System.out.println("Arch: " + KofVersion.arch());
        System.out.println("Target: " + KofVersion.target());
        System.out.println("JVM: " + jvm + (embedded ? " (embedded)" : ""));
        System.out.println("Compiler: " + KofVersion.compiler());
        System.out.println("Runtime: " + KofVersion.runtime());
        System.out.println("Stdlib: " + KofVersion.stdlib());
        System.out.println("Targets: jvm, native, js (alpha)");
        System.out.println("LSP: available");
        System.out.println("Editor support: available");
        System.out.println("Install: " + installDir);
    }

    private static String releaseChannel(String version) {
        if (version.endsWith("-alpha")) return "alpha";
        if (version.endsWith("-beta")) return "beta";
        if (version.endsWith("-rc")) return "release-candidate";
        return "stable";
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static void serve(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof serve <file.kf> [--port <port>] [--host <host>]");
        if ("--help".equals(args[1]) || "-h".equals(args[1]) || "--version".equals(args[1])) {
            System.out.println("usage: kof serve <file.kf> [--port <port>] [--host <host>]");
            return;
        } return; }
        Path file = Path.of(args[1]);
        if (!Files.exists(file)) { System.err.println("file not found: " + file); System.exit(1); return; }

        int port = 8080;
        String host = "0.0.0.0";
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
                i++;
            } else if (args[i].equals("--host") && i + 1 < args.length) {
                host = args[i + 1];
                i++;
            }
        }

        Path tempDir;
        try { tempDir = Files.createTempDirectory("kof-serve-"); }
        catch (IOException e) { System.err.println("failed to create temp dir: " + e.getMessage()); System.exit(1); return; }

        CompilerDriver driver = new CompilerDriver();
        // módulo = diretório do arquivo de entrada (irmãos .kf incluídos)
        java.util.List<Path> serveSources = new ArrayList<>();
        serveSources.add(file.toAbsolutePath().normalize());
        Path serveDir = file.toAbsolutePath().normalize().getParent();
        if (serveDir != null) {
            for (Path sib : collect(serveDir)) {
                Path abs = sib.toAbsolutePath().normalize();
                if (!abs.equals(serveSources.get(0)) && !serveSources.contains(abs)) serveSources.add(abs);
            }
        }
        CompilationResult result = driver.compileSources(serveSources, tempDir, Target.JVM);
        for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
        if (!result.success()) { cleanup(tempDir); System.exit(1); return; }

        String className = findMainClass(tempDir);
        if (System.getProperty("kof.trace") != null) {
            System.err.println("LAUNCH className=" + className + " dir=" + tempDir);
        }
        if (className == null) {
            System.err.println("no main class found");
            cleanup(tempDir);
            System.exit(1);
            return;
        }

        System.out.println("kof serve starting on " + host + ":" + port);
        System.out.println("compiling " + file + " ...");
        System.out.println("server ready at http://" + host + ":" + port);

        URLClassLoader handlerLoader;
        try {
            handlerLoader = new URLClassLoader(
                    new java.net.URL[]{tempDir.toUri().toURL()}, Main.class.getClassLoader());
        } catch (java.net.MalformedURLException e) {
            System.err.println("failed to load compiled classes: " + e.getMessage());
            cleanup(tempDir);
            System.exit(1);
            return;
        }

        try {
            Class<?> handlerClass = Class.forName(className, true, handlerLoader);
            boolean hasMain = false;
            try {
                handlerClass.getMethod("main", String[].class);
                hasMain = true;
            } catch (NoSuchMethodException ignored) {
            }
            if (hasMain) {
                // Kof-native web app (web.app() + app.listen()): the program
                // runs its own server. Legacy handle(...) apps have no main.
                handlerLoader.close();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("\nkof serve shutting down...");
                    if (servedProcess != null && servedProcess.isAlive()) {
                        servedProcess.destroy();
                    }
                    cleanup(tempDir);
                }));
                executeProcess(List.of(javaExecutable(),
                        "-Dkof.root=" + file.toAbsolutePath().normalize().getParent(),
                        "-cp", tempDir.toString(), className), tempDir);
                return;
            }
            dev.kof.compiler.KofHttpServer server = new dev.kof.compiler.KofHttpServer(
                    dev.kof.compiler.ReflectiveHandler.forClass(handlerClass));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nkof serve shutting down...");
                server.close();
                try { handlerLoader.close(); } catch (IOException ignored) {}
                cleanup(tempDir);
            }));

            System.out.println("listening for connections...");
            server.serve(host, port);
        } catch (ClassNotFoundException e) {
            System.err.println("handler class not found: " + e.getMessage());
            cleanup(tempDir);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("server error: " + e.getMessage());
            cleanup(tempDir);
            System.exit(1);
        }
    }
}
