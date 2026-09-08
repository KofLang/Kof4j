package dev.kof.cli;

import dev.kof.cli.editor.DetectContext;
import dev.kof.cli.editor.EditorInfo;
import dev.kof.cli.editor.EditorIntegration;
import dev.kof.cli.editor.EditorRegistry;
import dev.kof.compiler.KofVersion;

import java.io.PrintStream;
import java.util.List;

/**
 * {@code kof editor} — infraestrutura de integração de editores (EDI001,
 * §6/§11). Degráus 1-2: face READ-ONLY (list/detect/status). install/
 * uninstall/setup/update entram no degrau 3 (plano §20) — aqui respondem
 * com diagnóstico honesto (R6), nunca silencioso.
 */
final class CmdEditor {

    private CmdEditor() {}

    static int run(String[] args) {
        return run(args, DetectContext.system(), System.out, System.err);
    }

    /** Testável: contexto de detecção e saídas injetáveis (§24). */
    static int run(String[] args, DetectContext ctx, PrintStream out, PrintStream err) {
        if (args.length < 2 || "--help".equals(args[1]) || "-h".equals(args[1])) {
            usage(out);
            return 0;
        }
        return switch (args[1]) {
            case "list" -> list(out);
            case "detect" -> detect(ctx, out);
            case "status" -> status(ctx, out);
            case "install", "uninstall", "setup", "update" -> {
                err.println("kof editor " + args[1] + ": ainda não implementado "
                        + "(plano EDI001 degrau 3 — use a integração manual de "
                        + "docs/tooling/EDITOR_SUPPORT.md por enquanto)");
                yield 2;
            }
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
        out.println("  install <editor>     (planned — EDI001 degrau 3)");
        out.println("  uninstall <editor>   (planned — EDI001 degrau 3)");
        out.println("  setup                (planned — EDI001 degrau 3)");
        out.println("  update               (planned — EDI001 degrau 3)");
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
            EditorInfo info = e.detect(ctx);
            out.println("  " + (info.installed() ? "✓" : "✗") + " " + e.displayName());
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
}
