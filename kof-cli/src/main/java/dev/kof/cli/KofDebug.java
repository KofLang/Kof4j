package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * KofDebug — Debug Adapter Protocol (DAP) server for Kof.
 *
 * The user debugs Kof source, never bytecode: the program is compiled with
 * debug metadata (SourceFile + LineNumberTable), launched with JDWP, and
 * driven through the raw JDWP protocol by {@link JdwpClient} (no jdk.jdi
 * dependency — self-contained tooling).
 *
 * MVP requests: initialize, launch, setBreakpoints, configurationDone,
 * continue, threads, stackTrace, scopes, variables, disconnect.
 */
final class KofDebug {

    private KofDebug() {
    }

    private static final String USAGE =
            "usage: kof debug [--dap] [--attach PORT|PID] [--target jvm|native] [--break <line>]... [--output <dir>] <file.kf>";

    public static int run(String[] args) {
        if (args.length < 2) {
            System.err.println(USAGE);
            return 1;
        }
        String target = "jvm";
        boolean dap = false;
        Path out = null;
        List<Integer> breaks = new ArrayList<>();
        Integer attach = null; // X7-5: porta JDWP (jvm) ou PID (gdb -p)
        Path file = null;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--attach")) {
                if (i + 1 >= args.length) {
                    System.err.println("debug: --attach requires a port (jvm) or pid (native)");
                    return 1;
                }
                try {
                    attach = Integer.parseInt(args[++i]);
                } catch (NumberFormatException bad) {
                    System.err.println("debug: --attach wants a number, got '" + args[i] + "'");
                    return 1;
                }
            } else if (a.equals("--target") || a.equals("--break") || a.equals("--output")) {
                if (i + 1 >= args.length) {
                    System.err.println("debug: " + a + " requires a value (" + USAGE + ")");
                    return 1;
                }
                String v = args[++i];
                switch (a) {
                    case "--target" -> target = v;
                    case "--output" -> out = Path.of(v);
                    case "--break" -> {
                        Integer line = parseBreakLine(v);
                        if (line == null) return 1;
                        breaks.add(line);
                    }
                    default -> {
                    }
                }
            } else if (a.equals("--dap")) {
                dap = true;
            } else if (a.startsWith("-")) {
                // R6 strictness (CliFlagStrictnessTest): a typo must not be silently ignored.
                System.err.println("debug: unknown flag: " + a + " (" + USAGE + ")");
                return 1;
            } else if (file == null) {
                file = Path.of(a);
            } else {
                System.err.println("debug: unexpected argument: " + a + " (" + USAGE + ")");
                return 1;
            }
        }
        if (file == null) {
            System.err.println("debug: missing file (" + USAGE + ")");
            return 1;
        }
        if (!Files.exists(file)) {
            System.err.println("file not found: " + file);
            return 1;
        }
        String extErr = KofCliSupport.unsupportedSourceExtension("debug", file);
        if (extErr != null) {
            System.err.println(extErr);
            return 1;
        }
        if (target.equals("native")) {
            if (dap) {
                if (!breaks.isEmpty() || out != null) {
                    System.err.println("debug: --break/--output only apply to the native gdb console"
                            + " (not --dap)");
                    return 1;
                }
                try {
                    new KofDebugNativeDap(file.toAbsolutePath(), attach).run();
                    return 0;
                } catch (Exception e) {
                    System.err.println("kof debug native (dap): " + e.getMessage());
                    return 1;
                }
            }
            return debugNative(file, out, breaks, attach);
        }
        if (!breaks.isEmpty() || out != null) {
            System.err.println("debug: --break/--output only apply to --target native");
            return 1;
        }
        if (target.equals("js")) {
            System.err.println("debug js: honest gap — the JS target runs on the EMBEDDED engine"
                    + " (there is no node/inspector to attach to). Roadmap §19.5 face 7 stays open.");
            return 1;
        }
        if (!target.equals("jvm")) {
            System.err.println("debug: unknown --target '" + target + "' (jvm|native; js = honest gap;"
                    + " android = packaging, not a debug target)");
            return 1;
        }
        try {
            new KofDebugJvmSession(file, attach).run();
            return 0;
        } catch (Exception e) {
            System.err.println("kof debug: " + e.getMessage());
            if (System.getenv("KOF_DEBUG_TRACE") != null) {
                e.printStackTrace(); // diagnostico cirurgico (R6): o stderr NAO e o canal DAP
            }
            return 1;
        }
    }

    /**
     * X7-3 (face NATIVE do debug, fase 6 do §19.5): o Kof NAO reimplementa um
     * debugger — ele constrói o ELF com DWARF (line table + DIEs do X7-1/X7-2,
     * on por default) e delega ao gdb do alvo, apontando-o para o diretório da
     * FONTE Kof (o `.file` do DWARF é nome relativo; sem o `directory`, o gdb
     * mostra asm). Intenção no comando, mecanismo no platform — o usuario escreve
     * `break Main.kf:2` na fonte, nunca no mangle. O executavel do gdb e
     * resolvido por `KOF_GDB` (override de teste/ambiente; padrao da casa:
     * `KOF_PUBLISH_API`/`KOF_CROSS_SYSROOT`), senao `gdb`.
     */
    private static int debugNative(Path file, Path outDir, List<Integer> breaks, Integer attachPid) {
        NativeBuild built = null;
        try {
            String gdb = firstNonEmpty(System.getenv("KOF_GDB"), "gdb");
            if (attachPid != null) {
                // X7-5: attach ao processo NATIVO vivo (kof servido vivo); sem build, sem kill.
                ProcessBuilder apb = new ProcessBuilder(gdb, "-q",
                        "-iex", "set pagination off",
                        "-iex", "set debuginfod enabled off",
                        "-iex", "directory " + file.toAbsolutePath().getParent(),
                        "-p", attachPid.toString());
                apb.inheritIO();
                try {
                    return apb.start().waitFor();
                } catch (java.io.IOException spawnFail) {
                    System.err.println("debug native: '" + gdb + "' not available — install gdb"
                            + " (roadmap §19.5 fase 6: gdb front-end over the Kof ELF)");
                    return 1;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return 1;
                }
            }
            built = buildNativeElf(file, outDir);
            if (built == null) {
                return 1;
            }
            Path bin = built.bin();
            // `--break` = sessao batch (scriptavel/CI): para na LINHA Kof e imprime
            // o backtrace. Sem ele = console interativo do gdb (o usuario dirige).
            boolean batch = !breaks.isEmpty();
            List<String> cmd = new ArrayList<>();
            cmd.add(gdb);
            cmd.add("-q");
            if (batch) cmd.add("-batch");
            cmd.add("-iex"); cmd.add("set pagination off");
            cmd.add("-iex"); cmd.add("set debuginfod enabled off");
            if (batch) {
                cmd.add("-iex"); cmd.add("set confirm off");
                cmd.add("-iex"); cmd.add("directory " + file.toAbsolutePath().getParent());
                cmd.add("-ex"); cmd.add("file " + bin);
                for (int line : breaks) {
                    cmd.add("-ex"); cmd.add("break " + file.getFileName() + ":" + line);
                }
                cmd.add("-ex"); cmd.add("run");
                cmd.add("-ex"); cmd.add("bt");
            } else {
                cmd.add("-iex"); cmd.add("directory " + file.toAbsolutePath().getParent());
                cmd.add(bin.toString());
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (batch) pb.redirectErrorStream(true);
            else pb.inheritIO();
            try {
                Process p = pb.start();
                if (!batch) return p.waitFor();
                String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                System.out.print(output);
                System.out.flush();
                if (!p.waitFor(120, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    System.err.println("debug native: gdb did not finish in 120s");
                    return 1;
                }
                return p.exitValue();
            } catch (java.io.IOException spawnFail) {
                System.err.println("debug native: '" + gdb + "' not available — install gdb"
                        + " (roadmap §19.5 fase 6: gdb front-end over the Kof ELF; the DWARF"
                        + " is already emitted by the compiler)");
                return 1;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return 1;
            }
        } catch (Exception e) {
            System.err.println("kof debug native: " + e.getMessage());
            return 1;
        } finally {
            if (built != null && outDir == null) {
                KofCliSupport.cleanup(built.dir());
            }
        }
    }

    /** ELF Kof construido com DWARF em um diretorio temporario (console mode e DAP session). */
    record NativeBuild(Path dir, Path bin) {
    }

    static NativeBuild buildNativeElf(Path file) throws IOException {
        return buildNativeElf(file, null);
    }

    /**
     * `outDir` != null = o ELF fica no diretorio do usuario (`--output`, reuso entre
     * sessoes de debug); null = diretorio temporario, limpo pelo chamador.
     */
    static NativeBuild buildNativeElf(Path file, Path outDir) throws IOException {
        boolean temp = outDir == null;
        Path out = temp ? Files.createTempDirectory("kof-debug-native-") : outDir;
        CompilerDriver driver = new CompilerDriver();
        driver.setDebugInfoEnabled(true);
        CompilationResult r = driver.compile(file.toAbsolutePath(), out, Target.NATIVE);
        if (!r.success()) {
            r.diagnostics().getDiagnostics().forEach(d -> System.err.println(d.format()));
            if (temp) KofCliSupport.cleanup(out);
            return null;
        }
        Path bin = out.resolve("Default").resolve("Main");
        if (!Files.exists(bin)) {
            System.err.println("debug native: no ELF produced (native toolchain missing on this host)");
            if (temp) KofCliSupport.cleanup(out);
            return null;
        }
        return new NativeBuild(out, bin);
    }

    private static Integer parseBreakLine(String value) {
        try {
            int line = Integer.parseInt(value.trim());
            if (line < 1) throw new NumberFormatException();
            return line;
        } catch (NumberFormatException e) {
            System.err.println("debug: --break expects a line number (got '" + value + "')");
            return null;
        }
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }


    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        return sb.length() == 0 && c == -1 ? null : sb.toString();
    }

    static void writeMessage(OutputStream out, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    static String javaExecutable() { // package-private p/ KofDebugJvmSession (X7-5)
        String javaHome = System.getProperty("java.home");
        return javaHome + "/bin/java";
    }
}