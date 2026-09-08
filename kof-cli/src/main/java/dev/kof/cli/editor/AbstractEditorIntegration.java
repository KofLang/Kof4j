package dev.kof.cli.editor;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base para providers: detecta via executável no PATH (candidatos) e/ou
 * diretório de configuração, e extrai a versão do texto de {@code --version}
 * com um padrão. Cada subclasse só declara os nomes/probes específicos.
 */
public abstract class AbstractEditorIntegration implements EditorIntegration {

    /** Executáveis candidatos no PATH, em ordem de prioridade. */
    protected abstract List<String> executables();

    /** Diretórios de config que indicam o editor (relativos ao HOME). */
    protected abstract List<String> configDirs();

    /** Padrão que extrai a versão do texto de --version (grupo 1). */
    protected abstract Pattern versionPattern();

    /** true se a integração oficial (grammar/LSP) está disponível p/ este editor. */
    protected boolean integrationAvailable() { return true; }

    /** true se a integração já está instalada (config presente). Degrau 3 refina. */
    protected boolean integrationInstalled(DetectContext ctx, Path foundPath) {
        for (String dir : configDirs()) {
            Path home = ctx.home();
            if (home != null && ctx.dirExists(home.resolve(dir))) return true;
        }
        return false;
    }

    @Override
    public EditorInfo detect(DetectContext ctx) {
        String exe = null;
        for (String cand : executables()) {
            String p = ctx.whichPath(cand);
            if (p != null) { exe = cand; break; }
        }
        // o marker vale mesmo com o editor ausente do PATH (install pode ser
        // feito proativamente; status deve refletir o que foi escrito).
        boolean integrationInstalled = integrationInstalled(ctx, null);
        if (exe == null) {
            return new EditorInfo(id(), displayName(), "unknown", null,
                    integrationAvailable(), integrationInstalled);
        }
        String version = parseVersion(ctx.readVersion(exe));
        String path = ctx.whichPath(exe);
        return new EditorInfo(id(), displayName(), version, path, integrationAvailable(), integrationInstalled);
    }

    private String parseVersion(String raw) {
        if (raw == null) return "unknown";
        Matcher m = versionPattern().matcher(raw);
        if (m.find()) return m.group(1);
        return "unknown";
    }
}
