package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.KofFormatter;
import dev.kof.compiler.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * kof fmt — formatador MVP baseado em tokenização leve:
 * reindenta por chaves/parênteses, normaliza espaços ao redor de operadores
 * e vírgulas, preserva strings e comentários. Não reescreve a AST (ainda).
 */
final class Fmt {

    private Fmt() {}

    static int run(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: kof fmt <file.kf|dir> [-w]");
            return 1;
        }
        boolean write = Arrays.asList(args).contains("-w");
        Path src = Path.of(args[1]);
        if (!Files.exists(src)) { System.err.println("not found: " + src); return 1; }

        List<Path> files = new ArrayList<>();
        try {
            if (Files.isDirectory(src)) {
                try (var s = Files.walk(src)) {
                    s.filter(p -> p.toString().endsWith(".kf")).forEach(files::add);
                }
            } else files.add(src);
        } catch (Exception e) { System.err.println("fmt: " + e.getMessage()); return 1; }

        int changed = 0;
        for (Path f : files) {
            try {
                String in = Files.readString(f);
                String out = format(in);
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
        if (write) System.out.println("fmt: " + changed + " arquivo(s) reformatado(s)");
        return 0;
    }

    /** Formata via parser real (KofFormatter); fallback token-based se falhar. */
    static String format(String src) {
        String viaAst = dev.kof.compiler.KofFormatter.format(src, "Main.kf");
        if (viaAst != null) return viaAst;
        List<String> tokens = tokenize(src);
        StringBuilder out = new StringBuilder();
        int indent = 0;
        StringBuilder line = new StringBuilder();
        boolean pendingBlank = false;

        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);

            if (t.equals("\n")) {
                if (line.length() > 0) {
                    emit(out, indent, line);
                    line.setLength(0);
                    pendingBlank = false;
                }
                continue;
            }

            if (t.equals("}")) {
                if (line.length() > 0) { emit(out, indent, line); line.setLength(0); }
                indent = Math.max(0, indent - 1);
                line.append('}');
                // fecha linha imediatamente (próximo token decide se continua)
                String nxt = peek(tokens, i);
                if (!nxt.isEmpty() && !nxt.equals(";") && !nxt.equals(")") && !nxt.equals(",")
                        && !nxt.equals(".") ) {
                    out.append("    ".repeat(indent)).append(line).append('\n');
                    line.setLength(0);
                } else if (nxt.equals(";") || nxt.equals(")")) {
                    line.append(nxt);
                    i++;
                    out.append("    ".repeat(indent)).append(line).append('\n');
                    line.setLength(0);
                }
                continue;
            }

            if (t.equals("{")) {
                if (line.length() > 0 && !line.toString().endsWith(" ")) line.append(' ');
                line.append('{');
                emit(out, indent, line);
                line.setLength(0);
                indent++;
                continue;
            }

            if (line.length() == 0 && t.startsWith("//")) {
                // comentário solto: preserva blank line anterior se houve
                emit(out, indent, line);
                line.append(t);
                continue;
            }

            if (line.length() > 0 && needsSpace(line, t)) line.append(' ');
            line.append(t);

            if (t.startsWith("//")) { emit(out, indent, line); line.setLength(0); }
        }
        if (line.length() > 0) emit(out, indent, line);
        return normalizeBlankLines(out.toString());
    }

    private static String peek(List<String> tokens, int i) {
        return i + 1 < tokens.size() ? tokens.get(i + 1) : "";
    }

    private static void emit(StringBuilder out, int indent, StringBuilder line) {
        String trimmed = line.toString().stripTrailing();
        if (trimmed.isEmpty()) return;
        out.append("    ".repeat(indent)).append(trimmed).append('\n');
    }

    /** Tokeniza respeitando strings e comentários de linha. */
    private static List<String> tokenize(String src) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"') {
                flush(cur, out);
                StringBuilder str = new StringBuilder("\"");
                i++;
                while (i < n && src.charAt(i) != '"' ) {
                    if (src.charAt(i)=='\\' && i+1<n) { str.append(src.charAt(i)).append(src.charAt(i+1)); i+=2; continue; }
                    str.append(src.charAt(i)); i++;
                }
                str.append('"'); i++;
                out.add(str.toString());
                continue;
            }
            if (c=='/' && i+1<n && src.charAt(i+1)=='/') {
                flush(cur,out);
                StringBuilder cm=new StringBuilder();
                while (i<n && src.charAt(i)!='\n') { cm.append(src.charAt(i)); i++; }
                out.add(cm.toString());
                out.add("\n");
                i++;
                continue;
            }
            if (c=='\n') {
                flush(cur,out);
                out.add("\n");
                i++; continue;
            }
            if (Character.isWhitespace(c)) { flush(cur,out); i++; continue; }
            if ("{}();,".indexOf(c)>=0 || "+-*/=<>&|!".indexOf(c)>=0) {
                flush(cur,out);
                // operadores compostos
                if (i+1<n) {
                    String two=""+c+src.charAt(i+1);
                    if (two.equals("==")||two.equals("!=")||two.equals("<=")||two.equals(">=")
                        ||two.equals("&&")||two.equals("->")||two.equals("++")||two.equals("--")) {
                        out.add(two); i+=2; continue;
                    }
                }
                out.add(String.valueOf(c)); i++; continue;
            }
            cur.append(c); i++;
        }
        flush(cur,out);
        return out;
    }

    private static void flush(StringBuilder cur, List<String> out) {
        if (cur.length()>0) { out.add(cur.toString()); cur.setLength(0); }
    }

    private static boolean needsSpace(StringBuilder cur, String nxt) {
        String c = cur.toString();
        if (c.isEmpty() || nxt.isEmpty()) return false;
        String last = c.substring(c.length()-1);
        char n0 = nxt.charAt(0);
        if (nxt.equals(";") || nxt.equals(",") || nxt.equals(")")) return false;
        if (c.endsWith("(")) return false;
        boolean lastOp = isOp(last), nextOp = isOp(nxt) && nxt.length()==1;
        if (last.equals(",") ) return true;
        if (lastOp != nextOp) return true;
        if (!lastOp && Character.isLetterOrDigit(last.charAt(0)) && Character.isLetterOrDigit(n0)) return true;
        return false;
    }

    private static boolean isOp(String t) {
        return t.length()==1 && "+-*/=<>!&|:".contains(t);
    }

    private static String normalizeBlankLines(String s) {
        return s.replaceAll("\n{3,}", "\n\n");
    }
}
