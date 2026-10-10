package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fase 2 (plataforma): parser MÍNIMO de {@code kof.toml} — subconjunto INI
 * do TOML: seções {@code [nome]} e pares {@code chave = valor} (string
 * entre aspas ou número). Sem dependência nova.
 *
 * Seções conhecidas (docs/development/DECISIONS.md §D-APP.REF):
 * <pre>
 * [project]  name = "my-app"
 * [backend]  target = "jvm" | "native" | "script" | ...
 * [frontend] target = "kofjs" | "script" | ...
 * [server]   port = 8080
 * [sources]  app = "src/main/kof"   # #708: separate app/test roots
 *            test = "src/test/kof"
 * </pre>
 *
 * Chaves desconhecidas viram WARNING (honesto, não silencioso) — nunca
 * erro: permite evolução do formato sem quebrar projetos existentes.
 * Arquivo ausente → {@link #empty()} (projeto sem manifesto = comportamento
 * atual; a Fase 1 já trata isso).
 */
public final class KofProjectConfig {

    private final String projectName;
    private final String backendTarget;
    private final String frontendTarget;
    private final Integer serverPort;
    private final String sourceApp;
    private final String sourceTest;
    private final List<String> warnings;

    private KofProjectConfig(String projectName, String backendTarget,
                             String frontendTarget, Integer serverPort,
                             String sourceApp, String sourceTest,
                             List<String> warnings) {
        this.projectName = projectName;
        this.backendTarget = backendTarget;
        this.frontendTarget = frontendTarget;
        this.serverPort = serverPort;
        this.sourceApp = sourceApp;
        this.sourceTest = sourceTest;
        this.warnings = warnings;
    }

    public String projectName() { return projectName; }
    public String backendTarget() { return backendTarget; }
    public String frontendTarget() { return frontendTarget; }
    public Integer serverPort() { return serverPort; }
    public String sourceApp() { return sourceApp; }
    public String sourceTest() { return sourceTest; }
    public List<String> warnings() { return warnings; }

    /**
     * #708: resolve a raiz de fontes declarada em {@code [sources]} contra a
     * raiz do projeto. Sem manifesto ou sem a chave → {@code fallback}
     * (comportamento atual inalterado). Caminho declarado é um diretório
     * relativo à raiz do projeto (ou absoluto).
     */
    public static Path resolveSourceRoot(Path projectRoot, String declared, Path fallback) {
        if (declared == null || declared.isBlank()) return fallback;
        Path p = Path.of(declared);
        Path resolved = p.isAbsolute() || projectRoot == null
                ? p : projectRoot.resolve(p);
        return resolved.toAbsolutePath().normalize();
    }

    /** Configuração vazia (sem manifesto). */
    public static KofProjectConfig empty() {
        return new KofProjectConfig(null, null, null, null, null, null, List.of());
    }

    /** Lê kof.toml da raiz do projeto; ausente/ilegível → empty + warning. */
    public static KofProjectConfig load(Path projectRoot) {
        Path manifest = projectRoot.resolve(ProjectLocator.MANIFEST);
        if (!Files.isRegularFile(manifest)) return empty();
        try {
            return parse(Files.readString(manifest));
        } catch (IOException e) {
            return new KofProjectConfig(null, null, null, null, null, null,
                    List.of("unreadable kof.toml: " + e.getMessage()));
        }
    }

    /**
     * Faz o parse do texto. Linhas vazias e comentários (#) são ignoradas.
     * Formato inválido (linha fora de seção, sem '=', valor não numérico em
     * port) → warning, nunca exceção.
     */
    public static KofProjectConfig parse(String text) {
        String section = "";
        String name = null, backend = null, frontend = null;
        String sourceApp = null, sourceTest = null;
        Integer port = null;
        List<String> warnings = new ArrayList<>();
        int lineNo = 0;
        for (String raw : text.split("\n")) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                warnings.add("line " + lineNo + ": expected 'key = value', found '"
                        + line + "'");
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = unquote(line.substring(eq + 1).trim());
            switch (section) {
                case "project" -> {
                    if (key.equals("name")) name = val;
                    else warnUnknown(warnings, section, key);
                }
                case "backend" -> {
                    if (key.equals("target")) backend = val;
                    else warnUnknown(warnings, section, key);
                }
                case "frontend" -> {
                    if (key.equals("target")) frontend = val;
                    else warnUnknown(warnings, section, key);
                }
                case "server" -> {
                    if (key.equals("port")) {
                        try {
                            port = Integer.parseInt(val);
                        } catch (NumberFormatException e) {
                            warnings.add("[server] port must be a number, found '" + val + "'");
                        }
                    } else warnUnknown(warnings, section, key);
                }
                case "sources" -> {
                    // #708: raízes separadas de app/teste (caminhos relativos
                    // à raiz do projeto). Vazio = comportamento atual.
                    if (key.equals("app")) sourceApp = val;
                    else if (key.equals("test")) sourceTest = val;
                    else warnUnknown(warnings, section, key);
                }
                default -> warnings.add("line " + lineNo + ": unknown section '["
                        + section + "]' (key '" + key + "' ignored)");
            }
        }
        return new KofProjectConfig(name, backend, frontend, port, sourceApp, sourceTest, warnings);
    }

    private static void warnUnknown(List<String> warnings, String section, String key) {
        warnings.add("[" + section + "] unknown key '" + key + "' ignored");
    }

    private static String unquote(String v) {
        if (v.startsWith("\"")) {
            int close = v.indexOf('"', 1);
            if (close > 0) {
                // descarta resto da linha (comentário inline: name = "x" # ...)
                return v.substring(1, close);
            }
            return v.substring(1);
        }
        int hash = v.indexOf('#');
        if (hash >= 0) v = v.substring(0, hash).trim();
        return v;
    }
}
