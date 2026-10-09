package dev.kof.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * #566, opção (b) (DECISIONS.md, D-RELEASE-0.5.0-GATE addendum): um pacote publicado por
 * {@code kof deploy --publish} é consumido como MÓDULO-FONTE. O tar.gz carrega as fontes em
 * {@code src/<caminho>.kf}; o {@code kof deps resolve} as instala VERIFICADAS em
 * {@code <cache>/kof/<owner>/<repo>/<ver>/src/} e {@code kof run/build --deps} entregam essas
 * raízes ao compilador (onde o {@code import} as procura). Integridade não é opcional (R6):
 * cada fonte tem de estar no {@code SHA256SUMS} e conferir; só {@code .kf/.kof} entra.
 */
final class DepsSources {

    private DepsSources() {}

    /** Diretório das fontes dentro do pacote e da versão instalada. */
    static final String DIR = "src";

    /** A versão instalada já tem fontes (o jar é opcional num pacote-biblioteca). */
    static boolean hasSources(Path versionDir) {
        return Files.isDirectory(versionDir.resolve(DIR));
    }

    /** Raízes de fonte instaladas dos deps {@code owner/repo@ver} do {@code kofdeps} do projeto. */
    static List<Path> roots(Path projectDir) throws IOException {
        Path file = projectDir.resolve(Deps.DEPS_FILE);
        List<Path> roots = new ArrayList<>();
        if (!Files.exists(file)) return roots;
        for (String l : Files.readAllLines(file)) {
            String s = l.trim();
            if (s.isEmpty() || !DepsRegistry.isRegistrySpec(s)) continue;
            String ver = DepsRegistry.versionOf(s);
            if (ver == null) continue;                       // latest sem resolve previo: honesto
            Path versionDir = DepsRegistry.jarPath(DepsRegistry.ownerOf(s),
                    DepsRegistry.repoOf(s), ver).getParent();
            if (hasSources(versionDir) && !roots.contains(versionDir.resolve(DIR))) {
                roots.add(versionDir.resolve(DIR));
            }
        }
        return roots;
    }

    /**
     * Confere as fontes extraídas em {@code extracted/src} contra o {@code SHA256SUMS} e as instala
     * em {@code versionDir/src}. Devolve false se o pacote não tem fontes. Lança REG002 (soma
     * violada) ou REG004 (fonte fora do SHA256SUMS, arquivo que não é .kf/.kof, ou listada e
     * ausente) — e nada é instalado.
     */
    static boolean install(Path sumsFile, Path extracted, Path versionDir) throws IOException {
        Map<String, String> sums = parseSums(sumsFile);
        Set<String> listed = new HashSet<>();
        for (String name : sums.keySet()) if (name.startsWith(DIR + "/")) listed.add(name);
        Path srcTmp = extracted.resolve(DIR);
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(srcTmp)) {
            try (var walk = Files.walk(srcTmp)) {
                walk.filter(Files::isRegularFile).sorted().forEach(files::add);
            }
        }
        if (files.isEmpty()) {
            if (!listed.isEmpty()) {
                throw new IOException("REG004: SHA256SUMS lists sources that are not in the package"
                        + " — refusing to install");
            }
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (Path p : files) {
            String rel = DIR + "/" + srcTmp.relativize(p).toString().replace('\\', '/');
            if (!rel.endsWith(".kf") && !rel.endsWith(".kof") && !rel.endsWith(".ptkf")) {
                throw new IOException("REG004: unexpected file in the package sources: " + rel
                        + " (only .kf/.kof/.ptkf are installed) — refusing to install");
            }
            String want = sums.get(rel);
            if (want == null) {
                throw new IOException("REG004: source " + rel + " not listed in SHA256SUMS"
                        + " (integrity unverifiable) — refusing to install");
            }
            String actual = CmdDeploy.sha256Hex(p);
            if (!actual.equalsIgnoreCase(want)) {
                throw new IOException("REG002: SHA256 mismatch for " + rel + " (package is corrupt"
                        + " or was tampered after publish — expected " + want + ", got " + actual + ")");
            }
            seen.add(rel);
        }
        for (String name : listed) {
            if (!seen.contains(name)) {
                throw new IOException("REG004: " + name + " is listed in SHA256SUMS but missing from"
                        + " the package — refusing to install");
            }
        }
        // instala por copia + rename (o temp pode estar em outro filesystem; o rename e atomico)
        Files.createDirectories(versionDir);
        Path staging = versionDir.resolve(DIR + ".installing");
        deleteTree(staging);
        for (Path p : files) {
            Path dst = staging.resolve(srcTmp.relativize(p).toString());
            Files.createDirectories(dst.getParent());
            Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
        }
        Path target = versionDir.resolve(DIR);
        deleteTree(target);
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    /** `SHA256SUMS` (coreutils: `<hex>  <nome>`) → nome normalizado ("/"; sem `*`) → hex. */
    private static Map<String, String> parseSums(Path sumsFile) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(sumsFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length != 2) continue;
            String name = parts[1].trim().replaceFirst("^\\*?", "").replace('\\', '/');
            out.put(name, parts[0].trim());
        }
        return out;
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
