package dev.kof.cli;

import dev.kof.compiler.Target;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.List;

/**
 * kof script — execução direta de KofScript (.ks/.kf): para .ks,
 * declarações (fn/enum/class) viram top-level e o resto cai num main()
 * sintético; para .kf compila direto. Suporta --target jvm|native|js
 * e diagnostics com file:line via Diagnostic.format(). Também hospeda
 * kof repl e o --watch. Extraído de Main (REFACTOR-500 Fase 8).
 */
final class CmdScript {

    private CmdScript() {
    }

    static int run(String[] args) {
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
            if (a.startsWith("--target=")) target = KofCliSupport.parseTarget(a.substring("--target=".length()));
            else if (a.equals("--target") && i + 1 < args.length) target = KofCliSupport.parseTarget(args[++i]);
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
            Files.writeString(kf, dev.kof.script.KofScript.toKofSyntax(program.toString()));
            var r = dev.kof.script.KofScript.runFile(kf, target, progArgs.toArray(new String[0]));
            // Propaga diagnostics com file:line (KofScript já usa d.format())
            if (!r.success()) {
                if (!r.stderr().isBlank()) System.err.print(r.stderr());
                if (!r.stdout().isBlank()) System.out.print(r.stdout());
                KofCliSupport.cleanup(tmp);
                return 1;
            }
            if (!r.stdout().isBlank()) System.out.print(r.stdout());
            if (!r.stderr().isBlank()) System.err.print(r.stderr());
            KofCliSupport.cleanup(tmp);
            return r.exitCode();
        } catch (Exception e) {
            System.err.println("kof script: " + e.getMessage());
            return 1;
        }
    }

    static int repl(String[] args) {
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
}
