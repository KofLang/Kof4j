package dev.kof.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * `kof-test.kofmd` — the project manifest that declares the browser testing
 * provider (D-MAINT-BATCH-0610B/C as refined by the maintainer's third chat
 * poll, 10/10/2026; the §6 provider slice of the testing-platform plan).
 *
 * <p>The "opt-in per project, CLI does not bundle" half is the landed policy;
 * this class is the declaration half: a manifest file at the project root
 * declares the provider and its version, the CLI reads it and gates. Explicit
 * and versioned — no command flags, no environment variables, no bundle.
 *
 * <p>Format: the repo's own compressed-doc shape — non-empty lines that are
 * not comments are `key: value` pairs; `provider` selects the provider
 * (e.g. `playwright`), `version` pins it (e.g. `1.64.0`). Unknown keys are
 * warnings, never exceptions (the KofProjectConfig rule). A missing manifest
 * = no provider declared = the browser tests are not gated by one (the CLI
 * reports SKIP with a named reason when a browser test needs one).
 *
 * <p>The gate contract: a declared provider's binary must be probeable
 * (`npx playwright --version`) before any browser test runs; absent → the
 * honest skip with the named reason — never a false green, never a silent
 * fallback (Q7).
 */
public final class KofTestManifest {

    private final String provider;
    private final String version;
    private final List<String> warnings;

    private KofTestManifest(String provider, String version, List<String> warnings) {
        this.provider = provider;
        this.version = version;
        this.warnings = warnings;
    }

    private static final String MANIFEST = "kof-test.kofmd";

    public static KofTestManifest empty() {
        return new KofTestManifest(null, null, new ArrayList<>());
    }

    public String provider() { return provider; }
    public String version() { return version; }
    public List<String> warnings() { return warnings; }

    public boolean declaresProvider() {
        return provider != null && !provider.isBlank();
    }

    /** Lê kof-test.kofmd subindo a partir de {@code start} (inclusive) até a
     *  raiz do filesystem — o mesmo padrão do ProjectLocator, MAS para o
     *  manifesto de teste (o locate só procura kof.toml). Ausente/ilegível →
     *  empty + warning. */
    public static KofTestManifest load(Path start) {
        if (start == null) return empty();
        Path dir;
        try {
            dir = start.toAbsolutePath().normalize();
        } catch (Exception e) {
            return empty();
        }
        for (int i = 0; i < 64 && dir != null; i++) {
            Path manifest = dir.resolve(MANIFEST);
            if (Files.isRegularFile(manifest)) {
                try {
                    return parse(Files.readString(manifest));
                } catch (java.io.IOException e) {
                    List<String> w = new ArrayList<>();
                    w.add("unreadable " + MANIFEST + ": " + e.getMessage());
                    return new KofTestManifest(null, null, w);
                }
            }
            dir = dir.getParent();
        }
        return empty();
    }

    /** Faz o parse do texto. Linhas vazias e comentários (#) são ignoradas. */
    public static KofTestManifest parse(String text) {
        String provider = null;
        String version = null;
        List<String> warnings = new ArrayList<>();
        int lineNo = 0;
        for (String raw : text.split("\n")) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon < 0) {
                warnings.add(MANIFEST + " line " + lineNo + ": expected 'key: value', found '"
                        + line + "'");
                continue;
            }
            String key = line.substring(0, colon).trim();
            String val = unquote(line.substring(colon + 1).trim());
            switch (key) {
                case "provider" -> provider = val;
                case "version" -> version = val;
                default -> warnings.add(MANIFEST + ": unknown key '" + key + "' (accepts: provider, version)");
            }
        }
        return new KofTestManifest(provider, version, warnings);
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
