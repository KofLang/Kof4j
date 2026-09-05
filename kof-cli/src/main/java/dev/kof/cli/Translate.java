package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * `kof translate` — translate a subset of Java source into idiomatic Kof
 * (docs/future/TRANSLATOR.md, Fase F).
 *
 * Understands structure (classes, fields, methods, control flow) rather than
 * doing textual substitution. Static methods become top-level functions;
 * {@code System.out.println} becomes {@code println}; {@code a.equals(b)}
 * becomes {@code a == b}; {@code new X(...)} drops {@code new}.
 */
public final class Translate {

    private Translate() {
    }

    public static int run(String[] args) {
        if (args.length > 0 && "translate".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 0) {
            System.err.println("usage: kof translate <file.java> [--output <file.kf>]");
            return 1;
        }
        Path file = Path.of(args[0]);
        String outArg = optionValue(args, "--output");
        Path outFile = outArg != null ? Path.of(outArg) : null;
        if (!Files.isRegularFile(file)) {
            System.err.println("file not found: " + file);
            return 1;
        }
        try {
            String kof = translateJava(Files.readString(file));
            if (outFile != null) {
                Files.writeString(outFile, kof);
                System.out.println("translated to " + outFile);
            } else {
                System.out.print(kof);
            }
            return 0;
        } catch (TranslateException e) {
            System.err.println("kof translate: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("kof translate: " + e.getMessage());
            return 1;
        }
    }

    // ── public API for tests ──────────────────────────────────────────────

    static String translateJava(String source) {
        List<Tok> toks = lex(source);
        Parser p = new Parser(toks);
        return new Emitter(p).translate();
    }

    // ── Lexer ─────────────────────────────────────────────────────────────

    private enum T {
        IDENT, INT, FLOAT, STR, CHAR, P, // { } ( ) [ ] ; , . 
        EQ, EQEQ, NE, LT, LE, GT, GE, PLUS, MINUS, STAR, SLASH, PERCENT,
        ANDAND, OROR, NOT, PLUSEQ, MINUSEQ, STAREQ, SLASHEQ, PERCENTEQ,
        INC, DEC, EOF
    }

    private record Tok(T type, String text) {
    }

    private static final java.util.Set<String> KEYWORDS = java.util.Set.of(
            "public", "private", "protected", "static", "final", "abstract",
            "class", "interface", "extends", "implements", "return", "if",
            "else", "while", "for", "new", "package", "import", "null",
            "true", "false", "throw", "try", "catch", "finally", "void",
            "boolean", "byte", "short", "int", "long", "float", "double",
            "char", "String", "this", "super", "switch", "case", "break",
            "default", "do");

    private static List<Tok> lex(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            if (c == '"') {
                int j = i + 1;
                StringBuilder sb = new StringBuilder();
                while (j < n && s.charAt(j) != '"') {
                    if (s.charAt(j) == '\\' && j + 1 < n) {
                        char e = s.charAt(j + 1);
                        sb.append(switch (e) {
                            case 'n' -> '\n'; case 't' -> '\t'; case 'r' -> '\r';
                            case '"' -> '"'; case '\\' -> '\\';
                            default -> e;
                        });
                        j += 2;
                    } else {
                        sb.append(s.charAt(j)); j++;
                    }
                }
                out.add(new Tok(T.STR, sb.toString()));
                i = j + 1;
                continue;
            }
            if (c == '\'') {
                if (i + 2 < n && s.charAt(i + 2) == '\'') {
                    out.add(new Tok(T.CHAR, String.valueOf(s.charAt(i + 1))));
                    i += 3;
                } else {
                    i++;
                }
                continue;
            }
            if (Character.isDigit(c)) {
                int j = i;
                boolean isFloat = false;
                while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                    if (s.charAt(j) == '.') isFloat = true;
                    j++;
                }
                out.add(new Tok(isFloat ? T.FLOAT : T.INT, s.substring(i, j)));
                i = j;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int j = i;
                while (j < n && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                String w = s.substring(i, j);
                if (KEYWORDS.contains(w)) {
                    out.add(new Tok(T.IDENT, w)); // keyword kept as IDENT text
                } else {
                    out.add(new Tok(T.IDENT, w));
                }
                i = j;
                continue;
            }
            switch (c) {
                case '{' -> out.add(new Tok(T.P, "{"));
                case '}' -> out.add(new Tok(T.P, "}"));
                case '(' -> out.add(new Tok(T.P, "("));
                case ')' -> out.add(new Tok(T.P, ")"));
                case '[' -> out.add(new Tok(T.P, "["));
                case ']' -> out.add(new Tok(T.P, "]"));
                case ';' -> out.add(new Tok(T.P, ";"));
                case ',' -> out.add(new Tok(T.P, ","));
                case ':' -> out.add(new Tok(T.P, ":"));
                case '.' -> out.add(new Tok(T.P, "."));
                case '=' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.EQEQ, "==")); i += 2; continue; } out.add(new Tok(T.EQ, "=")); }
                case '!' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.NE, "!=")); i += 2; continue; } out.add(new Tok(T.NOT, "!")); }
                case '<' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.LE, "<=")); i += 2; continue; } out.add(new Tok(T.LT, "<")); }
                case '>' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.GE, ">=")); i += 2; continue; } out.add(new Tok(T.GT, ">")); }
                case '+' -> { if (i + 1 < n && s.charAt(i + 1) == '+') { out.add(new Tok(T.INC, "++")); i += 2; continue; } if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.PLUSEQ, "+=")); i += 2; continue; } out.add(new Tok(T.PLUS, "+")); }
                case '-' -> { if (i + 1 < n && s.charAt(i + 1) == '-') { out.add(new Tok(T.DEC, "--")); i += 2; continue; } if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.MINUSEQ, "-=")); i += 2; continue; } out.add(new Tok(T.MINUS, "-")); }
                case '*' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.STAREQ, "*=")); i += 2; continue; } out.add(new Tok(T.STAR, "*")); }
                case '/' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.SLASHEQ, "/=")); i += 2; continue; } out.add(new Tok(T.SLASH, "/")); }
                case '%' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.PERCENTEQ, "%=")); i += 2; continue; } out.add(new Tok(T.PERCENT, "%")); }
                case '&' -> { if (i + 1 < n && s.charAt(i + 1) == '&') { out.add(new Tok(T.ANDAND, "&&")); i += 2; continue; } }
                case '|' -> { if (i + 1 < n && s.charAt(i + 1) == '|') { out.add(new Tok(T.OROR, "||")); i += 2; continue; } }
                default -> i++;
            }
            i = Math.min(i + 1, n); // guarded advance for simple single-char cases
        }
        out.add(new Tok(T.EOF, ""));
        return out;
    }

    // ── Parser + Emitter range helpers ────────────────────────────────────

    private static final class TranslateException extends RuntimeException {
        TranslateException(String m) { super(m); }
    }

    private static final class Parser {
        final List<Tok> toks;
        int pos;
        Parser(List<Tok> toks) { this.toks = toks; }
        Tok peek() { return toks.get(pos); }
        Tok peek(int ahead) { int i = Math.min(pos + ahead, toks.size() - 1); return toks.get(i); }
        Tok next() { Tok t = toks.get(pos); if (pos < toks.size() - 1) pos++; return t; }
        boolean at(String text) { return peek().text.equals(text); }
        boolean at(T t) { return peek().type == t; }
        Tok expect(String text) {
            if (!at(text)) throw new TranslateException("expected '" + text + "' but found '" + peek().text + "'");
            return next();
        }
        Tok expectPunct() { return next(); }
    }

    private static final class Emitter {
        final Parser p;
        final StringBuilder out = new StringBuilder();
        final StringBuilder topFns = new StringBuilder();

        Emitter(Parser p) { this.p = p; }

        String translate() {
            // Skip package / imports.
            while (p.at("package")) {
                while (!p.at(";")) p.next();
                p.next(); // ;
            }
            while (p.at("import")) {
                while (!p.at(";")) p.next();
                p.next();
            }
            // Parse a type declaration.
            parseTypeDeclaration();
            // Static methods were collected as top-level functions; emit them first.
            out.insert(0, topFns);
            return out.toString();
        }

        private void parseTypeDeclaration() {
            // modifiers
            while (isModifier(p.peek().text)) p.next();
            if (p.at("class")) {
                parseClass();
            } else if (p.at("interface")) {
                parseInterface();
            } else if (p.at("record")) {
                parseRecord();
            } else if (p.at("enum")) {
                parseEnum();
            } else {
                throw new TranslateException("expected class/interface/record/enum, found '" + p.peek().text + "'");
            }
        }

        private void parseEnum() {
            p.expect("enum");
            String name = p.next().text;
            List<String> constants = new ArrayList<>();
            p.expect("{");
            while (!p.at("}") && !p.at(";")) {
                constants.add(p.next().text);
                if (p.at("(")) { p.next(); while (!p.at(")")) p.next(); p.next(); }  // args ignorados (MVP)
                if (p.at("{")) skipBlock();                                          // corpo de constante ignorado
                if (p.at(",")) p.next();
            }
            if (p.at(";")) { p.next(); while (!p.at("}")) skipBlock(); }             // métodos/campos ignorados
            p.expect("}");
            out.append("enum ").append(name).append(" { ")
               .append(String.join(", ", constants)).append(" }\n");
        }

        private void parseRecord() {
            p.expect("record");
            String name = p.next().text;
            List<String> components = new ArrayList<>();
            if (p.at("(")) {
                p.next();
                while (!p.at(")")) {
                    String type = kofType(p.next().text);
                    String cname = p.next().text;
                    components.add(type + " " + cname);
                    if (p.at(",")) p.next();
                }
                p.expect(")");
            }
            if (p.at("{")) skipBlock();
            else p.expect(";");
            out.append("record ").append(name).append('(')
               .append(String.join(", ", components)).append(")\n");
        }

        private void parseClass() {
            p.expect("class");
            String name = p.next().text;
            String superCls = null;
            List<String> ifaces = new ArrayList<>();
            if (p.at("extends")) { p.next(); superCls = p.next().text; }
            if (p.at("implements")) {
                p.next();
                while (!p.at("{")) { ifaces.add(p.next().text); if (p.at(",")) p.next(); }
            }
            p.expect("{");
            out.append("class ").append(kofType(name));
            if (superCls != null) out.append(" extends ").append(kofType(superCls));
            if (!ifaces.isEmpty()) {
                out.append(" implements ");
                out.append(ifaces.stream().map(Emitter::kofType).collect(java.util.stream.Collectors.joining(", ")));
            }
            out.append(" {\n");

            while (!p.at("}")) {
                parseMember(name);
            }
            p.expect("}");
            out.append("}\n");
        }

        private void parseInterface() {
            p.expect("interface");
            String name = p.next().text;
            p.expect("{");
            out.append("interface ").append(name).append(" {\n");
            while (!p.at("}")) {
                // method signature ending in ';'
                int save = p.pos;
                boolean isStatic = false;
                while (isModifier(p.peek().text)) { if (p.at("static")) isStatic = true; p.next(); }
                String ret = parseType();
                String mname = p.next().text;
                List<String> params = parseParams();
                if (p.at("{")) { skipBlock(); }
                else p.expect(";");
                out.append("    ").append(ret).append(' ').append(mname).append('(')
                   .append(paramList(params)).append("): ").append(ret).append('\n');
            }
            p.expect("}");
            out.append("}\n");
        }

        private void parseMember(String className) {
            int save = p.pos;
            boolean isStatic = false;
            while (isModifier(p.peek().text)) { if (p.at("static")) isStatic = true; p.next(); }
            if (p.at("{")) { // static initializer block — skip
                skipBlock();
                return;
            }
            String typeName = parseType();
            String memberName = p.next().text;
            if (p.at("(")) {
                // method or constructor
                List<String> params = parseParams();
                boolean isConstructor = memberName.equals(className);
                if (p.at(";")) { p.next(); return; } // abstract/native signature
                List<String> body = parseBlock();
                emitMethod(isStatic, isConstructor, typeName, memberName, params, body);
            } else {
                // field: "Type name [= expr];"
                String init = "";
                if (p.at("=")) {
                    p.next();
                    init = " = " + parseExpr();
                }
                while (!p.at(";")) p.next();
                p.next();
                if (isStatic) return; // static field → skip (no top-level state in Kof)
                out.append("    ").append(kofType(typeName)).append(' ').append(memberName).append(init).append('\n');
            }
        }

        private void emitMethod(boolean isStatic, boolean isConstructor, String retType,
                                String name, List<String> params, List<String> body) {
            StringBuilder sb = isStatic ? topFns : out;
            if (isConstructor) {
                sb.append("    constructor(").append(paramList(params)).append(") {}\n");
                return;
            }
            if (isStatic && name.equals("main")) {
                // Java main(String[] args) → top-level Kof main()
                sb.append("main() {\n");
                for (String stmt : body) sb.append("    ").append(stmt).append('\n');
                sb.append("}\n");
                return;
            }
            sb.append("    ").append(kofType(retType)).append(' ').append(name)
              .append('(').append(paramList(params)).append(')');
            if (body.size() == 1 && body.get(0).startsWith("return ")) {
                String expr = body.get(0).substring("return ".length());
                sb.append(" = ").append(expr).append('\n');
            } else {
                sb.append(" {\n");
                for (String stmt : body) sb.append("        ").append(stmt).append('\n');
                sb.append("    }\n");
            }
        }

        // ── statements ─────────────────────────────────────────────────────

        private List<String> parseBlock() {
            p.expect("{");
            List<String> stmts = new ArrayList<>();
            while (!p.at("}")) {
                stmts.add(parseStatement());
            }
            p.expect("}");
            return stmts;
        }

        private void skipBlock() {
            p.expect("{");
            int depth = 1;
            while (depth > 0) {
                if (p.at("{")) depth++;
                if (p.at("}")) depth--;
                p.next();
            }
        }

        private String parseStatement() {
            if (p.at("{")) {
                List<String> body = parseBlock();
                StringBuilder sb = new StringBuilder("{ ");
                for (String s : body) sb.append(s).append(' ');
                return sb.append('}').toString().trim();
            }
            if (p.at("return")) {
                p.next();
                if (p.at(";")) { p.next(); return "return"; }
                String e = parseExpr();
                p.expect(";");
                return "return " + e;
            }
            if (p.at("if")) {
                p.next();
                p.expect("(");
                String cond = parseExpr();
                p.expect(")");
                String thenBranch = parseStatement();
                String out = "if (" + cond + ") { " + thenBranch + " }";
                if (p.at("else")) {
                    p.next();
                    String elseBranch = parseStatement();
                    out += " else { " + elseBranch + " }";
                }
                return out;
            }
            if (p.at("while")) {
                p.next();
                p.expect("(");
                String cond = parseExpr();
                p.expect(")");
                String body = parseStatement();
                return "while (" + cond + ") { " + body + " }";
            }
            if (p.at("for")) {
                return parseFor();
            }
            // local variable declaration or expression statement.
            return parseExprOrDecl();
        }

        private String parseFor() {
            p.next();
            p.expect("(");
            if (forHasColon()) {
                // enhanced for: [Type] ident ':' expr
                String type = parseType();
                String varName = p.next().text;
                p.expect(":");
                String coll = parseExpr();
                p.expect(")");
                String body = parseStatement();
                return "for (var " + varName + " in " + coll + ") { " + body + " }";
            }
            String init = "";
            if (!p.at(";")) init = parseForInit();
            p.expect(";");
            String cond = "";
            if (!p.at(";")) cond = parseExpr();
            p.expect(";");
            String incr = "";
            if (!p.at(")")) incr = parseExpr();
            p.expect(")");
            String body = parseStatement();
            return "for (" + init + "; " + cond + "; " + incr + ") { " + body + " }";
        }

        private boolean forHasColon() {
            int depth = 0;
            for (int i = p.pos; i < p.toks.size(); i++) {
                String t = p.toks.get(i).text;
                if (t.equals("(")) depth++;
                else if (t.equals(")")) { if (depth == 0) return false; depth--; }
                else if (depth == 0 && t.equals(":")) return true;
                else if (depth == 0 && t.equals(";")) return false;
            }
            return false;
        }

        private String parseForInit() {
            if (isPrimitiveOrType(p.peek().text) && p.peek(1).type == T.IDENT) {
                p.next(); // type
                String name = p.next().text;
                String expr = "";
                if (p.at("=")) { p.next(); expr = parseExpr(); }
                return "var " + name + (expr.isEmpty() ? "" : " = " + expr);
            }
            return parseExpr();
        }

        private String parseExprOrDecl() {
            int save = p.pos;
            // Detect "Type name [= expr];"   — but also plain "name = expr;" 
            // Consume first ident; if next is an identifier (not operator) it's a decl.
            if (isPrimitiveOrType(p.peek().text) && p.peek(1).type == T.IDENT) {
                p.next(); // type
                String name = p.next().text;
                if (p.at("=")) {
                    p.next();
                    String e = parseExpr();
                    p.expect(";");
                    return "var " + name + " = " + e;
                }
                p.expect(";");
                return "var " + name;
            }
            p.pos = save;
            String e = parseExpr();
            p.expect(";");
            return e;
        }

        // ── expressions ────────────────────────────────────────────────────

        private String parseExpr() {
            return parseAssignment();
        }

        private String parseAssignment() {
            String lhs = parseOr();
            T t = p.peek().type;
            if (t == T.EQ) { p.next(); return lhs + " = " + parseAssignment(); }
            if (t == T.PLUSEQ) { p.next(); return lhs + " += " + parseAssignment(); }
            if (t == T.MINUSEQ) { p.next(); return lhs + " -= " + parseAssignment(); }
            if (t == T.STAREQ) { p.next(); return lhs + " *= " + parseAssignment(); }
            if (t == T.SLASHEQ) { p.next(); return lhs + " /= " + parseAssignment(); }
            if (t == T.PERCENTEQ) { p.next(); return lhs + " %= " + parseAssignment(); }
            return lhs;
        }

        private String parseOr() {
            String e = parseAnd();
            while (p.at(T.OROR)) { p.next(); e = e + " || " + parseAnd(); }
            return e;
        }

        private String parseAnd() {
            String e = parseEquality();
            while (p.at(T.ANDAND)) { p.next(); e = e + " && " + parseEquality(); }
            return e;
        }

        private String parseEquality() {
            String e = parseRel();
            while (p.at(T.EQEQ) || p.at(T.NE)) {
                if (p.at(T.EQEQ)) { p.next(); e = e + " == " + parseRel(); }
                else { p.next(); e = e + " != " + parseRel(); }
            }
            return e;
        }

        private String parseRel() {
            String e = parseAdd();
            while (p.at(T.LT) || p.at(T.LE) || p.at(T.GT) || p.at(T.GE)) {
                String op = p.next().text;
                e = e + " " + op + " " + parseAdd();
            }
            return e;
        }

        private String parseAdd() {
            String e = parseMul();
            while (p.at(T.PLUS) || p.at(T.MINUS)) {
                String op = p.next().text;
                e = e + " " + op + " " + parseMul();
            }
            return e;
        }

        private String parseMul() {
            String e = parseUnary();
            while (p.at(T.STAR) || p.at(T.SLASH) || p.at(T.PERCENT)) {
                String op = p.next().text;
                e = e + " " + op + " " + parseUnary();
            }
            return e;
        }

        private String parseUnary() {
            if (p.at(T.NOT)) { p.next(); return "!" + parseUnary(); }
            if (p.at(T.MINUS)) { p.next(); return "-" + parseUnary(); }
            if (p.at(T.PLUS)) { p.next(); return "+" + parseUnary(); }
            return parsePostfix();
        }

        private String parsePostfix() {
            String e = parsePrimary();
            while (true) {
                if (p.at(".")) {
                    p.next();
                    String field = p.next().text;
                    if (p.at("(")) {
                        // method call on receiver
                        e = translateCall(e, field);
                    } else {
                        e = e + "." + field;
                    }
                } else if (p.at("[")) {
                    p.next();
                    String idx = parseExpr();
                    p.expect("]");
                    e = e + "[" + idx + "]";
                } else if (p.at(T.INC)) { p.next(); e += "++"; }
                else if (p.at(T.DEC)) { p.next(); e += "--"; }
                else break;
            }
            return e;
        }

        private String translateCall(String receiver, String method) {
            if (receiver.equals("System.out") && method.equals("println")) {
                String args = parseCallArgs();
                return "println(" + args + ")";
            }
            if (receiver.equals("System.out") && method.equals("print")) {
                String args = parseCallArgs();
                return "print(" + args + ")";
            }
            if (method.equals("equals")) {
                String arg = parseSingleArg();
                return receiver + " == " + arg;
            }
            String args = parseCallArgs();
            return receiver + "." + method + "(" + args + ")";
        }

        private String parseSingleArg() {
            p.expect("(");
            String e = parseExpr();
            p.expect(")");
            return e;
        }

        private String parseCallArgs() {
            p.expect("(");
            List<String> args = new ArrayList<>();
            if (!p.at(")")) {
                args.add(parseExpr());
                while (p.at(",")) { p.next(); args.add(parseExpr()); }
            }
            p.expect(")");
            return String.join(", ", args);
        }

        private String parsePrimary() {
            Tok t = p.next();
            return switch (t.type) {
                case INT, FLOAT -> t.text;
                case STR -> "\"" + t.text + "\"";
                case CHAR -> "'" + t.text + "'";
                case IDENT -> switch (t.text) {
                    case "true" -> "true";
                    case "false" -> "false";
                    case "null" -> "null";
                    case "new" -> parseNew();
                    case "this" -> "this";
                    default -> t.text;
                };
                case P -> {
                    if (t.text.equals("(")) {
                        String e = parseExpr();
                        p.expect(")");
                        yield e;
                    }
                    yield t.text;
                }
                default -> t.text;
            };
        }

        private String parseNew() {
            String typeName = p.next().text;
            if (p.at("[")) {
                // array creation: new int[n] or new int[]{...}
                p.next();
                p.next(); // ]
                String size = parseExpr();
                return "new " + kofType(typeName) + "[" + size + "]";
            }
            String args = parseCallArgs();
            return kofType(typeName) + "(" + args + ")";
        }

        // ── types ───────────────────────────────────────────────────────────

        private String parseType() {
            String base = p.next().text;
            StringBuilder sb = new StringBuilder(kofType(base));
            // generic args <...>
            if (p.at("<")) {
                p.next();
                String inner = parseType();
                while (p.at(",")) { p.next(); inner += ", " + parseType(); }
                p.expect(">");
                sb.append("<").append(inner).append(">");
            }
            while (p.at("[")) { p.next(); p.next(); sb.append("[]"); }
            return sb.toString();
        }

        private List<String> parseParams() {
            p.expect("(");
            List<String> params = new ArrayList<>();
            if (!p.at(")")) {
                params.add(parseType());
                String pname = p.next().text;
                params.set(params.size() - 1, params.get(params.size() - 1) + " " + pname);
                while (p.at(",")) { p.next(); String ty = parseType(); String nm = p.next().text; params.add(ty + " " + nm); }
            }
            p.expect(")");
            return params;
        }

        private String paramList(List<String> params) {
            return String.join(", ", params);
        }

        // ── static helpers ──────────────────────────────────────────────────

        private static boolean isModifier(String s) {
            return switch (s) {
                case "public", "private", "protected", "static", "final",
                     "abstract", "synchronized", "native", "transient", "volatile",
                     "default" -> true;
                default -> false;
            };
        }

        private static boolean isTypekeyword(String s) {
            return switch (s) {
                case "int", "long", "float", "double", "boolean", "char", "byte",
                     "short", "void", "String" -> true;
                default -> false;
            };
        }

        private static boolean isPrimitiveOrType(String s) {
            return isTypekeyword(s) || (!isKeyword(s) && Character.isUpperCase(s.charAt(0)));
        }

        private static boolean isKeyword(String s) {
            return KEYWORDS.contains(s);
        }

        private static String kofType(String javaType) {
            return switch (javaType) {
                case "int" -> "Int";
                case "long" -> "Long";
                case "float" -> "Float";
                case "double" -> "Double";
                case "boolean" -> "Bool";
                case "char" -> "Char";
                case "byte" -> "Byte";
                case "short" -> "Short";
                case "void" -> "void";
                default -> javaType;
            };
        }
    }

    private static String optionValue(String[] args, String opt) {
        for (int i = 0; i < args.length - 1; i++) {
            if (opt.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}