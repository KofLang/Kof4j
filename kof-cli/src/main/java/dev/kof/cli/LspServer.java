package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


final class LspServer {

    private final InputStream in;
    private final OutputStream out;
    private final CompilerDriver driver = new CompilerDriver();
    private final Map<String, String> openText = new HashMap<>();
    private boolean running = true;

    LspServer(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    void run() throws IOException {
        while (running) {
            int contentLength = -1;
            while (true) {
                String line = readLine();
                if (line == null) return;
                if (line.isBlank()) break;
                if (line.toLowerCase().startsWith("content-length:")) {
                    // §CodeQL uncaught-NFE: header malformado nao pode matar o
                    // servidor com stack-trace (R6: diagnostico + saida limpa;
                    // sem length valido o framing esta perdido — encerra a
                    // conexao em vez de continuar lendo lixo).
                    String raw = line.substring("content-length:".length()).trim();
                    try {
                        contentLength = Integer.parseInt(raw);
                    } catch (NumberFormatException e) {
                        System.err.println("kof lsp: Content-Length invalido: '" + raw + "'");
                        return;
                    }
                }
            }
            if (contentLength < 0) continue;
            byte[] body = in.readNBytes(contentLength);
            if (body.length < contentLength) return;
            handleMessage(new String(body, StandardCharsets.UTF_8));
        }
    }

    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        if (c == -1 && sb.isEmpty()) return null;
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String raw) {
        Object parsed = Json.parse(raw);
        if (!(parsed instanceof Map<?, ?> msg)) return;
        Map<String, Object> m = (Map<String, Object>) msg;
        Object id = m.get("id");
        String method = m.get("method") == null ? null : m.get("method").toString();
        Map<String, Object> params = m.get("params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();

        if (method == null) return; 

        switch (method) {
            case "initialize" -> {
                Map<String, Object> capabilities = new LinkedHashMap<>();
                Map<String, Object> sync = new LinkedHashMap<>();
                sync.put("change", 1L); 
                sync.put("openClose", Boolean.TRUE);
                capabilities.put("textDocumentSync", sync);
                capabilities.put("positionEncoding", "utf-16");
                Map<String, Object> completion = new LinkedHashMap<>();
                completion.put("triggerCharacters", List.of("."));
                capabilities.put("completionProvider", completion);
                capabilities.put("hoverProvider", Boolean.TRUE);
                Map<String, Object> sigHelp = new LinkedHashMap<>();
                sigHelp.put("triggerCharacters", List.of("(", ","));
                capabilities.put("signatureHelpProvider", sigHelp);
                capabilities.put("definitionProvider", Boolean.TRUE);
                capabilities.put("referencesProvider", Boolean.TRUE);
                capabilities.put("renameProvider", Boolean.TRUE);
                capabilities.put("documentFormattingProvider", Boolean.TRUE);
                capabilities.put("documentSymbolProvider", Boolean.TRUE);
                capabilities.put("workspaceSymbolProvider", Boolean.TRUE);
                capabilities.put("codeActionProvider",
                        Map.of("codeActionKinds", List.of("source")));
                java.nio.file.Path root = params.get("rootUri") instanceof String ru
                        ? LspProject.toPath(ru) : null;
                if (root != null) workspaceRoot = root;
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("capabilities", capabilities);
                result.put("serverInfo", Map.of("name", "kof-lsp", "version", dev.kof.compiler.KofVersion.version()));
                respond(id, result);
            }
            case "initialized" -> {  }
            case "shutdown" -> respond(id, null);
            case "exit" -> running = false;
            case "textDocument/didOpen" -> publishDiagnostics(params);
            case "textDocument/didChange" -> publishDiagnostics(params);
            case "textDocument/didClose" -> clearDiagnostics(params);
            case "textDocument/hover" -> hover(id, params);
            case "textDocument/signatureHelp" -> signatureHelp(id, params);
            case "textDocument/definition" -> definition(id, params);
            case "textDocument/completion" -> completion(id, params);
            case "textDocument/references" -> references(id, params);
            case "textDocument/rename" -> rename(id, params);
            case "textDocument/formatting" -> formatting(id, params);
            case "textDocument/documentSymbol" -> documentSymbol(id, params);
                case "workspace/symbol" -> workspaceSymbol(id, params);
            case "textDocument/codeAction" -> codeAction(id, params);
            default -> {
                // A request carries an `id` and MUST be answered (JSON-RPC 2.0):
                // silence makes a compliant client block until timeout. A
                // notification (no `id`, e.g. $/setTrace) stays ignored.
                if (id != null) respondError(id, -32601, "Method not found: " + method);
            }
        }
    }

    /** didClose — clears diagnostics for the closed document. */
    private void clearDiagnostics(Map<String, Object> params) {
        Map<String, Object> textDoc = params.get("textDocument") instanceof Map<?, ?> td
                ? (Map<String, Object>) td : Map.of();
        String uri = textDoc.get("uri") == null ? "" : textDoc.get("uri").toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uri", uri);
        result.put("diagnostics", List.of());
        writeMessage(LspJsonRpc.notification("textDocument/publishDiagnostics", result));
    }

    private void respond(Object id, Object result) {
        writeMessage(LspJsonRpc.success(id, result));
    }

    /** JSON-RPC 2.0 error response (no `result` field). */
    private void respondError(Object id, int code, String message) {
        writeMessage(LspJsonRpc.error(id, code, message));
    }

    @SuppressWarnings("unchecked")
    private void publishDiagnostics(Map<String, Object> params) {
        Map<String, Object> textDoc = params.get("textDocument") instanceof Map<?, ?> td
                ? (Map<String, Object>) td : Map.of();
        String uri = textDoc.get("uri") == null ? "" : textDoc.get("uri").toString();
        String text = textDoc.get("text") == null ? "" : textDoc.get("text").toString();
        openText.put(uri, text);
        if (params.get("contentChanges") instanceof List<?> changes && !changes.isEmpty()
                && changes.get(0) instanceof Map<?, ?> c) {
            text = String.valueOf(((Map<?, ?>) c).get("text"));
        }

        List<Object> diagnostics = analyze(uri, text);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uri", uri);
        result.put("diagnostics", diagnostics);
        writeMessage(LspJsonRpc.notification("textDocument/publishDiagnostics", result));
    }

    private List<Object> analyze(String uri, String text) {
        List<Object> diagnostics = new ArrayList<>();
        Path tmpDir = null;
        Path file = null;
        try {
            tmpDir = Files.createTempDirectory("kof-lsp-");
            String name = "LspMain.kf";
            String path = uri.startsWith("file:") ? uri.substring("file:".length()) : uri;
            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            if (slash >= 0) path = path.substring(slash + 1);
            if (path.endsWith(".kf") || path.endsWith(".ks")) name = path;
            // KofScript = Kof puro executado direto: sem sugar de outra
            // linguagem; o wrapper só dá modelo de script (statements ->
            // main(), var/val de topo -> globals).
            String outText = text;
            if (name.endsWith(".ks")) {
                outText = text.contains("main()") ? text : dev.kof.script.KofScript.wrapPureKof(text);
                name = name.replace(".ks", ".kf");
            }
            file = tmpDir.resolve(name);
            Files.writeString(file, outText);

            CompilationResult result = driver.compile(file, tmpDir.resolve("out"), Target.JVM);
            for (Diagnostic d : result.diagnostics().getDiagnostics()) {
                Map<String, Object> diag = new LinkedHashMap<>();
                Map<String, Object> range = new LinkedHashMap<>();
                Map<String, Object> start = new LinkedHashMap<>();
                Map<String, Object> end = new LinkedHashMap<>();
                start.put("line", Math.max(0, d.line() - 1));
                start.put("character", Math.max(0, d.column() - 1));
                end.put("line", Math.max(0, d.line() - 1));
                end.put("character", Math.max(0, d.column() - 1 + Math.max(0, d.length())));
                range.put("start", start);
                range.put("end", end);
                diag.put("range", range);
                diag.put("severity", d.severity() == Diagnostic.Severity.ERROR ? 1 : 2);
                diag.put("source", "kof");
                diag.put("code", d.code());
                diag.put("message", d.message() + (d.code() != null && !d.code().isEmpty()
                        ? " [" + d.code() + "]" : ""));
                diagnostics.add(diag);
            }
        } catch (IOException e) {
            Map<String, Object> diag = new LinkedHashMap<>();
            Map<String, Object> range = new LinkedHashMap<>();
            range.put("start", Map.of("line", 0L, "character", 0L));
            range.put("end", Map.of("line", 0L, "character", 0L));
            diag.put("range", range);
            diag.put("severity", 1);
            diag.put("source", "kof");
            diag.put("message", "internal error: " + e.getMessage());
            diagnostics.add(diag);
        } finally {
            if (tmpDir != null) {
                try (var s = Files.walk(tmpDir)) {
                    s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                } catch (IOException ignored) {}
            }
        }
        return diagnostics;
    }

    /** Offset (0-based) a partir de line/character LSP. */
    private static int offsetOf(String text, long line, long character) {
        long l = 0;
        int i = 0, n = text.length();
        while (i < n && l < line) {
            if (text.charAt(i) == '\n') l++;
            i++;
        }
        return Math.min(n, i + (int) character);
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    /** Palavra no cursor: [start,end) do identificador contendo offset. */
    private static String wordAt(String text, int offset) {
        int st = offset;
        while (st > 0 && (Character.isLetterOrDigit(text.charAt(st - 1)) || text.charAt(st - 1) == '_')) st--;
        int en = offset;
        while (en < text.length() && (Character.isLetterOrDigit(text.charAt(en)) || text.charAt(en) == '_')) en++;
        return en > st ? text.substring(st, en) : "";
    }

    /** raiz do workspace do `initialize` (8.3-B); null = so a arvore do arquivo. */
    private java.nio.file.Path workspaceRoot;

    private void hover(Object id, Map<String, Object> params) {
        Map<String, Object> td = params.get("textDocument") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String text = openText.getOrDefault(str(td.get("uri")), "");
        Map<String, Object> pos = params.get("position") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        long line = pos.get("line") instanceof Number n ? n.longValue() : 0;
        long ch = pos.get("character") instanceof Number n ? n.longValue() : 0;
        String word = wordAt(text, offsetOf(text, line, ch));
        String contents = word.isEmpty() ? null : LspHover.hoverFor(word, text, offsetOf(text, line, ch));
        if (contents == null && !word.isEmpty()) {
            // X10 fatia 7: declaração do projeto (buffer ou .kf irmão) como fallback.
            String[] d = LspProject.declarationLine(str(td.get("uri")), text, word, workspaceRoot);
            if (d != null) {
                contents = "**" + word + "** \u2014 declared in `" + d[1] + "`\n```kof\n" + d[0] + "\n```";
            }
        }
        if (contents == null) { respond(id, null); return; }
        respond(id, Map.of("contents", Map.of("kind", "markdown", "value", contents)));
    }

    @SuppressWarnings("unchecked")
    private void completion(Object id, Map<String, Object> params) {
        Map<String, Object> td = params.get("textDocument") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String text = openText.getOrDefault(str(td.get("uri")), "");
        Map<String, Object> pos = params.get("position") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        long line = pos.get("line") instanceof Number n ? n.longValue() : 0;
        long ch = pos.get("character") instanceof Number n ? n.longValue() : 0;
        int off = offsetOf(text, line, ch);
        boolean member = off > 0 && text.charAt(off - 1) == '.';
        List<Object> items = new ArrayList<>();
        java.util.function.BiConsumer<String, String> add = (label, kind) -> {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("label", label);
            it.put("kind", kind);
            it.put("detail", "Kof");
            items.add(it);
        };
        if (!member) {
            for (String[] k : LspHover.KEYWORDS) add.accept(k[0], "Keyword");
            for (String ty : LspHover.BUILTIN_TYPES) add.accept(ty, "Type");
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String ln : text.split("\n")) {
            String t = ln.strip();
            if ((t.startsWith("var ") || t.startsWith("val ")) && t.contains("=")) {
                String rest = t.substring(4).strip();
                String name = rest.split("[\\s:=]")[0];
                if (!name.isEmpty() && seen.add(name)) add.accept(name, "Variable");
            }
        }
        // X10 fatia 1: completion domain-aware — membros reais do typer
        // (StdCatalog) quando o prefixo antes do '.' é um namespace stdlib.
        if (member) {
            String ns = namespaceBefore(text, off - 1);
            if (ns != null) {
                for (String fn : dev.kof.compiler.StdCatalog.membersOf(ns)) {
                    Map<String, Object> it = new LinkedHashMap<>();
                    it.put("label", fn);
                    it.put("kind", "Function");
                    it.put("detail", "kof." + ns);
                    items.add(it);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isIncomplete", false);
        result.put("items", items);
        respond(id, result);
    }

    /** Identificador antes da posição do '.', se for namespace stdlib (X10). */
    private static String namespaceBefore(String text, int dotIndex) {
        int i = dotIndex;
        while (i > 0 && isIdentChar(text.charAt(i - 1))) i--;
        String w = text.substring(i, dotIndex);
        return dev.kof.compiler.StdCatalog.isNamespace(w) ? w : null;
    }

    /** Todas as ocorrências (start, end) do identificador em fronteiras de palavra. */
    static List<int[]> wordOccurrences(String text, String word) {
        List<int[]> out = new ArrayList<>();
        if (word.isEmpty()) return out;
        int from = 0;
        while (true) {
            int idx = text.indexOf(word, from);
            if (idx < 0) break;
            int end = idx + word.length();
            boolean leftOk = idx == 0 || !isIdentChar(text.charAt(idx - 1));
            boolean rightOk = end >= text.length() || !isIdentChar(text.charAt(end));
            if (leftOk && rightOk) out.add(new int[]{idx, end});
            from = idx + 1;
        }
        return out;
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    @SuppressWarnings("unchecked")
    private void signatureHelp(Object id, Map<String, Object> params) {
        Map<String, Object> td = params.get("textDocument") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String text = openText.getOrDefault(str(td.get("uri")), "");
        Map<String, Object> pos = params.get("position") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        long line = pos.get("line") instanceof Number n ? n.longValue() : 0;
        long ch = pos.get("character") instanceof Number n ? n.longValue() : 0;
        respond(id, LspSignatureHelp.helpFor(text, offsetOf(text, line, ch)));
    }

    private void definition(Object id, Map<String, Object> params) {
        Map<String, Object> td = params.get("textDocument") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String uri = str(td.get("uri"));
        String text = openText.getOrDefault(uri, "");
        Map<String, Object> pos = params.get("position") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        long line = pos.get("line") instanceof Number n ? n.longValue() : 0;
        long ch = pos.get("character") instanceof Number n ? n.longValue() : 0;
        int off = offsetOf(text, line, ch);
        String word = wordAt(text, off);
        int[] decl = LspSymbols.declarationRange(text, word);
        if (decl == null) {
            // X10 fatia 4: go-to-definition em packages — se o nome não é
            // declarado no buffer, procura nos .kf irmãos do projeto (mesma
            // convenção LspSymbols; sem parser paralelo; primeiro hit).
            Map<String, Object> other = crossFileDefinition(uri, word);
            respond(id, other == null ? null : List.of(other));
            return;
        }
        Map<String, Object> loc = new LinkedHashMap<>();
        loc.put("uri", uri);
        loc.put("range", rangeOf(text, decl[0], decl[1]));
        respond(id, List.of(loc));
    }

    @SuppressWarnings("unchecked")
    private void formatting(Object id, Map<String, Object> params) {
        Map<String, Object> td = params.get("textDocument") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String uri = str(td.get("uri"));
        String text = openText.getOrDefault(uri, "");
        Map<String, Object> edit = formatEdit(uri, text);
        if (edit == null) { respond(id, List.of()); return; }
        respond(id, List.of(edit));
    }

    /**
     * Edit de formatação (range do documento inteiro + newText), ou null se
     * já está formatado / o parser não fecha (não corrompe o buffer — R6).
     * Compartilhado por textDocument/formatting e codeAction source.format.
     */
    private Map<String, Object> formatEdit(String uri, String text) {
        String formatted;
        try {
            formatted = dev.kof.compiler.KofFormatter.format(text, fileNameOf(uri));
        } catch (RuntimeException e) {
            return null;
        }
        // §625: com parse-error ou fonte não-fechável o formatter devolve null —
        // responder null (sem edit) ao cliente, e NAO dar NPE no equals derrubando o server.
        if (formatted == null || formatted.equals(text)) return null;
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("range", rangeOf(text, 0, text.length()));
        edit.put("newText", formatted);
        return edit;
    }

    @SuppressWarnings("unchecked")
    private void codeAction(Object id, Map<String, Object> params) {
        Map<String, Object> td = params.get("textDocument") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String uri = str(td.get("uri"));
        String text = openText.getOrDefault(uri, "");
        List<Object> actions = new ArrayList<>();
        Map<String, Object> edit = formatEdit(uri, text);
        if (edit != null) {
            Map<String, Object> docEdit = new LinkedHashMap<>();
            docEdit.put("textDocument", Map.of("uri", uri));
            docEdit.put("edits", List.of(edit));
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("title", "Format Document");
            action.put("kind", "source");
            action.put("edit", Map.of("documentChanges", List.of(docEdit)));
            actions.add(action);
        }
        respond(id, actions);
    }

    private static String fileNameOf(String uri) {
        String path = uri.startsWith("file:") ? uri.substring("file:".length()) : uri;
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    @SuppressWarnings("unchecked")
    private void workspaceSymbol(Object id, Map<String, Object> params) {
        respond(id, LspProject.workspaceSymbols(openText, str(params.get("query")), workspaceRoot));
    }

    private void documentSymbol(Object id, Map<String, Object> params) {
        Map<String, Object> td = params.get("textDocument") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String uri = str(td.get("uri"));
        respond(id, LspSymbols.documentSymbolMaps(openText.getOrDefault(uri, "")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> crossFileDefinition(String fromUri, String word) {
        if (word.isEmpty()) return null;
        java.nio.file.Path self = LspProject.toPath(fromUri);
        if (self == null) return null;
        for (java.nio.file.Path f : LspProject.siblings(self, workspaceRoot)) {
            String txt = LspProject.readOrNull(f);
            if (txt == null) continue;
            int[] decl = LspSymbols.declarationRange(txt, word);
            if (decl != null) {
                Map<String, Object> loc = new LinkedHashMap<>();
                loc.put("uri", f.toAbsolutePath().toUri().toString());
                loc.put("range", rangeOf(txt, decl[0], decl[1]));
                return loc;
            }
        }
        return null;
    }

    private void references(Object id, Map<String, Object> params) {
        Map<String, Object> td = params.get("textDocument") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String uri = str(td.get("uri"));
        String text = openText.getOrDefault(uri, "");
        Map<String, Object> pos = params.get("position") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        long line = pos.get("line") instanceof Number n ? n.longValue() : 0;
        long ch = pos.get("character") instanceof Number n ? n.longValue() : 0;
        int off = offsetOf(text, line, ch);
        String word = wordAt(text, off);
        List<Object> locations = new ArrayList<>();
        for (int[] r : wordOccurrences(text, word)) {
            Map<String, Object> loc = new LinkedHashMap<>();
            loc.put("uri", uri);
            loc.put("range", rangeOf(text, r[0], r[1]));
            locations.add(loc);
        }
        // X10 fatia 5: referências também nos .kf irmãos do projeto (read-only).
        java.nio.file.Path self = LspProject.toPath(uri);
        if (self != null && !word.isEmpty()) {
            for (java.nio.file.Path f : LspProject.siblings(self, workspaceRoot)) {
                String txt = LspProject.readOrNull(f);
                if (txt == null) continue;
                for (int[] r : wordOccurrences(txt, word)) {
                    Map<String, Object> loc = new LinkedHashMap<>();
                    loc.put("uri", f.toAbsolutePath().toUri().toString());
                    loc.put("range", rangeOf(txt, r[0], r[1]));
                    locations.add(loc);
                }
            }
        }
        respond(id, locations);
    }

    @SuppressWarnings("unchecked")
    private void rename(Object id, Map<String, Object> params) {
        Map<String, Object> td = params.get("textDocument") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String uri = str(td.get("uri"));
        String text = openText.getOrDefault(uri, "");
        Map<String, Object> pos = params.get("position") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        long line = pos.get("line") instanceof Number n ? n.longValue() : 0;
        long ch = pos.get("character") instanceof Number n ? n.longValue() : 0;
        String word = wordAt(text, offsetOf(text, line, ch));
        String newName = str(params.get("newName"));
        // LSP-A (D-POLL-19 19/09): rename cross-file na mesma convenção dos
        // references; guardas (keyword/namespace/nome inválido) em LspRename.
        respond(id, LspRename.workspaceEdit(uri, text, word, newName, openText,
                LspProject.toPath(uri), workspaceRoot));
    }

    static Map<String, Object> rangeOf(String text, int start, int end) {
        Map<String, Object> s = positionPoint(text, start);
        Map<String, Object> e = positionPoint(text, end);
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("start", s);
        range.put("end", e);
        return range;
    }

    private static Map<String, Object> positionPoint(String text, int offset) {
        int line = 0;
        int lineStart = 0;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') { line++; lineStart = i + 1; }
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("line", line);
        p.put("character", offset - lineStart);
        return p;
    }


    private void writeMessage(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        try {
            out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
        } catch (IOException e) {
            running = false;
        }
    }
}