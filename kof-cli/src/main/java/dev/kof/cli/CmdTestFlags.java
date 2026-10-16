package dev.kof.cli;

import dev.kof.compiler.Target;

import java.nio.file.Path;

/**
 * #856 — parsing das flags do `kof test`, extraído de {@link CmdTest} (o arquivo
 * estava grande; esta é a extração do refactor-500, mesmo padrão dos outros
 * satélites do repositório).
 *
 * <p>Inclui a flag nova {@code --module-root <dir>}: a raiz do MÓDULO deixa de ser
 * derivada do alvo, então um teste pode ser um ARQUIVO SÓ e viver dentro (ou fora)
 * da árvore de fontes sem cair em {@code PKG004}. O alvo posicional continua
 * aceito e vence a raiz de config do {@code kof.toml}.
 */
final class CmdTestFlags {

    static final String USAGE =
            "usage: kof test <file.kf|dir> [--module-root <dir>] [--tests-only]"
            + " [--target jvm|native|js]"
            + " [--timeout <sec>] [--tag <tag>[,<tag>...]]";

    /** O que as flags pediram. {@code positional} = o alvo passado depois delas. */
    record Parsed(Target target, long timeoutSec, String tag, Path moduleRoot, Path positional,
                  boolean testsOnly) {}

    private CmdTestFlags() {}

    static Parsed parse(String[] args, int argStart) {
        Target target = Target.JVM;
        long timeoutSec = 0;   // 0 = sem limite (comportamento historico, aditivo)
        Path moduleRoot = null;
        Path positional = null;
        boolean testsOnly = false;   // #856(a): nao roda o `main` como programa de teste
        String tag = null;
        boolean positionalTarget = false;
    for (int i = argStart; i < args.length; i++) {
        if (args[i].startsWith("--target=")) {
            target = KofCliSupport.parseTarget(args[i].substring("--target=".length()));
        } else if (args[i].equals("--target") && i + 1 < args.length) {
            target = KofCliSupport.parseTarget(args[i + 1]);
            i++;
        } else if (args[i].startsWith("--timeout=")) {
            Long t = parseTimeout(args[i].substring("--timeout=".length()));
            if (t == null) { badTimeout(); return null; }
            timeoutSec = t;
        } else if (args[i].equals("--timeout") && i + 1 < args.length) {
            Long t = parseTimeout(args[i + 1]);
            if (t == null) { badTimeout(); return null; }
            timeoutSec = t;
            i++;
        } else if (args[i].equals("--tests-only")) {
            testsOnly = true;
        } else if (args[i].startsWith("--module-root=")) {
            moduleRoot = Path.of(args[i].substring("--module-root=".length()));
        } else if (args[i].equals("--module-root") && i + 1 < args.length) {
            moduleRoot = Path.of(args[i + 1]);
            i++;
        } else if (args[i].startsWith("--tag=")) {
            tag = args[i].substring("--tag=".length());
            if (tag.isEmpty()) { badTag(); return null; }
        } else if (args[i].equals("--tag") && i + 1 < args.length) {
            tag = args[i + 1];
            if (tag.isEmpty()) { badTag(); return null; }
            i++;
        } else if (args[i].equals("--help") || args[i].equals("-h")) {
            System.err.println(USAGE);
            System.exit(0);   // --help no meio das flags: para aqui, como o run fazia
            return null;
        } else if (!args[i].startsWith("-")) {
            // #856: em modo flag o alvo POSICIONAL é aceito
            // (`kof test --module-root src src/testes/X.kf`); o primeiro
            // não-flag vence a raiz de config do kof.toml, e um SEGUNDO
            // não-flag segue erro honesto.
            if (positionalTarget) {
                System.err.println("test: unexpected argument: " + args[i]
                        + " (accepts: one target file/dir)");
                System.exit(1);
                return null;
            }
            positional = Path.of(args[i]);
        
        } else if (args[i].startsWith("-")) {
            // R6: an unknown flag (or --target/--timeout without its value) must
            // never be silently ignored — the user/CI would believe it took effect.
            System.err.println("test: unknown or incomplete flag: " + args[i]
                    + " (accepts: --module-root <dir>, --tests-only, --target jvm|native|js,"
                    + " --timeout <sec>, --tag <tag>[,<tag>...])");
            System.exit(1);
            return null;
        } else {
            System.err.println("test: unexpected argument: " + args[i]
                    + " (accepts: --module-root <dir>, --tests-only, --target jvm|native|js,"
                    + " --timeout <sec>, --tag <tag>[,<tag>...])");
            System.exit(1);
            return null;
        }
    }
        return new Parsed(target, timeoutSec, tag, moduleRoot, positional, testsOnly);
    }

    private static void badTag() {
        System.err.println("test: --tag requires a non-empty value");
        System.exit(1);
    }

    private static Long parseTimeout(String v) {
        try {
            long n = Long.parseLong(v.trim());
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void badTimeout() {
        System.err.println("test: --timeout expects a positive integer of seconds");
        System.exit(1);
    }
}
