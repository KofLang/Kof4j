package dev.kof.cli;

import dev.kof.compiler.KofFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * kof fmt — prints the canonical text of each file from its AST
 * ({@link KofFormatter}), comments included. A file that does not parse is
 * reported and never rewritten (§818).
 */
final class Fmt {

    private Fmt() {}

    static int run(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: kof fmt <file.kf|dir> [-w]");
            return 1;
        }
        // R6: fmt não repassa args ao programa (diferente de run/serve) — uma
        // flag desconhecida seria ignorada em silêncio (o usuário acharia que
        // teve efeito). Aceita só -w.
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("-") && !"-w".equals(args[i])) {
                System.err.println("fmt: unknown flag: " + args[i] + " (accepts: -w)");
                return 1;
            }
        }
        boolean write = Arrays.asList(args).contains("-w");
        Path src = Path.of(args[1]);
        if (!Files.exists(src)) { System.err.println("not found: " + src); return 1; }
        String extErr = KofCliSupport.unsupportedSourceExtension("fmt", src);
        if (extErr != null) { System.err.println(extErr); return 1; }

        List<Path> files = new ArrayList<>();
        try {
            if (Files.isDirectory(src)) {
                try (var s = Files.walk(src)) {
                    s.filter(KofCliSupport::isKofSource).forEach(files::add);
                }
            } else files.add(src);
        } catch (Exception e) { System.err.println("fmt: " + e.getMessage()); return 1; }

        int changed = 0;
        int unparseable = 0;
        for (Path f : files) {
            try {
                String in = Files.readString(f);
                // §818: a file that does not parse is reported and left as it is,
                // the others are still formatted
                String out = format(in, f.getFileName().toString());
                if (out == null) {
                    System.err.println("fmt " + f + ": not formatted, the file does not parse"
                            + " (run `kof check " + f + "` for the error)");
                    unparseable++;
                    continue;
                }
                if (!out.equals(in)) changed++;
                if (write) {
                    if (!out.equals(in)) Files.writeString(f, out);
                } else {
                    System.out.print(out);
                }
            } catch (Exception e) {
                System.err.println("fmt " + f + ": " + e.getMessage());
            }
        }
        if (write) System.out.println("fmt: " + changed + " file(s) reformatted");
        return unparseable == 0 ? 0 : 1;
    }

    /** The canonical text of {@code src} as {@code Main.kf}; null when it does not parse. */
    static String format(String src) {
        return format(src, "Main.kf");
    }

    /** {@code fileName} decides the profile ({@code .ptkf} is PortuKof). Null when the
     *  source does not parse: §818 removed the token-based fallback, which corrupted
     *  the text it could not understand ({@code String?name}, {@code json.decode < T > (x)}). */
    static String format(String src, String fileName) {
        return KofFormatter.format(src, fileName);
    }
}
