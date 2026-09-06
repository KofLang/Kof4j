package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * JsArtifactWriter — escreve os artefatos de saída do backend JS:
 * módulo .mjs + source map, runtime (kof-runtime*.mjs) e index.html.
 */
class JsArtifactWriter {

    static String moduleFileName(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) return "Default.mjs";
        return moduleName + ".mjs";
    }

    void writeModule(Path outputDir, String fileName, String code, boolean debugInfo) throws IOException {
        String sourceMapUrl = debugInfo ? "//# sourceMappingURL=" + fileName + ".map\n" : "";
        Files.writeString(outputDir.resolve(fileName), code + sourceMapUrl);
    }

    void writeRuntime(Path outputDir) throws IOException {
        Path core = outputDir.resolve("kof-runtime.mjs");
        if (!Files.exists(core)) {
            // separate writes: a single concatenated constant would exceed the
            // JVM's 64KiB string / constant-pool limits
            Files.writeString(core, JsRuntimeCore.CORE_RUNTIME);
            Files.writeString(core, JsRuntimeUiComponents.UI_COMPONENT_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(core, JsRuntimeUiWidgets.UI_WIDGET_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(core, JsRuntimeUiLayout.UI_LAYOUT_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(core, JsRuntimeUiWeb.UI_WEB_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(core, JsRuntimeUiSupport.UI_SUPPORT_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(core, JsRuntimeUiSecurity.UI_SECURITY_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(core, JsRuntimeUiCrypto.UI_CRYPTO_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(core, JsRuntimeUiEvents.UI_EVENT_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
        }
        Path node = outputDir.resolve("kof-runtime-io.mjs");
        if (!Files.exists(node)) {
            Files.writeString(node, JsRuntimeIo.IO_RUNTIME);
        }
    }

    void writeHtmlEntry(Path outputDir, String moduleName) throws IOException {
        String entry = (moduleName.isEmpty() ? "Default" : moduleName.replace('.', '/')) + ".mjs";
        String title = moduleName.isEmpty() ? "Kof" : moduleName;
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>__TITLE__ — Kof</title>
                  <style>
                    :root {
                      --bg: #282a36; --fg: #f8f8f2; --panel: #21222c;
                      --border: #2e303e; --hover: #2e303e; --accent: #8be9fd;
                      --output: #50fa7b; --dim: #6272a4;
                    }
                    * { box-sizing: border-box; }
                    html, body { margin: 0; height: 100%; overflow: hidden; }
                    body {
                      background: var(--bg); color: var(--fg);
                      font-family: "Cascadia Code", "Fira Code", Consolas, Menlo, monospace;
                      display: flex; flex-direction: column;
                    }
                    #kof-titlebar {
                      display: flex; align-items: center; gap: 10px;
                      background: var(--panel); border-bottom: 1px solid var(--border);
                      padding: 7px 14px; font-size: 12px; user-select: none;
                    }
                    #kof-titlebar .dot { width: 11px; height: 11px; border-radius: 50%; display: inline-block; }
                    #kof-titlebar .dot.red { background: #ff5f57; }
                    #kof-titlebar .dot.yellow { background: #febc2e; }
                    #kof-titlebar .dot.green { background: #28c840; }
                    #kof-titlebar .name { color: var(--accent); font-weight: 600; }
                    #kof-titlebar .kind { color: var(--dim); margin-left: auto; }
                    #kof-root {
                      flex: 1; overflow: auto; padding: 14px 16px;
                      display: flex; flex-direction: column; gap: 2px;
                      background: var(--bg);
                    }
                    .kof-label {
                      font-size: 13px; line-height: 1.5; color: var(--output);
                      white-space: pre-wrap; word-break: break-word;
                    }
                    .kof-button {
                      font-size: 13px; padding: 6px 14px; cursor: pointer;
                      background: var(--panel); color: var(--fg);
                      border: 1px solid var(--border); border-radius: 6px;
                    }
                    .kof-button:hover { background: var(--hover); }
                    .kof-input {
                      font-size: 13px; padding: 6px 10px; width: 100%;
                      background: var(--panel); color: var(--fg);
                      border: 1px solid var(--border); border-radius: 6px;
                      font-family: inherit;
                    }
                    .kof-column { display: flex; flex-direction: column; gap: 8px; }
                    .kof-row { display: flex; flex-direction: row; gap: 8px; align-items: center; }
                    .kof-view { box-sizing: border-box; }
                    .kof-window {
                      box-sizing: border-box; padding: 16px; border-radius: 8px;
                      border: 1px solid var(--border); background: var(--bg);
                      display: flex; flex-direction: column; gap: 8px;
                    }
                    #kof-status {
                      background: var(--panel); border-top: 1px solid var(--border);
                      color: var(--dim); font-size: 11px; padding: 4px 14px;
                    }
                  </style>
                </head>
                <body>
                  <div id="kof-titlebar">
                    <span class="dot red"></span><span class="dot yellow"></span><span class="dot green"></span>
                    <span class="name">__TITLE__</span><span class="kind">Kof output</span>
                  </div>
                  <div id="kof-root"></div>
                  <div id="kof-status">terminated</div>
                  <script type="module" src="__ENTRY__"></script>
                </body>
                </html>
                """.replace("__TITLE__", title).replace("__ENTRY__", entry);
        Files.writeString(outputDir.resolve("index.html"), html);
    }

    void writeSourceMap(IRModule module, Path outputDir, String fileName,
                        List<JsIr.JsFunctionLine> functionLines) throws IOException {
        String source = module.name().isEmpty() ? "Default.kf" : module.name() + ".kf";
        String mappings = buildSourceMapMappings(functionLines);
        String map = "{\"version\":3,\"file\":\"" + fileName
                + "\",\"sources\":[\"" + source + "\"],\"sourcesContent\":null"
                + ",\"names\":[],\"mappings\":\"" + mappings + "\"}";
        Files.writeString(outputDir.resolve(fileName + ".map"), map);
    }

    /**
     * Mappings VLQ (source map V3, formato padrão) — mapeamento de nível de
     * linha: cada linha gerada com mapeamento tem um segmento
     * {@code [genCol=0, srcIdx=0, srcLine(0-based), srcCol=0]}. As linhas geradas
     * são entradas separadas por {@code ';'} (linhas sem mapeamento ficam
     * vazias); {@code srcIdx}/{@code srcLine}/{@code srcCol} são deltas
     * acumulativos entre segmentos; {@code genCol} zera a cada linha.
     */
    private static String buildSourceMapMappings(List<JsIr.JsFunctionLine> lines) {
        if (lines == null || lines.isEmpty()) return "";
        java.util.TreeMap<Integer, Integer> byGen = new java.util.TreeMap<>();
        for (JsIr.JsFunctionLine fl : lines) {
            if (fl.generatedLine() > 1 && fl.kofLine() > 0) {
                byGen.putIfAbsent(fl.generatedLine(), fl.kofLine());
            }
        }
        if (byGen.isEmpty()) return "";
        int maxGen = byGen.lastKey();
        java.util.List<String> entries = new java.util.ArrayList<>(maxGen);
        int prevSrcLine0 = 0;   // linha 0-based acumulativa entre segmentos
        for (int g = 1; g <= maxGen; g++) {
            Integer src = byGen.get(g);
            if (src == null) {
                entries.add("");
                continue;
            }
            int srcLine0 = src - 1;
            entries.add(vlq(0) + vlq(0) + vlq(srcLine0 - prevSrcLine0) + vlq(0));
            prevSrcLine0 = srcLine0;
        }
        return String.join(";", entries);
    }

    /** VLQ base64 do source map (RFC 3436 + tabela do source map V3). */
    private static final String VLQ_B64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private static String vlq(int value) {
        int v = (value < 0) ? (((-value) << 1) | 1) : (value << 1);
        StringBuilder out = new StringBuilder();
        while (true) {
            int digit = v & 31;
            v >>= 5;
            if (v > 0) digit |= 32;
            out.append(VLQ_B64.charAt(digit));
            if (v == 0) break;
        }
        return out.toString();
    }
}
