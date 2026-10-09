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
            System.out.println("       kof script --repl  (or: kof repl)");
            System.out.println("       --watch   re-runs when the file is saved");
            System.out.println("       --inspect prints IR stats without executing");
            return 0;
        }
        if ("--repl".equals(args[1])) return repl(args);
        String sourceArg = args[1];
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
            else if (a.equals("--")) {
                for (int j = i + 1; j < args.length; j++) progArgs.add(args[j]);
                break;
            }
            else if (a.startsWith("-")) { System.err.println("unknown option: " + a); return 1; }
            else progArgs.add(a);
        }
        // Modo inline (zero-state): o argumento não é um path existente, mas
        // parece fonte Kof → é um programa KofScript efêmero, não um arquivo.
        boolean inline = !Files.exists(Path.of(sourceArg));
        if (inline && !looksLikeKofSource(sourceArg)) {
            System.err.println("not found: " + sourceArg);
            return 1;
        }
        if (inline && watch) {
            System.err.println("script: --watch needs a file, not inline code");
            return 1;
        }
        if (inspect) {
            if (inline) { System.err.println("script: --inspect needs a file, not inline code"); return 1; }
            Path src = Path.of(sourceArg);
            String extErr = KofCliSupport.unsupportedSourceExtension("script", src);
            if (extErr != null) { System.err.println(extErr); return 1; }
            try {
                System.out.println(dev.kof.script.KofScript.inspect(src));
                return 0;
            } catch (Exception e) { System.err.println("inspect: " + e.getMessage()); return 1; }
        }
        if (inline) {
            try {
                var r = dev.kof.script.KofScript.runSource(sourceArg, target, progArgs.toArray(new String[0]));
                if (!r.stdout().isBlank()) System.out.print(r.stdout());
                if (!r.stderr().isBlank()) System.err.print(r.stderr());
                return r.exitCode();
            } catch (Exception e) {
                System.err.println("kof script: " + e.getMessage());
                return 1;
            }
        }
        Path src = Path.of(sourceArg);
        String extErr = KofCliSupport.unsupportedSourceExtension("script", src);
        if (extErr != null) { System.err.println(extErr); return 1; }
        if (watch) return watchScript(src, target, progArgs.toArray(new String[0]));
        // Pipeline ÚNICA (.kf/.kof/.ks): o texto passa por prepareSource; se já
        // declara main() roda o arquivo original (preserva irmãos e file:line),
        // senão o top-level é o programa e o texto é materializado num .kf.
        try {
            String content = Files.readString(src);
            boolean hasMain = dev.kof.script.KofScript.hasMainDeclaration(content);
            if (hasMain) {
                return emit(dev.kof.script.KofScript.runFile(src, target, progArgs.toArray(new String[0])));
            }
            Path tmp = Files.createTempDirectory("kof-script-");
            try {
                String kfName = src.getFileName().toString();
                if (kfName.endsWith(".ks") || kfName.endsWith(".kof")) {
                    kfName = kfName.substring(0, kfName.lastIndexOf('.')) + ".kf";
                }
                if (!kfName.endsWith(".kf")) kfName = "Script.kf";
                Path kf = tmp.resolve(kfName);
                Files.writeString(kf, dev.kof.script.KofScript.prepareSource(content));
                return emit(dev.kof.script.KofScript.runFile(kf, target, progArgs.toArray(new String[0])));
            } finally {
                KofCliSupport.cleanup(tmp);
            }
        } catch (Exception e) {
            System.err.println("kof script: " + e.getMessage());
            return 1;
        }
    }

    /** Emite o RunResult do KofScript no stdout/stderr e devolve o exit code. */
    private static int emit(dev.kof.script.KofScript.RunResult r) {
        if (!r.stdout().isBlank()) System.out.print(r.stdout());
        if (!r.stderr().isBlank()) System.err.print(r.stderr());
        return r.success() ? r.exitCode() : 1;
    }

    /**
     * Heurística do modo inline: o argumento parece código Kof (e não um nome
     * de arquivo digitado errado)? Exige um marcador sintático — parêntese,
     * chave, ponto-e-vírgula ou quebra de linha.
     */
    private static boolean looksLikeKofSource(String s) {
        return s.indexOf('(') >= 0 || s.indexOf('{') >= 0
                || s.indexOf(';') >= 0 || s.indexOf('\n') >= 0;
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
                            if (!r.success()) {
                                if (!r.stderr().isBlank()) System.err.print(r.stderr());
                                if (!r.stdout().isBlank()) System.out.print(r.stdout());
                            } else {
                                if (!r.stdout().isBlank()) System.out.print(r.stdout());
                                if (!r.stderr().isBlank()) System.err.print(r.stderr());
                            }
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
}
