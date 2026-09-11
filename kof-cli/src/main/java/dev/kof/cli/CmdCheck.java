package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

final class CmdCheck {

    private CmdCheck() {}

    static int run(String[] args) {
        return run(args, System.out, System.err);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            err.println("usage: kof check <file.kf|dir> [--json]");
            return 1;
        }

        boolean json = false;
        String pathArg = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg) || "--version".equals(arg)) {
                out.println("usage: kof check <file.kf|dir> [--json]");
                return 0;
            } else if ("--json".equals(arg)) {
                json = true;
            } else if (pathArg == null && !arg.startsWith("-")) {
                pathArg = arg;
            }
        }

        if (pathArg == null) {
            err.println("usage: kof check <file.kf|dir> [--json]");
            return 1;
        }

        Path src = Path.of(pathArg);
        if (!Files.exists(src)) {
            err.println("not found: " + src);
            return 1;
        }

        List<Path> files = Files.isDirectory(src) ? KofCliSupport.collect(src) : List.of(src);
        if (files.isEmpty()) {
            if (json) {
                out.println("{\"success\":true,\"filesChecked\":0,\"errorCount\":0,\"diagnostics\":[]}");
            } else {
                out.println("no .kf/.kof files found");
            }
            return 0;
        }

        CompilerDriver driver = new CompilerDriver();
        int count = files.size();
        Path tmp;
        try {
            tmp = Files.createTempDirectory("kof-check-");
        } catch (IOException e) {
            err.println("failed to create temp dir: " + e.getMessage());
            return 1;
        }

        CompilationResult r;
        try {
            r = files.size() > 1
                    ? driver.compileSources(files.stream()
                            .map(p -> p.toAbsolutePath().normalize()).distinct()
                            .collect(Collectors.toList()), tmp, Target.JVM,
                            src.toAbsolutePath().normalize())
                    : driver.compile(files.get(0), tmp, Target.JVM);
        } finally {
            KofCliSupport.cleanup(tmp);
        }

        List<Diagnostic> diagnostics = r.diagnostics().getDiagnostics();
        long errorCount = diagnostics.stream()
                .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
                .count();

        boolean success = r.success() && errorCount == 0;

        if (json) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":").append(success)
                    .append(",\"filesChecked\":").append(count)
                    .append(",\"errorCount\":").append(errorCount)
                    .append(",\"diagnostics\":[");
            for (int i = 0; i < diagnostics.size(); i++) {
                if (i > 0) sb.append(",");
                Diagnostic d = diagnostics.get(i);
                sb.append("{\"code\":\"").append(jsonEscape(d.code() != null ? d.code() : ""))
                        .append("\",\"file\":\"").append(jsonEscape(d.file() != null ? d.file() : ""))
                        .append("\",\"line\":").append(d.line())
                        .append(",\"column\":").append(d.column())
                        .append(",\"length\":").append(d.length())
                        .append(",\"severity\":\"").append(d.severity() != null ? d.severity().name() : "ERROR")
                        .append("\",\"message\":\"").append(jsonEscape(d.message() != null ? d.message() : ""))
                        .append("\"}");
            }
            sb.append("]}");
            out.println(sb.toString());
        } else {
            for (Diagnostic d : diagnostics) {
                out.println(d.format());
            }
            if (success) {
                out.println("checked " + count + " file(s) — no errors");
            }
        }

        return success ? 0 : 1;
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
