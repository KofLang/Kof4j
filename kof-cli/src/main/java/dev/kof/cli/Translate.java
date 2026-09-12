package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * `kof translate` — translate a subset of Java source into idiomatic Kof
 * (docs/development/future/TRANSLATOR.md, Fase F).
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
        List<Tok> toks = TranslateLexer.lex(source);
        Parser p = new Parser(toks);
        return new Emitter(p).translate();
    }

    static final class Emitter extends TranslateExpr {
        final StringBuilder out = new StringBuilder();
        final StringBuilder topFns = new StringBuilder();

        Emitter(Parser p) { super(p); }

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
            // Parse all top-level type declarations.
            while (!p.at(T.EOF)) {
                parseTypeDeclaration();
            }
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
    }

    private static String optionValue(String[] args, String opt) {
        for (int i = 0; i < args.length - 1; i++) {
            if (opt.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}
