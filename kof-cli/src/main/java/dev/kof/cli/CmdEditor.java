package dev.kof.cli;

import dev.kof.cli.editor.DetectContext;
import dev.kof.cli.editor.EditorInfo;
import dev.kof.cli.editor.EditorIntegration;
import dev.kof.cli.editor.EditorInstaller;
import dev.kof.cli.editor.EditorRegistry;
import dev.kof.compiler.KofVersion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code kof editor} — infraestrutura de integração de editores (EDI001,
 * §6/§11/§12). Read-only: list/detect/status. Escrita: install/uninstall/
 * setup/update — idempotentes e com CONSENTIMENTO quando alteram o ambiente
 * interativamente (§12: nunca instalar silencioso).
 */
final class CmdEditor {

    private CmdEditor() {}

    static int run(String[] args) {
        DetectContext ctx = DetectContext.system();
        Path home = ctx.home();
        return run(args, ctx, home, new BufferedReader(new InputStreamReader(System.in)),
                System.out, System.err);
    }

    /**
     * Hook pós-instalador (EDI001 §13): oferece as integrações recomendadas
     * logo após `kof install`. NUNCA bloqueia a instalação: sem console
     * (headless/CI/pipe) só aponta o comando; recusou → `kof editor setup`.
     */
    static void offerAfterInstall() {
        DetectContext ctx = DetectContext.system();
        offerAfterInstall(ctx, ctx.home(),
                new BufferedReader(new InputStreamReader(System.in)),
                System.out, System.err, System.console() != null);
    }

    /** Testável: tudo injetado, inclusive a flag de console interativo. */
    static int offerAfterInstall(DetectContext ctx, Path home, BufferedReader in,
                                 PrintStream out, PrintStream err, boolean interactive) {
        if (home == null) return 0;
        java.util.List<EditorIntegration> recommended = new ArrayList<>();
        for (EditorIntegration e : EditorRegistry.all()) {
            EditorInfo info = e.detect(ctx);
            if (info.installed() && !EditorInstaller.isInstalled(home, e.id())) recommended.add(e);
        }
        if (recommended.isEmpty()) return 0;
        out.println();
        out.println("We detected editors without Kof integration:");
        for (EditorIntegration e : recommended) out.println("  [✓] " + e.displayName());
        if (!interactive) {
            out.println();
            out.println("Install them with:  kof editor setup");
            return 0;
        }
        out.print("Install recommended integrations now? [Y/n] ");
        out.flush();
        String ans = readLine(in);
        if (ans != null && !ans.isBlank() && !ans.trim().toLowerCase().startsWith("y")) {
            out.println("You can install them later with:  kof editor setup");
            return 0;
        }
        int rc = 0;
        for (EditorIntegration e : recommended) {
            rc |= report(e, doInstallResult(e, ctx, home), out, err, "setup");
        }
        return rc;
    }

    /** Testável: contexto, home, stdin e saídas injetáveis (§24). */
    static int run(String[] args, DetectContext ctx, Path home, BufferedReader in,
                   PrintStream out, PrintStream err) {
        if (args.length < 2 || "--help".equals(args[1]) || "-h".equals(args[1])) {
            usage(out);
            return 0;
        }
        return switch (args[1]) {
            case "list" -> list(out);
            case "detect" -> detect(ctx, out);
            case "status" -> status(ctx, out);
            case "install" -> install(args, ctx, home, out, err);
            case "uninstall" -> uninstall(args, ctx, home, out, err);
            case "setup" -> setup(ctx, home, in, out, err);
            case "update" -> update(ctx, home, out, err);
            default -> {
                err.println("kof editor: subcomando desconhecido '" + args[1] + "'");
                usage(err);
                yield 1;
            }
        };
    }

    private static void usage(PrintStream out) {
        out.println("usage: kof editor <subcommand>");
        out.println("  list                 integrações oficiais disponíveis");
        out.println("  detect               editores instalados + integrações");
        out.println("  status               ambiente de edição Kof (detalhado)");
        out.println("  install <editor>     instala a integração de um editor");
        out.println("  uninstall <editor>   remove a integração instalada");
        out.println("  setup                detecta e instala as recomendadas (com consentimento)");
        out.println("  update               re-sincroniza integrações instaladas");
    }

    private static int list(PrintStream out) {
        out.println("Available integrations:");
        for (EditorIntegration e : EditorRegistry.all()) {
            out.println("  ✓ " + e.integrationName() + "  (kof editor install " + e.id() + ")");
        }
        return 0;
    }

    private static int detect(DetectContext ctx, PrintStream out) {
        out.println("Kof Editor Integration");
        out.println();
        out.println("Detected editors:");
        for (EditorIntegration e : EditorRegistry.all()) {
            out.println("  " + (e.detect(ctx).installed() ? "✓" : "✗") + " " + e.displayName());
        }
        out.println();
        out.println("Available integrations:");
        for (EditorIntegration e : EditorRegistry.all()) {
            out.println("  ✓ " + e.integrationName());
        }
        out.println();
        out.println("Use:");
        out.println();
        out.println("    kof editor setup");
        out.println();
        out.println("to install recommended integrations.");
        return 0;
    }

    private static int status(DetectContext ctx, PrintStream out) {
        out.println("Kof Editor Environment");
        out.println();
        out.println("Kof:");
        out.println("  version: " + KofVersion.version());
        out.println("  compiler: OK");
        out.println("  LSP: OK (kof lsp)");
        out.println("  formatter: OK (kof fmt)");
        out.println("  debugger: PARTIAL (kof debug — DAP em evolução)");
        out.println();
        out.println("Editors:");
        List<EditorIntegration> all = EditorRegistry.all();
        for (int i = 0; i < all.size(); i++) {
            EditorIntegration e = all.get(i);
            EditorInfo info = e.detect(ctx);
            out.println((info.installed() ? "✓ " : "✗ ") + e.displayName());
            if (info.installed()) {
                out.println("  version: " + info.version());
                out.println("  path: " + info.path());
            }
            out.println("  integration: " + (info.integrationAvailable() ? "available" : "none")
                    + (info.integrationInstalled() ? " (installed)" : " (not installed)"));
            if (i < all.size() - 1) out.println();
        }
        return 0;
    }

    private static int install(String[] args, DetectContext ctx, Path home,
                               PrintStream out, PrintStream err) {
        if (args.length < 3) { err.println("usage: kof editor install <editor>"); return 1; }
        EditorIntegration e = EditorRegistry.byId(args[2]);
        if (e == null) { err.println("kof editor: editor desconhecido '" + args[2] + "'"); return 1; }
        return doInstall(e, ctx, home, out, err);
    }

    private static int uninstall(String[] args, DetectContext ctx, Path home,
                                 PrintStream out, PrintStream err) {
        if (args.length < 3) { err.println("usage: kof editor uninstall <editor>"); return 1; }
        EditorIntegration e = EditorRegistry.byId(args[2]);
        if (e == null) { err.println("kof editor: editor desconhecido '" + args[2] + "'"); return 1; }
        if (home == null) { err.println("kof editor: HOME indisponível"); return 1; }
        EditorInstaller.Result r = EditorInstaller.uninstall(e, home);
        return report(e, r, out, err, "uninstall");
    }

    private static int update(DetectContext ctx, Path home, PrintStream out, PrintStream err) {
        if (home == null) { err.println("kof editor: HOME indisponível"); return 1; }
        int rc = 0;
        for (EditorIntegration e : EditorRegistry.all()) {
            if (EditorInstaller.isInstalled(home, e.id())) {
                rc |= report(e, doInstallResult(e, ctx, home), out, err, "update");
            }
        }
        return rc;
    }

    private static int setup(DetectContext ctx, Path home, BufferedReader in,
                             PrintStream out, PrintStream err) {
        if (home == null) { err.println("kof editor: HOME indisponível"); return 1; }
        // 1-3: detectar editores + integrações existentes
        List<EditorIntegration> recommended = new ArrayList<>();
        out.println("Kof Editor Setup");
        out.println();
        out.println("Detected editors:");
        for (EditorIntegration e : EditorRegistry.all()) {
            EditorInfo info = e.detect(ctx);
            if (!info.installed()) continue;
            boolean already = EditorInstaller.isInstalled(home, e.id());
            out.println("  ✓ " + e.displayName()
                    + (already ? "  (integration installed)" : ""));
            if (!already) recommended.add(e);
        }
        if (recommended.isEmpty()) {
            out.println();
            out.println("Nada a instalar (nenhum editor detectado sem integração).");
            return 0;
        }
        // 5: recomendações
        out.println();
        out.println("Recommended Kof integrations:");
        for (EditorIntegration e : recommended) out.println("  [✓] " + e.displayName());
        // 6: CONSENTIMENTO (§12 — nunca silencioso)
        out.println();
        out.print("Install recommended integrations now? [Y/n] ");
        out.flush();
        String ans = readLine(in);
        if (ans != null && !ans.isBlank() && !ans.trim().toLowerCase().startsWith("y")) {
            out.println("Ok — você pode instalar depois com:  kof editor setup");
            return 0;
        }
        // 7-10: instalar + validar + resultado
        int rc = 0;
        for (EditorIntegration e : recommended) {
            rc |= report(e, doInstallResult(e, ctx, home), out, err, "setup");
        }
        return rc;
    }

    private static EditorInstaller.Result doInstallResult(EditorIntegration e, DetectContext ctx, Path home) {
        if (home == null) return new EditorInstaller.Failed("HOME indisponível");
        return EditorInstaller.install(e, ctx, home);
    }

    private static int doInstall(EditorIntegration e, DetectContext ctx, Path home,
                                 PrintStream out, PrintStream err) {
        return report(e, doInstallResult(e, ctx, home), out, err, "install");
    }

    private static int report(EditorIntegration e, EditorInstaller.Result r,
                              PrintStream out, PrintStream err, String verb) {
        if (r instanceof EditorInstaller.Installed inst) {
            out.println(e.displayName() + ": integração " + verb + " (" + inst.written().size()
                    + " arquivo(s))");
            return 0;
        } else if (r instanceof EditorInstaller.Unchanged) {
            out.println(e.displayName() + ": já atualizado (nenhuma mudança)");
            return 0;
        } else if (r instanceof EditorInstaller.Removed rem) {
            out.println(e.displayName() + ": removido (" + rem.deleted().size() + " arquivo(s))");
            return 0;
        } else if (r instanceof EditorInstaller.Failed f) {
            err.println(e.displayName() + ": " + f.message());
            return 1;
        }
        return 1;
    }

    private static String readLine(BufferedReader in) {
        try { return in.readLine(); } catch (IOException e) { return null; }
    }
}
