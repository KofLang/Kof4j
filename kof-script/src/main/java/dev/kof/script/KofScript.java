package dev.kof.script;

import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Target;

import java.io.*;
import java.nio.file.*;
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
        String pre = preprocess(code);
        // top-level let/const as globals (KofScriptGlobals) — fast path for eval single snippet
        String wrappedForEval = pre.contains("main()") ? pre : wrapKsWithGlobals(pre);
        String key = target + ":" + wrappedForEval.hashCode() + ":" + wrappedForEval.length();
        RunResult cached = evalCache.get(key);
        if (cached != null) return cached;
        Path tmp = Files.createTempDirectory("kofscript");
        try {
            Path src = tmp.resolve("Main.kf");
            Files.writeString(src, toKofSyntax(wrappedForEval));
            RunResult r = runFile(src, target);
            if (r.success()) evalCache.put(key, r);
            // LRU bound 64
            if (evalCache.size() > 64) evalCache.clear();
            return r;
        } finally {
            deleteRecursively(tmp);
        }
    }

    private static String wrapKsWithGlobals(String pre) {
        // Extract top-level let/var/val/const as globals -> class KofScriptGlobals
        // Properly separates top-level decls (fn/class/record/enum) from stmts so that
        // `let x=5; fn foo(){println(x)}; foo()` generates:
        //   class KofScriptGlobals { static Int x=5 }
        //   fn foo(){println(KofScriptGlobals.x)}
        //   main(){ foo() }
        java.util.List<String> gNames = new java.util.ArrayList<>();
        java.util.List<String> gTypes = new java.util.ArrayList<>();
        java.util.List<String> gInits = new java.util.ArrayList<>();
        StringBuilder decls = new StringBuilder();
        StringBuilder stmts = new StringBuilder();
        String[] lines = pre.split("\n");
        java.util.regex.Pattern letPat = java.util.regex.Pattern.compile("^(?:let|var|val|const)\\s+(\\w+)(?:\\s*:\\s*([^=]+))?\\s*=\\s*(.+)$");
        StringBuilder cur = new StringBuilder();
        boolean curIsDecl = false;
        for (String raw : lines) {
            String t = raw.strip();
            String tNorm = t.replaceFirst("^async\\s+", "");
            if (t.isEmpty() || t.startsWith("//")) continue;
            cur.append(raw).append('\n');
            boolean declStart = tNorm.startsWith("fn ") || tNorm.startsWith("enum ") || tNorm.startsWith("class ") || tNorm.startsWith("record ");
            if (cur.length() == raw.length() + 1) curIsDecl = declStart;
            if (cur.toString().chars().filter(ch -> ch == '{').count() > cur.toString().chars().filter(ch -> ch == '}').count()) continue;
            String block = cur.toString().strip();
            String blockTrim = block.strip();
            java.util.regex.Matcher mLet = letPat.matcher(blockTrim);
            boolean isTopLet = !curIsDecl && mLet.matches() && !blockTrim.contains("{") && !blockTrim.contains("}");
            if (isTopLet) {
                gNames.add(mLet.group(1));
                gTypes.add(mLet.group(2) != null ? mLet.group(2).strip() : null);
                gInits.add(mLet.group(3).strip().replaceAll(";$", ""));
            } else if (curIsDecl) decls.append(block).append('\n');
            else stmts.append(block).append('\n');
            cur.setLength(0);
        }
        if (!cur.isEmpty()) {
            String block = cur.toString().strip();
            String bt = block.strip();
            java.util.regex.Matcher mLet2 = letPat.matcher(bt);
            boolean isTopLet2 = mLet2.matches() && !bt.contains("{") && !bt.contains("}");
            if (isTopLet2) { gNames.add(mLet2.group(1)); gTypes.add(mLet2.group(2)!=null?mLet2.group(2).strip():null); gInits.add(mLet2.group(3).strip().replaceAll(";$","")); }
            else if (curIsDecl) decls.append(block); else stmts.append(block);
        }
        if (gNames.isEmpty() && decls.length()==0) {
            // no globals and no top-level decls — just wrap in main
            return "main() {\n" + pre + "\n}";
        }
        if (gNames.isEmpty()) {
            // only decls/stmts, no globals
            StringBuilder prog0 = new StringBuilder();
            prog0.append(decls);
            if (!stmts.isEmpty()) prog0.append("main() {\n").append(stmts).append("\n}\n");
            else if (decls.isEmpty()) prog0.append("main() {}\n");
            return prog0.toString();
        }
        // dedup globals last wins
        java.util.LinkedHashMap<String,String> typeMap = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String,String> initMap = new java.util.LinkedHashMap<>();
        for (int i=0;i<gNames.size();i++) { typeMap.put(gNames.get(i), gTypes.get(i)); initMap.put(gNames.get(i), gInits.get(i)); }
        StringBuilder prog = new StringBuilder();
        prog.append("class KofScriptGlobals {\n");
        for (String n: initMap.keySet()) {
            String ty=typeMap.get(n);
            String init=initMap.get(n);
            String fieldTy = ty != null ? ty.strip() : inferKofType(init);
            prog.append("  static ").append(fieldTy).append(" ").append(n).append(" = ").append(init).append("\n");
        }
        prog.append("}\n");
        String declStr = decls.toString();
        String stmtStr = stmts.toString();
        for (String n : initMap.keySet()) {
            declStr = declStr.replaceAll("\\b" + java.util.regex.Pattern.quote(n) + "\\b", "KofScriptGlobals." + n);
            stmtStr = stmtStr.replaceAll("\\b" + java.util.regex.Pattern.quote(n) + "\\b", "KofScriptGlobals." + n);
        }
        declStr = normalizeVoidFns(declStr);
        prog.append(declStr);
        if (!stmtStr.isBlank()) prog.append("main() {\n").append(stmtStr).append("\n}\n");
        else if (declStr.isBlank()) prog.append("main() {\nprintln(KofScriptGlobals.").append(initMap.keySet().iterator().next()).append(")\n}\n");
        else prog.append("main() {}\n");
        return prog.toString();
    }

    private static String normalizeVoidFns(String decls0) {
        // fn foo() { -> fn foo(): Void {
        return decls0.replaceAll("fn\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{", "fn $1($2): Void {");
    }

    /**
     * Fronteira .ks → .kf: KofScript mantém `fn` como sintaxe própria, mas o
     * parser Kof rejeita `fn`/`fun`/`func` (PARSE085 — SG-001). A tradução é
     * feita aqui, no último passo antes de materializar o .kf: `fn foo(...)`
     * → `foo(...)` (a forma anotada `foo(...): T` já é idiomática em Kof).
     */
    public static String toKofSyntax(String ks) {
        return ks.replaceAll("(?m)^(\\s*)fn\\s+(\\w+\\s*\\()", "$1$2");
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

    /**
     * Preprocess KofScript syntactic sugar:
     * - `async fn` → `fn` (async is just spawn+await, Kof already has spawn/await via Handle<T>)
     * - `let`/`const` → `var` (Kof's var)
     * Future: full async→Handle<T> transform will be done in the frontend.
     */
    public static String preprocess(String code) {
        // Preserve string literals while replacing
        String r = code.replaceAll("\\basync\\s+fn\\b", "fn");
        r = r.replaceAll("\\blet\\b", "var");
        r = r.replaceAll("\\bconst\\b", "val");
        return r;
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
            CompilationResult result;
            if (sources.size() == 1) {
                Path single = sources.get(0);
                if (single.toString().endsWith(".ks")) {
                    // Wrap single .ks like multi-file does — with top-level let as KofScriptGlobals
                    String content = Files.readString(single);
                    String pre = preprocess(content);
                    String[] lines = pre.split("\n");
                    StringBuilder decls = new StringBuilder();
                    StringBuilder stmts = new StringBuilder();
                    java.util.List<String> gNames = new java.util.ArrayList<>();
                    java.util.List<String> gTypes = new java.util.ArrayList<>();
                    java.util.List<String> gInits = new java.util.ArrayList<>();
                    java.util.regex.Pattern letPat2 = java.util.regex.Pattern.compile("^(?:let|var|val|const)\\s+(\\w+)(?:\\s*:\\s*([^=]+))?\\s*=\\s*(.+)$");
                    StringBuilder cur = new StringBuilder();
                    boolean curIsDecl = false;
                    for (String raw : lines) {
                        String t = raw.strip();
                        String tNorm = t.replaceFirst("^async\\s+", "");
                        if (t.isEmpty() || t.startsWith("//")) continue;
                        cur.append(raw).append('\n');
                        boolean declStart = tNorm.startsWith("fn ") || tNorm.startsWith("enum ") || tNorm.startsWith("class ") || tNorm.startsWith("record ");
                        if (cur.length() == raw.length() + 1) curIsDecl = declStart;
                        if (cur.toString().chars().filter(ch -> ch == '{').count() > cur.toString().chars().filter(ch -> ch == '}').count()) continue;
                        String block = cur.toString().strip().replaceFirst("(?m)^async\\s+fn\\b", "fn").replaceAll("\\blet\\b", "var").replaceAll("\\bconst\\b", "val");
                        String blockTrim = block.strip();
                        java.util.regex.Matcher mLet1 = letPat2.matcher(blockTrim);
                        boolean isTopLet = !curIsDecl && mLet1.matches() && !blockTrim.contains("{") && !blockTrim.contains("}");
                        if (isTopLet) {
                            gNames.add(mLet1.group(1));
                            gTypes.add(mLet1.group(2) != null ? mLet1.group(2).strip() : null);
                            gInits.add(mLet1.group(3).strip().replaceAll(";$", ""));
                        } else if (curIsDecl) decls.append(block).append('\n'); else stmts.append(block).append('\n');
                        cur.setLength(0);
                    }
                    if (!cur.isEmpty()) {
                        String block = cur.toString().strip().replaceFirst("(?m)^async\\s+fn\\b", "fn").replaceAll("\\blet\\b", "var");
                        String bt = block.strip();
                        java.util.regex.Matcher mLet2 = letPat2.matcher(bt);
                        boolean isTopLet2 = mLet2.matches() && !bt.contains("{") && !bt.contains("}");
                        if (isTopLet2) { gNames.add(mLet2.group(1)); gTypes.add(mLet2.group(2)!=null?mLet2.group(2).strip():null); gInits.add(mLet2.group(3).strip().replaceAll(";$","")); }
                        else if (curIsDecl) decls.append(block); else stmts.append(block);
                    }
                    // dedup globals (last wins)
                    java.util.LinkedHashMap<String,String> gTypeMap = new java.util.LinkedHashMap<>();
                    java.util.LinkedHashMap<String,String> gInitMap = new java.util.LinkedHashMap<>();
                    for (int i=0;i<gNames.size();i++) { gTypeMap.put(gNames.get(i), gTypes.get(i)); gInitMap.put(gNames.get(i), gInits.get(i)); }
                    StringBuilder prog = new StringBuilder();
                    if (!gInitMap.isEmpty()) {
                        prog.append("class KofScriptGlobals {\n");
                        for (String n: gInitMap.keySet()) {
                            String ty=gTypeMap.get(n);
                            String init=gInitMap.get(n);
                            String fieldTy = ty != null ? ty.strip() : inferKofType(init);
                            prog.append("  static ").append(fieldTy).append(" ").append(n).append(" = ").append(init).append("\n");
                        }
                        prog.append("}\n");
                        String declStr = decls.toString();
                        String stmtStr = stmts.toString();
                        for (String n: gInitMap.keySet()) { declStr = declStr.replaceAll("\\b"+java.util.regex.Pattern.quote(n)+"\\b", "KofScriptGlobals."+n); stmtStr = stmtStr.replaceAll("\\b"+java.util.regex.Pattern.quote(n)+"\\b", "KofScriptGlobals."+n); }
                        declStr = normalizeVoidFns(declStr);
                        decls = new StringBuilder(declStr);
                        stmts = new StringBuilder(stmtStr);
                    }
                    // normalize void fns even when no globals
                    decls = new StringBuilder(normalizeVoidFns(decls.toString()));
                    prog.append(decls);
                    if (!stmts.isEmpty()) prog.append("main() {\n").append(stmts).append("\n}\n");
                    else if (decls.isEmpty() && gInitMap.isEmpty()) prog.append("main() {}\n");
                    else if (prog.length()==0) prog.append("main() {}\n");
                    Path tmpKsDir = Files.createTempDirectory("kofscript-ks-single");
                    String kfName = single.getFileName().toString().replaceFirst("\\.ks$", ".kf");
                    if (!kfName.endsWith(".kf")) kfName = "Main.kf";
                    Path kf = tmpKsDir.resolve(kfName);
                    Files.writeString(kf, toKofSyntax(prog.toString()));
                    result = driver.compile(kf, outDir, target);
                    deleteRecursively(tmpKsDir);
                } else {
                    result = driver.compile(single, outDir, target);
                }
            } else {
                // For .ks files, we need to materialize them as .kf temp files with wrapping
                java.util.List<Path> kfSources = new java.util.ArrayList<>();
                Path tmpKsDir = null;
                for (Path p : sources) {
                    if (p.toString().endsWith(".ks")) {
                        if (tmpKsDir == null) tmpKsDir = Files.createTempDirectory("kofscript-ks");
                        String content = Files.readString(p);
                        String pre = preprocess(content);
                        String[] lines = pre.split("\n");
                        StringBuilder decls = new StringBuilder();
                        StringBuilder stmts = new StringBuilder();
                        java.util.List<String> gNames2 = new java.util.ArrayList<>();
                        java.util.List<String> gTypes2 = new java.util.ArrayList<>();
                        java.util.List<String> gInits2 = new java.util.ArrayList<>();
                        java.util.regex.Pattern letPat3 = java.util.regex.Pattern.compile("^(?:let|var|val|const)\\s+(\\w+)(?:\\s*:\\s*([^=]+))?\\s*=\\s*(.+)$");
                        StringBuilder cur = new StringBuilder();
                        boolean curIsDecl = false;
                        for (String raw : lines) {
                            String t = raw.strip();
                            String tNorm = t.replaceFirst("^async\\s+", "");
                            if (t.isEmpty() || t.startsWith("//")) continue;
                            cur.append(raw).append('\n');
                            boolean declStart = tNorm.startsWith("fn ") || tNorm.startsWith("enum ") || tNorm.startsWith("class ") || tNorm.startsWith("record ") || tNorm.startsWith("Int ") || tNorm.startsWith("String ");
                            if (cur.length() == raw.length() + 1) curIsDecl = declStart;
                            if (cur.toString().chars().filter(ch -> ch == '{').count() > cur.toString().chars().filter(ch -> ch == '}').count()) continue;
                            String block = cur.toString().strip().replaceFirst("(?m)^async\\s+fn\\b", "fn").replaceAll("\\blet\\b", "var").replaceAll("\\bconst\\b", "val");
                            String bt = block.strip();
                            java.util.regex.Matcher mLet3 = letPat3.matcher(bt);
                            boolean isTopLet3 = !curIsDecl && mLet3.matches() && !bt.contains("{") && !bt.contains("}");
                            if (isTopLet3) { gNames2.add(mLet3.group(1)); gTypes2.add(mLet3.group(2)!=null?mLet3.group(2).strip():null); gInits2.add(mLet3.group(3).strip().replaceAll(";$","")); }
                            else if (curIsDecl) decls.append(block).append('\n'); else stmts.append(block).append('\n');
                            cur.setLength(0);
                        }
                        if (!cur.isEmpty()) {
                            String block = cur.toString().strip().replaceFirst("(?m)^async\\s+fn\\b", "fn").replaceAll("\\blet\\b", "var");
                            String bt2 = block.strip();
                            java.util.regex.Matcher mLet4 = letPat3.matcher(bt2);
                            boolean isTopLet4 = mLet4.matches() && !bt2.contains("{") && !bt2.contains("}");
                            if (isTopLet4) { gNames2.add(mLet4.group(1)); gTypes2.add(mLet4.group(2)!=null?mLet4.group(2).strip():null); gInits2.add(mLet4.group(3).strip().replaceAll(";$","")); }
                            else if (curIsDecl) decls.append(block); else stmts.append(block);
                        }
                        java.util.LinkedHashMap<String,String> gTypeMap2 = new java.util.LinkedHashMap<>();
                        java.util.LinkedHashMap<String,String> gInitMap2 = new java.util.LinkedHashMap<>();
                        for(int i=0;i<gNames2.size();i++){ gTypeMap2.put(gNames2.get(i), gTypes2.get(i)); gInitMap2.put(gNames2.get(i), gInits2.get(i)); }
                        StringBuilder prog = new StringBuilder();
                        if (!gInitMap2.isEmpty()) {
                            prog.append("class KofScriptGlobals {\n");
                            for(String n: gInitMap2.keySet()){ String ty=gTypeMap2.get(n); String init=gInitMap2.get(n); String fieldTy= ty!=null ? ty.strip() : inferKofType(init); prog.append("  static ").append(fieldTy).append(" ").append(n).append(" = ").append(init).append("\n"); }
                            prog.append("}\n");
                            String ds=decls.toString(); String ss=stmts.toString();
                            for(String n: gInitMap2.keySet()){ ds=ds.replaceAll("\\b"+java.util.regex.Pattern.quote(n)+"\\b","KofScriptGlobals."+n); ss=ss.replaceAll("\\b"+java.util.regex.Pattern.quote(n)+"\\b","KofScriptGlobals."+n); }
                            ds = normalizeVoidFns(ds);
                            decls=new StringBuilder(ds); stmts=new StringBuilder(ss);
                        }
                        decls = new StringBuilder(normalizeVoidFns(decls.toString()));
                        prog.append(decls);
                        if (!stmts.isEmpty()) prog.append("main() {\n").append(stmts).append("\n}\n");
                        Path kf = tmpKsDir.resolve(p.getFileName().toString().replace(".ks", ".kf"));
                        Files.writeString(kf, toKofSyntax(prog.toString()));
                        kfSources.add(kf);
                    } else {
                        kfSources.add(p);
                    }
                }
                Path root = sourceFile.toAbsolutePath().normalize().getParent();
                if (root == null) root = Path.of(".").toAbsolutePath();
                // If source is dir, root is the dir itself
                if (Files.isDirectory(sourceFile)) root = sourceFile.toAbsolutePath().normalize();
                result = driver.compileSources(kfSources, outDir, target, root);
                if (tmpKsDir != null) deleteRecursively(tmpKsDir);
            }
            if (!result.success()) {
                StringBuilder sb = new StringBuilder();
                result.diagnostics().getDiagnostics().forEach(d -> sb.append(d.format()).append("\n"));
                return new RunResult(1, "", sb.toString(), false);
            }
            KofScript.RunResult rr = KofScriptExecutor.executeCompiled(outDir, target, programArgs);
            // Cache successful runs for file incremental
            if (rr.success() && abs != null && fkey != null && fhash != null) {
                fileCache.put(fkey, new FileCacheEntry(fileLm, sz, fhash, rr));
                if (fileCache.size() > 64) fileCache.clear();
            }
            return rr;
        } finally {
            deleteRecursively(outDir);
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
                    String pre = preprocess(content);
                    // Simplified wrap for inspect: just preprocess
                    Path kf = tmpKsDir.resolve(p.getFileName().toString().replace(".ks", ".kf"));
                    Files.writeString(kf, toKofSyntax(pre.contains("main()") ? pre : "main() {\n" + pre + "\n}"));
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
