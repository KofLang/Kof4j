package dev.kof.cli;

import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * `kof legacy <copybook> [varName]` (KofBOL fatia 4, plan §20): parses a
 * COBOL copybook with the pure-Kof lib `libs/legacy` and prints the
 * generated Kof contract (`var <name> = Layout(listOf(...))`). Thin main:
 * every decision lives in the lib (the CmdMdCheck pattern).
 */
final class CmdLegacy {

    private CmdLegacy() {}

    static int run(String[] args) {
        return run(args, System.out, System.err);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            err.println("usage: kof legacy <copybook> [varName]");
            return 2;
        }
        Path cpy = Path.of(args[1]);
        String varName = args.length >= 3 ? args[2] : "layout";
        String text;
        try {
            text = Files.readString(cpy, StandardCharsets.UTF_8);
        } catch (Exception e) {
            err.println("legacy: cannot read " + cpy + ": " + e);
            return 1;
        }
        Path srcRoot = null;
        Path outRoot = null;
        try {
            srcRoot = Files.createTempDirectory("kof-legacy-src-");
            outRoot = Files.createTempDirectory("kof-legacy-out-");
            String mainSource = "import legacy.Copybook\n\n"
                    + "main() {\n"
                    + "    var cb = parseCopybookFull(\"\" " + literal(text) + ")\n"
                    + "    println(renderLayout(cb.layout, \"" + varName + "\"))\n"
                    + "    var rc = renderRedefines(cb.redefines)\n"
                    + "    if (rc.length > 0) {\n"
                    + "        println(rc)\n"
                    + "    }\n"
                    + "    var cc = renderConditions(cb.conditions)\n"
                    + "    if (cc.length > 0) {\n"
                    + "        println(cc)\n"
                    + "    }\n"
                    + "}\n";
            URLClassLoader loader = LegacyLibrary.compile("Main.kf", mainSource, srcRoot, outRoot);
            if (loader == null) {
                err.println("legacy: LEG0xx — the copybook was refused (see the diagnostics above) "
                        + "or the legacy library was not found (libs/legacy, $kof.install.dir/lib/kof-libs)");
                return 1;
            }
            PrintStream realOut = System.out;
            System.setOut(out);
            try (loader) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } finally {
                System.setOut(realOut);
            }
            return 0;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            err.println("legacy: " + (c == null ? String.valueOf(e)
                    : c.getClass().getSimpleName() + ": " + c.getMessage()));
            return 1;
        } catch (Exception e) {
            err.println("legacy: " + e.getMessage());
            return 1;
        } finally {
            if (srcRoot != null) KofCliSupport.cleanup(srcRoot);
            if (outRoot != null) KofCliSupport.cleanup(outRoot);
        }
    }

    /** The copybook text as a Kof string literal (line-by-line, \n-joined). */
    private static String literal(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace("\\", "\\\\").replace("\"", "\\\"");
            if (i > 0) {
                sb.append(" + \"\\n\"");
                sb.append('\n');
                sb.append("                    ");
            }
            sb.append("+ \"").append(line).append("\"");
        }
        return sb.toString();
    }
}
