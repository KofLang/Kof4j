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
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
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
                capabilities.put("referencesProvider", Boolean.TRUE);
                capabilities.put("renameProvider", Boolean.TRUE);
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
            case "textDocument/completion" -> completion(id, params);
            case "textDocument/references" -> references(id, params);
            case "textDocument/rename" -> rename(id, params);
            default -> {  }
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
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "textDocument/publishDiagnostics");
        notification.put("params", result);
        writeMessage(Json.stringify(notification));
    }

    private void respond(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        writeMessage(Json.stringify(response));
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
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "textDocument/publishDiagnostics");
        notification.put("params", result);
        writeMessage(Json.stringify(notification));
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
        int l = 0, i = 0, n = text.length();
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

    private static final List<String[]> KEYWORDS = List.of(
            new String[]{"var", "variável mutável"}, new String[]{"val", "valor imutável"},
            new String[]{"spawn", "roda tarefa em virtual thread"},
            new String[]{"await", "aguarda Handle<T> e devolve T"},
            new String[]{"enum", "conjunto fechado de constantes"},
            new String[]{"record", "estrutura imutável com componentes"},
            new String[]{"class", "classe"}, new String[]{"interface", "contrato"},
            new String[]{"switch", "seleção (exaustiva sobre enum → SEM031)"},
            new String[]{"listOf", "cria List<T>"}, new String[]{"mapOf", "cria Map<K,V>"},
            new String[]{"setOf", "cria Set<T>"},
            new String[]{"println", "imprime linha no stdout"});

    private static final List<String> BUILTIN_TYPES = List.of(
            "Int", "Long", "Bool", "String", "Float", "Double");

    private void hover(Object id, Map<String, Object> params) {
        Map<String, Object> td = params.get("textDocument") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String text = openText.getOrDefault(str(td.get("uri")), "");
        Map<String, Object> pos = params.get("position") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        long line = pos.get("line") instanceof Number n ? n.longValue() : 0;
        long ch = pos.get("character") instanceof Number n ? n.longValue() : 0;
        String word = wordAt(text, offsetOf(text, line, ch));
        if (word.isEmpty()) { respond(id, null); return; }
        String contents = hoverFor(word, text);
        if (contents == null) { respond(id, null); return; }
        respond(id, Map.of("contents", Map.of("kind", "markdown", "value", contents)));
    }

    private String hoverFor(String word, String text) {
        for (String[] k : KEYWORDS) {
            if (k[0].equals(word)) return "**" + k[0] + "** — " + k[1];
        }
        if (BUILTIN_TYPES.contains(word)) return "**" + word + "** — tipo primitivo Kof";
        for (String ln : text.split("\n")) {
            String t = ln.strip();
            if (t.startsWith("var ") || t.startsWith("val ")) {
                String rest = t.substring(4).strip();
                if (rest.startsWith(word)) {
                    int after = rest.indexOf(word) + word.length();
                    if (after < rest.length() && ":= \t".indexOf(rest.charAt(after)) >= 0) {
                        return "**" + word + "** — variável local\n```kf\n" + ln.strip() + "\n```";
                    }
                }
            }
        }
        return null;
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
            for (String[] k : KEYWORDS) add.accept(k[0], "Keyword");
            for (String ty : BUILTIN_TYPES) add.accept(ty, "Type");
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isIncomplete", false);
        result.put("items", items);
        respond(id, result);
    }

    /** Todas as ocorrências (start, end) do identificador em fronteiras de palavra. */
    private static List<int[]> wordOccurrences(String text, String word) {
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
        int off = offsetOf(text, line, ch);
        String word = wordAt(text, off);
        String newName = str(params.get("newName"));
        if (word.isEmpty() || !isValidIdentifier(newName)) { respond(id, null); return; }
        List<int[]> occ = wordOccurrences(text, word);
        if (occ.isEmpty()) { respond(id, null); return; }
        List<Object> edits = new ArrayList<>();
        for (int[] r : occ) {
            Map<String, Object> edit = new LinkedHashMap<>();
            edit.put("range", rangeOf(text, r[0], r[1]));
            edit.put("newText", newName);
            edits.add(edit);
        }
        Map<String, Object> docEdit = new LinkedHashMap<>();
        docEdit.put("textDocument", Map.of("uri", uri));
        docEdit.put("edits", edits);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentChanges", List.of(docEdit));
        respond(id, result);
    }

    private static Map<String, Object> rangeOf(String text, int start, int end) {
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

    private static boolean isValidIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '_';
            if (!ok) return false;
        }
        return true;
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