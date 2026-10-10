package dev.kof.script;

import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofInterpretException;
import dev.kof.compiler.KofInterpreter;
import dev.kof.compiler.KofInterpreterSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * #739 — REPL INCREMENTAL do KofScript.
 * <p>
 * O modelo antigo ({@code KofScript.repl}) acumulava o histórico e re-avaliava
 * o programa INTEIRO a cada linha, o que duplicava os efeitos colaterais
 * ({@code println("X")} e depois {@code println("Y")} imprimia {@code X} e
 * depois {@code X Y}). Aqui cada linha é avaliada UMA vez: os globais de topo
 * vivem num mapa de estáticos compartilhado entre avaliações (o workDir do
 * runtime também é fixo por sessão) e o valor da última expressão é ecoado.
 * <p>
 * Sem sugar de outra linguagem: as linhas continuam sendo Kof puro; o wrapper
 * apenas promove {@code var}/{@code val} de topo a campos de
 * {@code KofScriptGlobals} e injeta {@code println(expr)} quando a linha é uma
 * expressão.
 */
final class KofRepl {

    private KofRepl() {}

    private static final Pattern VAR_PAT = Pattern.compile(
            "^(var|val)\\s+(\\w+)(?:\\s*:\\s*([^=]+))?\\s*=\\s*(.+)$");
    private static final Pattern ASSIGN_PAT = Pattern.compile(
            "^[A-Za-z_][\\w.\\[\\]]*\\s*(\\+|-|\\*|/|%)?=\\s*[^=].*");

    static void run(InputStream in, PrintStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        out.println("KofScript REPL 0.1.2-beta — type 'exit' to quit");
        LinkedHashMap<String, String[]> globals = new LinkedHashMap<>(); // name -> {type, init}
        List<String> imports = new ArrayList<>();
        List<String> decls = new ArrayList<>();
        Map<String, Map<String, Object>> statics = new ConcurrentHashMap<>();
        Path srcDir = Files.createTempDirectory("kof-repl-src-");
        Path workDir = Files.createTempDirectory("kof-repl-");
        try {
            StringBuilder pending = new StringBuilder();
            while (true) {
                out.print(pending.length() == 0 ? "kof> " : "...> ");
                out.flush();
                String line = reader.readLine();
                if (line == null) break;
                if (pending.length() == 0 && "exit".equals(line.strip())) break;
                if (pending.length() == 0 && line.isBlank()) continue;
                pending.append(line).append('\n');
                if (braceBalance(pending.toString()) > 0) continue;
                String block = pending.toString().strip();
                pending.setLength(0);
                if (block.isEmpty()) continue;
                evalBlock(block, globals, imports, decls, statics, srcDir, workDir, out);
            }
        } finally {
            deleteRecursively(srcDir);
            deleteRecursively(workDir);
        }
    }

    private static void evalBlock(String block, LinkedHashMap<String, String[]> globals,
                                  List<String> imports, List<String> decls,
                                  Map<String, Map<String, Object>> statics,
                                  Path srcDir, Path workDir, PrintStream out) {
        // Snapshot para rollback se a linha não compilar.
        LinkedHashMap<String, String[]> prevGlobals = new LinkedHashMap<>(globals);
        int prevDecls = decls.size();
        int prevImports = imports.size();

        String statement = null;
        String echoGlobal = null;
        boolean echoCandidate = false;
        Matcher vm = VAR_PAT.matcher(block);
        if (vm.matches() && !block.contains("{") && !block.contains("}")) {
            String name = vm.group(2);
            String explicit = vm.group(3) != null ? vm.group(3).strip() : null;
            String init = vm.group(4).strip().replaceAll(";$", "");
            String type = explicit != null ? explicit : ScriptGlobalTypes.infer(init);
            globals.put(name, new String[]{type, init});
            echoGlobal = name;
        } else if (block.startsWith("import ")) {
            imports.add(block);
        } else if (KofScript.isTopLevelDecl(block)) {
            decls.add(block);
        } else {
            statement = block;
            echoCandidate = isEchoCandidate(block);
        }

        // Monta o programa da linha. `mainBody` é o corpo de main(); quando
        // `echoGlobal` existe, ele é inicializado e impresso (var de topo).
        if (echoGlobal != null) {
            String[] g = globals.get(echoGlobal);
            List<String> body = new ArrayList<>();
            body.add("KofScriptGlobals." + echoGlobal + " = "
                    + KofScript.qualifyGlobals(g[1], globals.keySet()));
            body.add("println(KofScriptGlobals." + echoGlobal + ")");
            if (!runProgram(build(imports, globals, decls, body), srcDir, workDir, statics, out)) {
                rollback(globals, prevGlobals, decls, prevDecls, imports, prevImports);
            }
            return;
        }
        if (statement == null) {
            // Só import/decl: valida a compilação (sem executar nada visível).
            if (!runProgram(build(imports, globals, decls, List.of()), srcDir, workDir, statics, out)) {
                rollback(globals, prevGlobals, decls, prevDecls, imports, prevImports);
            }
            return;
        }
        // Expressão: tenta ecoar o valor (`println(expr)`) SILENCIOSAMENTE; se
        // não compilar (ex.: chamada void como `println("X")`, que dá SEM033),
        // roda a sentença pura. A falha é de compilação, então não há efeito
        // colateral a desfazer e nenhum erro espúrio é impresso.
        if (echoCandidate) {
            Exec echo = exec(build(imports, globals, decls,
                    List.of("println(" + statement + ")")), srcDir, workDir, statics);
            if (echo.error() == null) {
                out.print(echo.stdout());
                return;
            }
        }
        Exec plain = exec(build(imports, globals, decls, List.of(statement)),
                srcDir, workDir, statics);
        if (plain.error() != null) {
            out.println(plain.error());
            rollback(globals, prevGlobals, decls, prevDecls, imports, prevImports);
            return;
        }
        out.print(plain.stdout());
    }

    private static String build(List<String> imports, LinkedHashMap<String, String[]> globals,
                                List<String> decls, List<String> mainBody) {
        StringBuilder prog = new StringBuilder();
        for (String imp : imports) prog.append(imp).append('\n');
        if (!globals.isEmpty()) {
            prog.append("class KofScriptGlobals {\n");
            for (Map.Entry<String, String[]> e : globals.entrySet()) {
                prog.append("  static ").append(e.getValue()[0]).append(" ")
                    .append(e.getKey()).append("\n");
            }
            prog.append("}\n");
        }
        for (String d : decls) prog.append(d).append('\n');
        prog.append("main() {\n");
        for (String line : mainBody) {
            prog.append("  ").append(KofScript.qualifyGlobals(line, globals.keySet())).append("\n");
        }
        prog.append("}\n");
        return prog.toString();
    }

    /** Resultado de uma linha: stdout quando rodou; error quando recusou/falhou. */
    private record Exec(String stdout, String error) {}

    /**
     * Compila e executa o programa da linha reusando o workDir e os estáticos,
     * SEM imprimir — devolve stdout (sucesso) ou error (frontend/execução).
     */
    private static Exec exec(String program, Path srcDir, Path workDir,
                             Map<String, Map<String, Object>> statics) {
        Path src = srcDir.resolve("Main.kf");
        try {
            Files.writeString(src, program);
            CompilerDriver driver = new CompilerDriver();
            IRModule ir = driver.prepareRepl(List.of(src), srcDir);
            KofInterpreter.Result r = KofInterpreterSession.runIncremental(
                    ir, new String[0], driver.interpreterWarnings(), workDir, statics);
            if (r.exitCode() != 0) {
                return new Exec(null, "error: " + r.stderr().strip());
            }
            return new Exec(r.stdout(), null);
        } catch (KofInterpretException e) {
            StringBuilder sb = new StringBuilder();
            e.diagnostics().getDiagnostics().forEach(d -> sb.append(d.format()).append('\n'));
            return new Exec(null, "error: " + sb.toString().strip());
        } catch (IOException e) {
            return new Exec(null, "error: " + e.getMessage());
        }
    }

    /** Executa imprimindo o erro quando falha — devolve {@code true} se rodou. */
    private static boolean runProgram(String program, Path srcDir, Path workDir,
                                      Map<String, Map<String, Object>> statics, PrintStream out) {
        Exec e = exec(program, srcDir, workDir, statics);
        if (e.error() != null) {
            out.println(e.error());
            return false;
        }
        out.print(e.stdout());
        return true;
    }

    private static void rollback(LinkedHashMap<String, String[]> globals,
                                 LinkedHashMap<String, String[]> prevGlobals,
                                 List<String> decls, int prevDecls,
                                 List<String> imports, int prevImports) {
        globals.clear();
        globals.putAll(prevGlobals);
        while (decls.size() > prevDecls) decls.remove(decls.size() - 1);
        while (imports.size() > prevImports) imports.remove(imports.size() - 1);
    }

    /**
     * Um bloco é candidato a eco quando NÃO é declaração de var/val,
     * atribuição nem bloco com corpo (`…}`). O eco é tentado como
     * `println(expr)`; se não compilar (ex.: chamada void), a sentença pura é
     * executada — sem dupla execução, pois a falha é de compilação.
     */
    static boolean isEchoCandidate(String block) {
        String t = block.strip();
        if (t.endsWith("}")) return false;
        if (ASSIGN_PAT.matcher(t).matches()) return false;
        return true;
    }

    /** +1 por `{` aberto, -1 por `}` — aguarda o bloco fechar antes de avaliar. */
    static int braceBalance(String text) {
        int depth = 0;
        boolean inStr = false, inChar = false, inLine = false, inBlock = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char n = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') inLine = false;
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inStr) {
                if (c == '\\') i++;
                else if (c == '"') inStr = false;
                continue;
            }
            if (inChar) {
                if (c == '\\') i++;
                else if (c == '\'') inChar = false;
                continue;
            }
            if (c == '/' && n == '/') {
                inLine = true;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') {
                inStr = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        return depth;
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.walk(dir).sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignore) {}
            });
        } catch (IOException ignore) {}
    }
}
