package dev.kof.cli;

import dev.kof.compiler.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Main {
    public static void main(String[] args) {
        if (args.length == 0) { printUsage(); return; }
        switch (args[0]) {
            case "build" -> CmdBuild.run(args);
            case "run" -> CmdRun.run(args);
            case "serve" -> CmdServe.run(args);
            case "check" -> check(args);
            case "test" -> CmdTest.run(args);
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
            case "script" -> System.exit(CmdScript.run(args));
            case "repl" -> System.exit(CmdScript.repl(args));
            case "init" -> System.exit(init(args));
            case "deps" -> System.exit(Deps.run(args));
            case "c" -> c(args);
            case "fmt" -> System.exit(Fmt.run(args));
            case "config" -> config(args);
            case "version" -> System.out.println("kof " + KofVersion.version());
            default -> { System.err.println("unknown: " + args[0]); printUsage(); }
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
                target = KofCliSupport.parseTarget(args[++i]);
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
            List<Path> files = Files.isDirectory(src) ? KofCliSupport.collect(src) : List.of(src);
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
            KofCliSupport.cleanup(tmp);
        }
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

    private static void check(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof check <file.kf|dir>"); System.exit(1); return; }
        if ("--help".equals(args[1]) || "-h".equals(args[1]) || "--version".equals(args[1])) {
            System.out.println("usage: kof check <file.kf|dir>");
            return;
        }
        Path src = Path.of(args[1]);
        if (!Files.exists(src)) { System.err.println("not found: " + src); System.exit(1); return; }
        List<Path> files = Files.isDirectory(src) ? KofCliSupport.collect(src) : List.of(src);
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
        KofCliSupport.cleanup(tmp);
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
}
