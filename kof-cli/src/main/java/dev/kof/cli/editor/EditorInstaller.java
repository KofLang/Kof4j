package dev.kof.cli.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Instalação idempotente de integrações (EDI001, degrau 3). Escreve os
 * {@link EditorFile} de um provider sob um {@code home} (o real, ou um
 * tempDir nos testes — §24: a suíte nunca toca o ambiente do usuário).
 *
 * <p>Idempotência: só escreve quando o conteúdo difere; um marker em
 * {@code .config/kof/editors/<id>.installed} registra o que foi escrito,
 * permitindo {@link #uninstall} reverter exatamente aquilo (nunca apaga
 * arquivo que o Kof não criou).
 */
public final class EditorInstaller {

    private EditorInstaller() {}

    public sealed interface Result permits Installed, Unchanged, Removed, Failed {}
    public record Installed(List<String> written) implements Result {}
    public record Unchanged() implements Result {}
    public record Removed(List<String> deleted) implements Result {}
    public record Failed(String message) implements Result {}

    private static Path markerDir(Path home) {
        return home.resolve(".config").resolve("kof").resolve("editors");
    }

    private static Path marker(Path home, String id) {
        return markerDir(home).resolve(id + ".installed");
    }

    public static boolean isInstalled(Path home, String id) {
        return Files.isRegularFile(marker(home, id));
    }

    /** Escreve os arquivos do provider sob {@code home}. Idempotente. */
    public static Result install(EditorIntegration provider, DetectContext ctx, Path home) {
        try {
            List<EditorFile> files = provider.integrationFiles(ctx);
            if (files.isEmpty()) {
                return new Failed("nenhum arquivo de integração para " + provider.id()
                        + " (provider ainda sem conteúdo idiomático)");
            }
            List<String> written = new ArrayList<>();
            boolean anyChanged = false;
            for (EditorFile f : files) {
                Path target = home.resolve(f.relativePath());
                byte[] want = f.content().getBytes(StandardCharsets.UTF_8);
                boolean differs = !Files.isRegularFile(target)
                        || !java.util.Arrays.equals(want, Files.readAllBytes(target));
                if (differs) {
                    Files.createDirectories(target.getParent());
                    Path tmp = target.resolveSibling(target.getFileName() + ".kof-tmp");
                    Files.write(tmp, want);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    written.add(f.relativePath());
                    anyChanged = true;
                }
            }
            Files.createDirectories(markerDir(home));
            StringBuilder sb = new StringBuilder();
            for (EditorFile f : files) sb.append(f.relativePath()).append('\n');
            Files.writeString(marker(home, provider.id()), sb.toString());
            return anyChanged ? new Installed(written) : new Unchanged();
        } catch (IOException e) {
            return new Failed(e.getMessage());
        }
    }

    /** Remove exatamente o que o marker registra (nunca apaga o que não é nosso). */
    public static Result uninstall(EditorIntegration provider, Path home) {
        Path m = marker(home, provider.id());
        if (!Files.isRegularFile(m)) return new Unchanged();
        try {
            List<String> deleted = new ArrayList<>();
            for (String rel : Files.readAllLines(m)) {
                if (rel.isBlank()) continue;
                Path target = home.resolve(rel);
                if (Files.deleteIfExists(target)) deleted.add(rel);
            }
            Files.deleteIfExists(m);
            return new Removed(deleted);
        } catch (IOException e) {
            return new Failed(e.getMessage());
        }
    }
}
