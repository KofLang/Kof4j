package dev.kof.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code kof connector init <dir>} — o gerador de conectores do plano Kof
 * Connector Ecosystem (§8, {@code D-CONNECTORS-GO}). A politica vive na lib Kof
 * pura {@code libs/interop} ({@code interop.ConnectorTemplate} renderiza o
 * manifest canonico §4.2); esta classe so fornece o mecanismo de terminal —
 * resolve a lib, compila um main gerado minimo, executa na JVM (JVM-first) e
 * cria a estrutura de pastas §8.
 *
 * <pre>
 *   kof connector init &lt;dir&gt; [--name N] [--language L] [--version V] [--abi A] [--runtime R]
 * </pre>
 *
 * CONNECTOR001 (R6): diretorio ausente, flag desconhecida, manifest ja existente
 * ou lib Kof ausente → diagnostico explicito e exit != 0 — nunca silencio.
 */
final class CmdConnector {

    private static final List<String> DIRS = List.of("bindings", "runtime", "types", "tests", "docs");

    private CmdConnector() {}

    static int run(String[] args) {
        if (args.length < 2 || "--help".equals(args[1]) || "help".equals(args[1])) {
            System.err.println("usage: kof connector init <dir> [--name N] [--language L] [--version V] [--abi A] [--runtime R]");
            return 1;
        }
        String verb = args[1];
        if (!"init".equals(verb)) {
            System.err.println("connector: unknown subcommand: " + verb + " (accepts: init) [CONNECTOR001]");
            return 1;
        }
        if (args.length < 3) {
            System.err.println("usage: kof connector init <dir> [--name N] [--language L] [--version V] [--abi A] [--runtime R]");
            return 1;
        }
        Path dir = Path.of(args[2]);
        String name = dir.getFileName() == null ? "kof-connector" : dir.getFileName().toString();
        String language = "c";
        String version = "0.1.0";
        String abi = "c";
        String runtime = "native";
        for (int i = 3; i < args.length; i++) {
            String a = args[i];
            String value = null;
            String key = a;
            int eq = a.indexOf('=');
            if (eq > 0) {
                key = a.substring(0, eq);
                value = a.substring(eq + 1);
            }
            switch (key) {
                case "--name" -> name = value != null ? value : next(args, ++i);
                case "--language" -> language = value != null ? value : next(args, ++i);
                case "--version" -> version = value != null ? value : next(args, ++i);
                case "--abi" -> abi = value != null ? value : next(args, ++i);
                case "--runtime" -> runtime = value != null ? value : next(args, ++i);
                default -> {
                    System.err.println("connector: unknown flag '" + a + "' [CONNECTOR001]");
                    return 1;
                }
            }
        }
        if (name == null || language == null || version == null || abi == null || runtime == null) {
            System.err.println("connector: option needs a value [CONNECTOR001]");
            return 1;
        }
        Path manifest = dir.resolve("kof-connector.toml");
        try {
            if (Files.exists(manifest)) {
                System.err.println("connector: " + manifest + " already exists [CONNECTOR001]");
                return 1;
            }
            Files.createDirectories(dir);
            for (String d : DIRS) {
                Files.createDirectories(dir.resolve(d));
            }
            Files.writeString(dir.resolve("docs/README.md"), docsReadme(name, language, runtime));
            String rendered = render(name, language, version, abi, runtime, manifest);
            if (rendered == null) {
                System.err.println("connector: CONNECTOR001 — interop library not found"
                        + " (libs/interop, $kof.install.dir/lib/kof-libs or packaged resources)");
                return 1;
            }
            System.out.println("created connector at " + dir.toAbsolutePath().normalize()
                    + "\nnext steps:\n  edit " + manifest.getFileName()
                    + "\n  kof connector init <other>");
            return 0;
        } catch (Exception e) {
            System.err.println("connector: " + e.getMessage() + " [CONNECTOR001]");
            return 1;
        }
    }

    private static String next(String[] args, int index) {
        return index < args.length ? args[index] : null;
    }

    /**
     * Gera um main minimo que usa o {@code ConnectorTemplate} puro-Kof, compila
     * na JVM e o executa; o manifest e escrito pelo proprio Kof. Devolve o texto
     * renderizado (lido de volta) ou null quando a lib/compilacao falha.
     */
    private static String render(String name, String language, String version,
                                 String abi, String runtime, Path manifest) throws Exception {
        String source = "import interop.ConnectorTemplate\n\n"
                + "main() {\n"
                + "    var t = ConnectorTemplate(\"" + literal(name) + "\", \""
                + literal(language) + "\", \"" + literal(version) + "\", \""
                + literal(abi) + "\", \"" + literal(runtime) + "\")\n"
                + "    File(\"" + literal(manifest.toAbsolutePath().normalize().toString())
                + "\").writeText(t.render())\n"
                + "    println(t.render())\n"
                + "}\n";
        Path srcRoot = Files.createTempDirectory("kof-connector-src-");
        Path outRoot = Files.createTempDirectory("kof-connector-out-");
        try {
            URLClassLoader loader = InteropLibrary.compile("Main.kf", source, srcRoot, outRoot);
            if (loader == null) {
                return null;
            }
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            PrintStream realOut = System.out;
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            try (loader) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } finally {
                System.setOut(realOut);
            }
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            KofCliSupport.cleanup(srcRoot);
            KofCliSupport.cleanup(outRoot);
        }
    }

    private static String docsReadme(String name, String language, String runtime) {
        return "# " + name + " connector\n\n"
                + "Generated by `kof connector init` (Kof Connector Ecosystem plan, §8).\n\n"
                + "- `kof-connector.toml` — the declarative manifest (§4.2), read by `interop.ConnectorManifest`.\n"
                + "- `bindings/` — the generated foreign bindings.\n"
                + "- `runtime/` — the runtime shims for " + language + " on " + runtime + ".\n"
                + "- `types/` — the interop type mappings.\n"
                + "- `tests/` — the cross-language suite (§10).\n"
                + "- `docs/` — this documentation.\n";
    }

    private static String literal(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
