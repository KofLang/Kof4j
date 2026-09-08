package dev.kof.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser de expressões Java→Kof do {@code kof translate} (extraído p/ gate
 * <=500). {@link Translate.Emitter} estende esta classe e herda {@code p}
 * + os métodos de expressão/tipo.
 */
class TranslateExpr {

    final Parser p;

    TranslateExpr(Parser p) { this.p = p; }

        String parseExpr() {
            return parseTernary();
        }

        String parseTernary() {
            String cond = parseAssignment();
            if (p.at("?")) {
                p.next();
                String a = parseTernary();
                p.expect(":");
                String b = parseTernary();
                return "if (" + cond + ") " + a + " else " + b;
            }
            return cond;
        }

        String parseAssignment() {
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

        String parseOr() {
            String e = parseAnd();
            while (p.at(T.OROR)) { p.next(); e = e + " || " + parseAnd(); }
            return e;
        }

        String parseAnd() {
            String e = parseEquality();
            while (p.at(T.ANDAND)) { p.next(); e = e + " && " + parseEquality(); }
            return e;
        }

        String parseEquality() {
            String e = parseRel();
            while (p.at(T.EQEQ) || p.at(T.NE)) {
                if (p.at(T.EQEQ)) { p.next(); e = e + " == " + parseRel(); }
                else { p.next(); e = e + " != " + parseRel(); }
            }
            return e;
        }

        String parseRel() {
            String e = parseAdd();
            while (p.at(T.LT) || p.at(T.LE) || p.at(T.GT) || p.at(T.GE)) {
                String op = p.next().text;
                e = e + " " + op + " " + parseAdd();
            }
            return e;
        }

        String parseAdd() {
            String e = parseMul();
            while (p.at(T.PLUS) || p.at(T.MINUS)) {
                String op = p.next().text;
                e = e + " " + op + " " + parseMul();
            }
            return e;
        }

        String parseMul() {
            String e = parseUnary();
            while (p.at(T.STAR) || p.at(T.SLASH) || p.at(T.PERCENT)) {
                String op = p.next().text;
                e = e + " " + op + " " + parseUnary();
            }
            return e;
        }

        String parseUnary() {
            if (p.at(T.NOT)) { p.next(); return "!" + parseUnary(); }
            if (p.at(T.MINUS)) { p.next(); return "-" + parseUnary(); }
            if (p.at(T.PLUS)) { p.next(); return "+" + parseUnary(); }
            return parsePostfix();
        }

        String parsePostfix() {
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

        String translateCall(String receiver, String method) {
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
            if (method.equals("length")) {
                p.expect("("); p.expect(")");
                return receiver + ".length";
            }
            String args = parseCallArgs();
            return receiver + "." + method + "(" + args + ")";
        }

        String parseSingleArg() {
            p.expect("(");
            String e = parseExpr();
            p.expect(")");
            return e;
        }

        String parseCallArgs() {
            p.expect("(");
            List<String> args = new ArrayList<>();
            if (!p.at(")")) {
                args.add(parseExpr());
                while (p.at(",")) { p.next(); args.add(parseExpr()); }
            }
            p.expect(")");
            return String.join(", ", args);
        }

        String parsePrimary() {
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
                        if (isLambdaAhead()) {
                            yield parseLambda();
                        }
                        String e = parseExpr();
                        p.expect(")");
                        yield e;
                    }
                    yield t.text;
                }
                default -> t.text;
            };
        }

        boolean isLambdaAhead() {
            int depth = 1; // já estamos dentro do '('
            for (int i = p.pos; i + 1 < p.toks.size(); i++) {
                String s = p.toks.get(i).text;
                if (s.equals("(")) depth++;
                else if (s.equals(")")) {
                    depth--;
                    if (depth == 0) return p.toks.get(i + 1).type == T.ARROW;
                }
            }
            return false;
        }

        String parseLambda() {
            List<String> params = new ArrayList<>();  // '(' já consumido pelo parsePrimary
            if (!p.at(")")) {
                // (Type name, ...) — tipado; (name) — não-tipado (fallback)
                boolean typed = isPrimitiveOrType(p.peek().text) && p.peek(1).type == T.IDENT;
                if (typed) {
                    String ty = kofType(p.next().text);
                    String nm = p.next().text;
                    params.add(nm + ": " + ty);
                    while (p.at(",")) { p.next(); String t2 = kofType(p.next().text); String n2 = p.next().text; params.add(n2 + ": " + t2); }
                } else {
                    String nm = p.next().text;
                    params.add(nm);
                    while (p.at(",")) { p.next(); params.add(p.next().text); }
                }
            }
            p.expect(")");
            p.expect("->");
            String body = parseExpr();
            return "(" + String.join(", ", params) + ") -> " + body;
        }

        String parseNew() {
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

        String parseType() {
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

        List<String> parseParams() {
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

        String paramList(List<String> params) {
            return String.join(", ", params);
        }

        // ── static helpers ──────────────────────────────────────────────────

        static boolean isModifier(String s) {
            return switch (s) {
                case "public", "private", "protected", "static", "final",
                     "abstract", "synchronized", "native", "transient", "volatile",
                     "default" -> true;
                default -> false;
            };
        }

        static boolean isTypekeyword(String s) {
            return switch (s) {
                case "int", "long", "float", "double", "boolean", "char", "byte",
                     "short", "void", "String" -> true;
                default -> false;
            };
        }

        static boolean isPrimitiveOrType(String s) {
            return isTypekeyword(s) || (!isKeyword(s) && Character.isUpperCase(s.charAt(0)));
        }

        static boolean isKeyword(String s) {
            return TranslateLexer.KEYWORDS.contains(s);
        }

        static String kofType(String javaType) {
            return switch (javaType) {
                case "int", "Integer" -> "Int";
                case "long", "Long" -> "Long";
                case "float", "Float" -> "Float";
                case "double", "Double" -> "Double";
                case "boolean", "Boolean" -> "Bool";
                case "char", "Character" -> "Char";
                case "byte", "Byte" -> "Byte";
                case "short", "Short" -> "Short";
                case "void" -> "void";
                default -> javaType;
            };
        }
}
