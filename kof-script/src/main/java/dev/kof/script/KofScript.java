package dev.kof.script;

import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.KofInterpretException;
import dev.kof.compiler.KofInterpreter;
import dev.kof.compiler.Target;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * KofScript — Fase 6 MVP.
 * <p>
 * Direct execution for Kof: compiles Kof source to JVM bytecode in a
 * temporary directory and runs it via {@code java -cp}. This reuses the
 * shared frontend (Lexer → Parser → AST → SemanticAnalyzer → Kof IR) and
 * the existing JvmBackend — no second compiler.
 * <p>
 * The MVP is intentionally thin: it is the foundation for the future
 * REPL / incremental execution / JIT. The public API is stable:
 * {@link #eval(String)}, {@link #runFile(Path)}.
 */
public final class KofScript {

    private KofScript() {}

    public record RunResult(int exitCode, String stdout, String stderr, boolean success) {}

    /**
     * Evaluates a Kof snippet as a program (wraps in main if needed).
     * The snippet may be a full program (with main) or just statements.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, RunResult> evalCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, FileCacheEntry> fileCache = new java.util.concurrent.ConcurrentHashMap<>();
    private record FileCacheEntry(long lastModified, long size, String hash, RunResult result) {}

    public static RunResult eval(String code) throws IOException {
        return eval(code, Target.JVM);
    }

    public static RunResult eval(String code, Target target) throws IOException {
        // KofScript = Kof puro executado direto: sem main() declarado, os
        // statements do topo viram main() (wrapKsWithGlobals). Nenhum sugar
        // de outra linguagem (let/const/async/fn NÃO existem).
        String wrappedForEval = code.contains("main()") ? code : wrapPureKof(code);
        String key = target + ":" + wrappedForEval.hashCode() + ":" + wrappedForEval.length();
        RunResult cached = evalCache.get(key);
        if (cached != null) return cached;
        Path tmp = Files.createTempDirectory("kofscript");
        try {
            Path src = tmp.resolve("Main.kf");
            Files.writeString(src, wrappedForEval);
            RunResult r = runFile(src, target);
            if (r.success()) evalCache.put(key, r);
            // LRU bound 64
            if (evalCache.size() > 64) evalCache.clear();
            return r;
        } finally {
            deleteRecursively(tmp);
        }
    }

    /**
     * KofScript = Kof puro executado direto. O único serviço do wrapper é o
     * modelo de execução de script: statements no topo viram `main()`, e
     * `var`/`val` no topo viram campos estáticos de `KofScriptGlobals`
     * (Kof não tem variável top-level). NENHUM sugar de outra linguagem:
     * `let`/`const`/`async`/`fn` NÃO existem — código com essas palavras
     * falha no parser Kof com o diagnóstico normal (R6: nunca silencioso).
     */
    public static String wrapPureKof(String code) {
        java.util.List<String> gNames = new java.util.ArrayList<>();
        java.util.List<String> gTypes = new java.util.ArrayList<>();
        java.util.List<String> gInits = new java.util.ArrayList<>();
        StringBuilder decls = new StringBuilder();
        StringBuilder stmts = new StringBuilder();
        java.util.regex.Pattern varPat = java.util.regex.Pattern.compile(
                "^(var|val)\\s+(\\w+)(?:\\s*:\\s*([^=]+))?\\s*=\\s*(.+)$");
        StringBuilder cur = new StringBuilder();
        for (String raw : code.split("\n", -1)) {
            String t = raw.strip();
            if (t.isEmpty() || t.startsWith("//")) { if (cur.length() > 0) cur.append(raw).append('\n'); continue; }
            if (cur.length() == 0) {
                java.util.regex.Matcher m = varPat.matcher(t);
                if (m.matches() && !t.contains("{") && !t.contains("}")) {
                    gNames.add(m.group(2));
                    gTypes.add(m.group(3) != null ? m.group(3).strip() : null);
                    gInits.add(m.group(4).strip().replaceAll(";$", ""));
                    continue;
                }
            }
            cur.append(raw).append('\n');
            long opens = cur.toString().chars().filter(ch -> ch == '{').count();
            long closes = cur.toString().chars().filter(ch -> ch == '}').count();
            if (opens > closes) continue;
            String block = cur.toString().strip();
            cur.setLength(0);
            if (isTopLevelDecl(block)) decls.append(block).append('\n');
            else stmts.append(block).append('\n');
        }
        if (cur.length() > 0) {
            String block = cur.toString().strip();
            if (!block.isEmpty()) {
                if (isTopLevelDecl(block)) decls.append(block); else stmts.append(block);
            }
        }
        if (gNames.isEmpty() && decls.length() == 0) {
            return "main() {\n" + code + "\n}\n";
        }
        StringBuilder prog = new StringBuilder();
        if (!gNames.isEmpty()) {
            java.util.LinkedHashMap<String,String> typeMap = new java.util.LinkedHashMap<>();
            java.util.LinkedHashMap<String,String> initMap = new java.util.LinkedHashMap<>();
            for (int i = 0; i < gNames.size(); i++) { typeMap.put(gNames.get(i), gTypes.get(i)); initMap.put(gNames.get(i), gInits.get(i)); }
            prog.append("class KofScriptGlobals {\n");
            for (String n : initMap.keySet()) {
                String ty = typeMap.get(n);
                String init = initMap.get(n);
                prog.append("  static ").append(ty != null ? ty : inferKofType(init)).append(" ").append(n).append(" = ").append(init).append("\n");
            }
            prog.append("}\n");
            String ds = decls.toString(), ss = stmts.toString();
            for (String n : initMap.keySet()) {
                ds = ds.replaceAll("\\b" + java.util.regex.Pattern.quote(n) + "\\b", "KofScriptGlobals." + n);
                ss = ss.replaceAll("\\b" + java.util.regex.Pattern.quote(n) + "\\b", "KofScriptGlobals." + n);
            }
            decls = new StringBuilder(ds); stmts = new StringBuilder(ss);
        }
        prog.append(decls);
        if (stmts.length() > 0) prog.append("main() {\n").append(stmts).append("\n}\n");
        else if (decls.length() == 0) prog.append("main() {\nprintln(KofScriptGlobals.").append(gNames.get(gNames.size()-1)).append(")\n}\n");
        else prog.append("main() {}\n");
        return prog.toString();
    }

    private static final java.util.regex.Pattern TOP_TYPE_KW = java.util.regex.Pattern.compile(
            "^(class|interface|enum|record|entity)\\s");
    private static final java.util.regex.Pattern STMT_KW = java.util.regex.Pattern.compile(
            "^(if|while|for|switch|try|catch|finally|return|throw|assert|spawn|else|do|break|continue|var|val)\\b");
    private static final java.util.regex.Pattern FUNC_HEAD = java.util.regex.Pattern.compile(
            "^(?:[A-Za-z_][\\w.<>,?\\[\\]\\s]*\\s+)?[A-Za-z_]\\w*\\s*(?:<[^<>]*>)?\\s*\\(");

    /**
     * Classifica um bloco de topo como DECLARAÇÃO (classe/record/enum ou
     * função com corpo/`=`/`: Tipo`) vs STATEMENT. Conservador: chamadas com
     * literais nos parênteses (`println("x")`, `every(100) {…}`) e keywords de
     * statement nunca são declarações.
     */
    static boolean isTopLevelDecl(String block) {
        String t = block.strip();
        if (TOP_TYPE_KW.matcher(t).find()) return true;
        if (STMT_KW.matcher(t).find()) return false;
        java.util.regex.Matcher m = FUNC_HEAD.matcher(t);
        if (!m.find()) return false;
        int open = t.indexOf('(', m.end() - 1);
        int depth = 0, close = -1;
        for (int i = open; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) { close = i; break; } }
        }
        if (close < 0) return false;
        String params = t.substring(open + 1, close);
        if (params.matches(".*[0-9\"'].*")) return false;
        String rest = t.substring(close + 1).strip();
        return rest.startsWith("{") || rest.startsWith("=") || rest.startsWith(":");
    }

    private static String inferKofType(String init) {
        String t = init.strip();
        // string literal
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) return "String";
        if ("true".equals(t) || "false".equals(t)) return "Bool";
        if (t.matches("-?\\d+")) return "Int";
        if (t.matches("-?\\d*\\.\\d+([eE][+-]?\\d+)?")) return "Double";
        if (t.startsWith("0x") || t.startsWith("0X")) return "Int";
        // heuristic: contains quotes => String concatenation
        if (t.contains("\"") || t.contains("'")) return "String";
        // fallback to Int (most common) — explicit type in source is preferred
        return "Int";
    }


    public static RunResult runFile(Path sourceFile) throws IOException {
        return runFile(sourceFile, Target.JVM, new String[0]);
    }

    public static RunResult runFile(Path sourceFile, Target target) throws IOException {
        return runFile(sourceFile, target, new String[0]);
    }

    public static RunResult runFile(Path sourceFile, Target target, String[] programArgs) throws IOException {
        // Multi-file: if sourceFile is a directory, compile all .kf/.ks in it; if file, include shallow siblings
        java.util.List<Path> sources = collectSources(sourceFile);
        // File incremental cache: if single file hasn't changed, reuse
        Path abs = null;
        long fileLm = 0;
        long sz = 0;
        String fkey = null;
        String fhash = null;
        try {
            abs = sourceFile.toAbsolutePath().normalize();
            if (Files.isRegularFile(abs)) {
                fileLm = Files.getLastModifiedTime(abs).toMillis();
                sz = Files.size(abs);
                fkey = abs + "|" + target + "|" + sources.size();
                FileCacheEntry fe = fileCache.get(fkey);
                if (fe != null && fe.lastModified() == fileLm && fe.size() == sz) {
                    String content = Files.readString(abs);
                    String h = Integer.toString(content.hashCode());
                    if (h.equals(fe.hash())) return fe.result();
                    fhash = h;
                } else {
                    fhash = Integer.toString(Files.readString(abs).hashCode());
                }
            }
        } catch (Exception ignore) {}
        Path outDir = Files.createTempDirectory("kofscript-out");
        try {
            CompilerDriver driver = new CompilerDriver();
            // Materializa .ks como .kf (Kof puro: statements -> main(), var/val
            // de topo -> globals). Sem sugar de outra linguagem.
            Materialized mat = materialize(sources, sourceFile);
            try {
                if (target == Target.JVM) {
                    // KofScript = target de execução direta: interpreta a IR no
                    // mesmo frontend do compilador, sem emitir bytecode e sem
                    // fork de JVM (paridade por construção).
                    try {
                        KofInterpreter.Result ir = driver.interpret(mat.sources, mat.root, programArgs);
                        RunResult rr = new RunResult(ir.exitCode(), ir.stdout(), ir.stderr(),
                                ir.exitCode() == 0);
                        cacheFile(rr, abs, fkey, fhash, fileLm, sz);
                        return rr;
                    } catch (KofInterpretException e) {
                        StringBuilder sb = new StringBuilder();
                        e.diagnostics().getDiagnostics().forEach(d -> sb.append(d.format()).append("\n"));
                        return new RunResult(1, "", sb.toString(), false);
                    }
                }
                CompilationResult result = mat.sources.size() == 1
                        ? driver.compile(mat.sources.get(0), outDir, target)
                        : driver.compileSources(mat.sources, outDir, target, mat.root);
                if (!result.success()) {
                    StringBuilder sb = new StringBuilder();
                    result.diagnostics().getDiagnostics().forEach(d -> sb.append(d.format()).append("\n"));
                    return new RunResult(1, "", sb.toString(), false);
                }
                KofScript.RunResult rr = KofScriptExecutor.executeCompiled(outDir, target, programArgs);
                cacheFile(rr, abs, fkey, fhash, fileLm, sz);
                return rr;
            } finally {
                if (mat.tmpDir != null) deleteRecursively(mat.tmpDir);
            }
        } finally {
            deleteRecursively(outDir);
        }
    }

    private record Materialized(java.util.List<Path> sources, Path root, Path tmpDir) {}

    /**
     * Caminho COMPILADO (fallback): emite bytecode e executa (in-memory ou
     * fork). Mantido vivo para paridade e para targets não-JVM; o teste de
     * paridade compara runFile (interpretado) vs runFileCompiled (JVM real).
     */
    public static RunResult runFileCompiled(Path sourceFile, Target target, String[] programArgs)
            throws IOException {
        java.util.List<Path> sources = collectSources(sourceFile);
        Path outDir = Files.createTempDirectory("kofscript-compiled");
        try {
            CompilerDriver driver = new CompilerDriver();
            Materialized mat = materialize(sources, sourceFile);
            try {
                CompilationResult result = mat.sources.size() == 1
                        ? driver.compile(mat.sources.get(0), outDir, target)
                        : driver.compileSources(mat.sources, outDir, target, mat.root);
                if (!result.success()) {
                    StringBuilder sb = new StringBuilder();
                    result.diagnostics().getDiagnostics().forEach(d -> sb.append(d.format()).append("\n"));
                    return new RunResult(1, "", sb.toString(), false);
                }
                return KofScriptExecutor.executeCompiled(outDir, target, programArgs);
            } finally {
                if (mat.tmpDir != null) deleteRecursively(mat.tmpDir);
            }
        } finally {
            deleteRecursively(outDir);
        }
    }

    /** Reescreve cada `.ks` como `.kf` (wrapPureKof) num diretório temporário. */
    private static Materialized materialize(java.util.List<Path> sources, Path sourceFile)
            throws IOException {
        java.util.List<Path> kfSources = new java.util.ArrayList<>();
        Path tmpKsDir = null;
        for (Path p : sources) {
            if (p.toString().endsWith(".ks")) {
                if (tmpKsDir == null) tmpKsDir = Files.createTempDirectory("kofscript-ks");
                String content = Files.readString(p);
                String wrapped = content.contains("main()") ? content : wrapPureKof(content);
                Path kf = tmpKsDir.resolve(p.getFileName().toString().replace(".ks", ".kf"));
                Files.writeString(kf, wrapped);
                kfSources.add(kf);
            } else {
                kfSources.add(p);
            }
        }
        Path root = sourceFile.toAbsolutePath().normalize().getParent();
        if (root == null) root = Path.of(".").toAbsolutePath();
        if (Files.isDirectory(sourceFile)) root = sourceFile.toAbsolutePath().normalize();
        return new Materialized(kfSources, root, tmpKsDir);
    }

    private static void cacheFile(RunResult rr, Path abs, String fkey, String fhash,
                                  long fileLm, long sz) {
        if (rr.success() && abs != null && fkey != null && fhash != null) {
            fileCache.put(fkey, new FileCacheEntry(fileLm, sz, fhash, rr));
            if (fileCache.size() > 64) fileCache.clear();
        }
    }

    public static String inspect(Path sourceFile) throws IOException {
        Path outDir = Files.createTempDirectory("kofscript-inspect");
        try {
            CompilerDriver driver = new CompilerDriver();
            java.util.concurrent.atomic.AtomicReference<String> stats = new java.util.concurrent.atomic.AtomicReference<>("");
            driver.setIRObserver((dev.kof.compiler.IRStatistics s) -> {
                stats.set("classes: " + s.classes() + " opsBefore: " + s.opsBefore() + " opsAfter: " + s.opsAfter() + " removed: " + s.opsRemoved() + " (" + s.reductionPct() + "%)\n"
                        + s.methods().stream().map(m -> "  " + m.className() + "." + m.methodName() + ": " + m.opsBefore() + "->" + m.opsAfter() + " (" + m.removed() + " removed)").reduce("", (a,b) -> a + "\n" + b));
            });
            java.util.List<Path> sources = collectSources(sourceFile);
            Path tmpKsDir = null;
            java.util.List<Path> kfSources = new java.util.ArrayList<>();
            for (Path p : sources) {
                if (p.toString().endsWith(".ks")) {
                    if (tmpKsDir == null) tmpKsDir = Files.createTempDirectory("kofscript-ks-inspect");
                    String content = Files.readString(p);
                    String wrapped = content.contains("main()") ? content : wrapPureKof(content);
                    Path kf = tmpKsDir.resolve(p.getFileName().toString().replace(".ks", ".kf"));
                    Files.writeString(kf, wrapped);
                    kfSources.add(kf);
                } else kfSources.add(p);
            }
            Path root = Files.isDirectory(sourceFile) ? sourceFile.toAbsolutePath().normalize() : sourceFile.toAbsolutePath().normalize().getParent();
            if (root == null) root = Path.of(".").toAbsolutePath();
            var res = kfSources.size() == 1 ? driver.compile(kfSources.get(0), outDir, Target.JVM) : driver.compileSources(kfSources, outDir, Target.JVM, root);
            if (tmpKsDir != null) deleteRecursively(tmpKsDir);
            if (!res.success()) {
                StringBuilder sb = new StringBuilder();
                res.diagnostics().getDiagnostics().forEach(d -> sb.append(d.format()).append("\n"));
                return sb.toString();
            }
            return stats.get() != null ? stats.get() : "no stats";
        } finally {
            deleteRecursively(outDir);
        }
    }

    private static java.util.List<Path> collectSources(Path sourceFile) throws IOException {
        Path abs = sourceFile.toAbsolutePath().normalize();
        if (Files.isDirectory(abs)) {
            try (var s = Files.list(abs)) {
                return s.filter(p -> p.toString().endsWith(".kf") || p.toString().endsWith(".ks"))
                        .sorted().toList();
            }
        }
        // Single file: include shallow siblings with same extension handling
        java.util.List<Path> out = new java.util.ArrayList<>();
        out.add(abs);
        Path parent = abs.getParent();
        if (parent != null) {
            try (var s = Files.list(parent)) {
                s.filter(p -> !p.equals(abs) && (p.toString().endsWith(".kf") || p.toString().endsWith(".ks")))
                 .sorted().forEach(out::add);
            } catch (IOException ignore) {}
        }
        return out;
    }

    /**
     * Simple REPL: reads lines from stdin, evals until "exit".
     * Incremental: each line is appended to the history and re-evaluated
     * as a whole program (MVP — future will be incremental IR).
     */
    public static void repl(InputStream in, PrintStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder history = new StringBuilder();
        out.println("KofScript REPL 0.1.2-beta — type 'exit' to quit");
        while (true) {
            out.print("kof> ");
            out.flush();
            String line = reader.readLine();
            if (line == null || "exit".equals(line.trim())) break;
            if (line.isBlank()) continue;
            history.append(line).append("\n");
            RunResult r = eval(history.toString());
            if (!r.success()) {
                out.println("error: " + r.stderr().trim());
                // rollback last line on error
                int lastNl = history.lastIndexOf(line);
                if (lastNl >= 0) history.setLength(lastNl);
            } else {
                out.print(r.stdout());
            }
        }
    }

    private static void deleteRecursively(Path dir) {
        try {
            if (!Files.exists(dir)) return;
            Files.walk(dir).sorted((a,b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignore) {}
            });
        } catch (IOException ignore) {}
    }
}
