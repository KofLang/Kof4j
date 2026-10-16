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

    /**
     * Hook para o corpo em bloco de lambda: os statements vivem em
     * {@link TranslateStatements} (subclasse), então reaproveitamos a
     * implementação de lá via override. Sem isto o {@code parseLambda}
     * (aqui na base) não enxerga {@code parseBlock}.
     */
    protected List<String> parseStatementBlock() {
        throw new TranslateException("lambda with a block body unavailable at this layer");
    }

    /**
     * Hook para switch-EXPRESSÃO Java (`switch (x) { case 1 -> ... }`): os
     * corpo/statements vivem em {@link TranslateStatements} (subclasse).
     */
    protected String parseSwitchExprHook() {
        throw new TranslateException("switch expression unavailable at this layer");
    }

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
            // Compostos bitwise/shift (`x &= 3`, `x <<= 2`, `x >>>= 1`): o
            // lexer os emite como operador + `=`. Kof aceita os compostos.
            if (p.at(T.AMP) && p.peek(1).type == T.EQ) {
                p.next(); p.next(); return lhs + " &= " + parseAssignment();
            }
            if (p.at(T.PIPE) && p.peek(1).type == T.EQ) {
                p.next(); p.next(); return lhs + " |= " + parseAssignment();
            }
            if (p.at(T.CARET) && p.peek(1).type == T.EQ) {
                p.next(); p.next(); return lhs + " ^= " + parseAssignment();
            }
            if (p.at(T.LT) && p.peek(1).type == T.LT && p.peek(2).type == T.EQ) {
                p.next(); p.next(); p.next(); return lhs + " <<= " + parseAssignment();
            }
            if (p.at(T.GT) && p.peek(1).type == T.GT && p.peek(2).type == T.GT && p.peek(3).type == T.EQ) {
                p.next(); p.next(); p.next(); p.next(); return lhs + " >>>= " + parseAssignment();
            }
            if (p.at(T.GT) && p.peek(1).type == T.GT && p.peek(2).type == T.EQ) {
                p.next(); p.next(); p.next(); return lhs + " >>= " + parseAssignment();
            }
            return lhs;
        }

        String parseOr() {
            String e = parseAnd();
            while (p.at(T.OROR)) { p.next(); e = e + " || " + parseAnd(); }
            return e;
        }

        String parseAnd() {
            String e = parseBitOr();
            while (p.at(T.ANDAND)) { p.next(); e = e + " && " + parseBitOr(); }
            return e;
        }

        String parseBitOr() {
            String e = parseBitAnd();
            while ((p.at(T.PIPE) && p.peek(1).type != T.EQ)
                    || (p.at(T.CARET) && p.peek(1).type != T.EQ)) {
                String op = p.next().text;
                e = e + " " + op + " " + parseBitAnd();
            }
            return e;
        }

        String parseBitAnd() {
            String e = parseEquality();
            while (p.at(T.AMP) && p.peek(1).type != T.EQ) { p.next(); e = e + " & " + parseEquality(); }
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
            String e = parseShift();
            while (p.at("instanceof") || p.at(T.LE) || p.at(T.GE)
                    || (p.at(T.LT) && p.peek(1).type != T.LT)
                    || (p.at(T.GT) && p.peek(1).type != T.GT)) {
                if (p.at("instanceof")) {
                    // `o instanceof String` → `o instanceof String` (Kof tem
                    // instanceof nativo, training/language/overview.md).
                    p.next();
                    String ty = parseType();
                    // Pattern matching `o instanceof String s` (binding) — Kof
                    // has no binding de pattern; introduzir a variável muda
                    // o fluxo → gap honesto R6 (antes: `expected ')' but
                    // found 's'` confuso).
                    if (p.peek().type == T.IDENT && !p.at("instanceof")) {
                        throw new TranslateException(
                                "pattern matching `instanceof Type var` (binding) has no equivalent "
                                + "direct in Kof (use `instanceof` + cast/`as`) — manual review");
                    }
                    e = e + " instanceof " + ty;
                } else {
                    String op = p.next().text;
                    e = e + " " + op + " " + parseShift();
                }
            }
            return e;
        }

        String parseShift() {
            String e = parseAdd();
            while (true) {
                if (p.at(T.LT) && p.peek(1).type == T.LT && p.peek(2).type != T.EQ) {
                    p.next(); p.next();
                    e = e + " << " + parseAdd();
                } else if (p.at(T.GT) && p.peek(1).type == T.GT && p.peek(2).type == T.GT
                        && p.peek(3).type != T.EQ) {
                    p.next(); p.next(); p.next();
                    e = e + " >>> " + parseAdd();
                } else if (p.at(T.GT) && p.peek(1).type == T.GT
                        && p.peek(2).type != T.GT && p.peek(2).type != T.EQ) {
                    p.next(); p.next();
                    e = e + " >> " + parseAdd();
                } else {
                    break;
                }
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
            if (p.at("~")) {
                // Complemento bit a bit `~x`: Kof has no `~` (PARSE041), mas
                // a identidade `~x == -x - 1` é exata em complemento de dois
                // → emite `(-x - 1)` com parênteses (precedência preservada).
                p.next();
                return "(-" + parseUnary() + " - 1)";
            }
            if (p.at(T.MINUS)) { p.next(); return "-" + parseUnary(); }
            if (p.at(T.PLUS)) { p.next(); return "+" + parseUnary(); }
            if (p.at(T.INC)) { p.next(); return "++" + parseUnary(); }
            if (p.at(T.DEC)) { p.next(); return "--" + parseUnary(); }
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
                    } else if (e.equals("Math")) {
                        // `Math.PI` / `Math.E` — constantes JDK no equivalent
                        // garantido em Kof (`math.*` cobre funções) → gap
                        // honesto R6 em vez de `Math.PI` = SEM011 silencioso.
                        throw new TranslateException(
                                "constant `Math." + field + "` is not resolved by the translator "
                                + "(Kof does not expose the JDK Math class constants) — manual review");
                    } else {
                        e = e + "." + field;
                    }
                } else if (p.at("[")) {
                    p.next();
                    String idx = parseExpr();
                    p.expect("]");
                    e = e + "[" + idx + "]";
                } else if (p.at("(")) {
                    // bare call: foo(args) — sem receiver. Sem este ramo,
                    // `boom("x");` caía em parseExprOrDecl → "expected ';'
                    // but found '('" (descoberto via try/catch 13/09: o corpo
                    // do try quase sempre chama métodos).
                    String args = parseCallArgs();
                    e = e + "(" + args + ")";
                } else if (p.at(":") && p.peek(1).text.equals(":")) {
                    // Method reference `Tipo::metodo` / `obj::metodo` — Kof
                    // has no referência de método (só lambda) → gap honesto
                    // R6 (antes: `expected ')' but found ':'` confuso).
                    throw new TranslateException(
                            "method reference (`::`) has no direct equivalent in Kof "
                            + "(use a lambda `(x) -> ...`) — manual review");
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
            if (receiver.equals("System") && method.equals("currentTimeMillis")) {
                // Tabela JDK→stdlib (mantenedora aprovou 14/10): epoch millis
                p.expect("("); p.expect(")");
                return "time.now()";
            }
            if (receiver.equals("Collections") && method.equals("sort")) {
                // Tabela JDK→stdlib (aprovada): `Collections.sort(xs)` →
                // `xs.sort()` (método da List Kof — medido: roda)
                String arg = parseSingleArg();
                return arg + ".sort()";
            }
            if (receiver.equals("Math")) {
                // `Math.<fn>(...)`: Kof expõe a stdlib em `math.<fn>` mas
                // `math.min/max/abs` são **Int-only** (SEM025 p/ Double, sem
                // widening) e o translator has no tipos p/ escolher o
                // overload → mapear cegamente geraria Kof que não compila;
                // emitir `Math.x(...)` dava `Math` undefined = SEM011
                // silencioso. Mapear Java→stdlib é decisão de design
                // (regra 6) → gap honesto R6 (Q4 13/09).
                throw new TranslateException(
                        "chamada a `Math." + method + "(...)` is not resolved by the translator "
                        + "(Kof uses the `math.*` namespace, but the Double/Int overloads do not map "
                        + "automatically) — manual review");
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
                case STR -> "\"" + TranslateLexer.escapeKofString(t.text) + "\"";
                case CHAR -> "'" + TranslateLexer.escapeKofChar(t.text) + "'";
                case IDENT -> switch (t.text) {
                    case "true" -> "true";
                    case "false" -> "false";
                    case "null" -> "null";
                    case "new" -> TranslateNew.parse(this);
                    case "this" -> "this";
                    case "throw" -> "throw " + parseExpr();
                    case "switch" -> parseSwitchExprHook();
                    default -> {
                        // Referência a tipo QUALIFICADO em expressão
                        // (`java.util.List.of(...)`, `javax.foo.Bar.x`): o
                        // translator ignora imports e Kof referencia por nome
                        // simples — mapear Java→stdlib é decisão de design
                        // (regra 6) → gap honesto R6 (antes: emitia
                        // `java.util.List.of(...)` = Kof inválido/SEM011).
                        if ((t.text.equals("java") || t.text.equals("javax")) && p.at(".")) {
                            throw new TranslateException(
                                    "qualified type in expression (`" + t.text
                                    + ".…`) is not resolved by the translator (imports are ignored) — "
                                    + "manual review");
                        }
                        if (p.at(T.ARROW)) {
                            // Lambda de um parâmetro SEM parênteses (`x -> expr`):
                            // Java permite, Kof exige parênteses (probe: `x -> x`
                            // é PARSE041) → emite `(x) -> expr`. Antes dava
                            // `expected ';' but found '->'` (bug latente Q4).
                            p.next(); // ->
                            String body = p.at("{")
                                    ? "{ " + String.join(" ", parseStatementBlock()) + " }"
                                    : parseExpr();
                            yield "(" + t.text + ") -> " + body;
                        }
                        yield t.text;
                    }
                };
                case P -> {
                    if (t.text.equals("(")) {
                        if (isLambdaAhead()) {
                            yield parseLambda();
                        }
                        if (isCastAhead()) {
                            yield parseCast();
                        }
                        String e = parseExpr();
                        p.expect(")");
                        // PRESERVAR os parênteses: descartá-los muda a
                        // semântica (`(1+2)*3` → `1+2*3` = 7, não 9) — bug
                        // latente de correção achado 13/09 (R6/Q0: compilável
                        // + semântica errada é o pior bug). Kof aceita
                        // parênteses redundantes.
                        yield "(" + e + ")";
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
                boolean typed = TranslateTypes.isPrimitiveOrType(p.peek().text) && p.peek(1).type == T.IDENT;
                if (typed) {
                    String ty = TranslateTypes.kofType(p.next().text);
                    String nm = p.next().text;
                    params.add(nm + ": " + ty);
                    while (p.at(",")) { p.next(); String t2 = TranslateTypes.kofType(p.next().text); String n2 = p.next().text; params.add(n2 + ": " + t2); }
                } else {
                    String nm = p.next().text;
                    params.add(nm);
                    while (p.at(",")) { p.next(); params.add(p.next().text); }
                }
            }
            p.expect(")");
            p.expect("->");
            // Corpo em BLOCO `() -> { ... }` — Kof aceita corpo de bloco em
            // lambda (verificado 13/09: `() -> { counter = counter + 1 }`).
            // Antes o parser só aceitava expressão → `expected ';' but found
            // 'System'` (bug latente Q4).
            String body;
            if (p.at("{")) {
                List<String> stmts = parseStatementBlock();
                body = "{ " + String.join(" ", stmts) + " }";
            } else {
                body = parseExpr();
            }
            return "(" + String.join(", ", params) + ") -> " + body;
        }

        /**
         * Fase F (decisão da mantenedora 14/10): `new Fun() { int apply(int x)
         * { ... } }` → `(x: Int) -> { ... }` — anônima de interface funcional
         * SAM baixa para lambda Kof (a forma idiomática). EXATAMENTE um
         * método; campos/estado e multi-método recusam NOMEADO (uma lambda
         * não tem campos; multi-método não é SAM).
         */
        String parseAnonymousAsLambda() {
            p.expect("{");
            int methods = 0;
            List<String> lambdaParams = List.of();
            List<String> lambdaBody = List.of();
            while (!p.at("}") && !p.at(T.EOF)) {
                if (p.at(";")) { p.next(); continue; }
                // anotações/modificadores do método (@Override, public)
                while (true) {
                    if (TranslateTypes.isModifier(p.peek().text)) { p.next(); continue; }
                    if (p.at(T.AT)) {
                        p.next();
                        while (p.at(".")) { p.next(); p.next(); }
                        if (p.at("(")) { int d = 0; do { if (p.at("(")) { d++; } else if (p.at(")")) { d--; } p.next(); } while (d > 0 && !p.at(T.EOF)); }
                        continue;
                    }
                    break;
                }
                // tipo de retorno (+ genéricos) e nome
                p.next(); // returnType
                if (p.at("<")) { int d = 0; do { if (p.at("<")) { d++; } else if (p.at(">")) { d--; } p.next(); } while (d > 0 && !p.at(T.EOF)); }
                if (!p.at(T.IDENT)) {
                    throw new TranslateException(
                            "anonymous class body: expected a method (fields/state are not part of a "
                            + "single-abstract-method lambda) — manual review");
                }
                p.next(); // member name
                if (!p.at("(")) {
                    // `Type name = ...;` / `Type name;` = CAMPO (estado) — uma
                    // lambda não tem campos; recusa NOMEADA (nunca parse cru).
                    throw new TranslateException(
                            "stateful anonymous class (field `" + p.peek(-1).text + "`) cannot lower "
                            + "to a lambda (a lambda has no fields) — extract it to a named class — "
                            + "manual review");
                }
                // parâmetros tipados `(int x, int y)` — formato do parseLambda
                p.expect("(");
                List<String> params = new ArrayList<>();
                while (!p.at(")")) {
                    if (TranslateTypes.isPrimitiveOrType(p.peek().text) && p.peek(1).type == T.IDENT) {
                        String ty = TranslateTypes.kofType(p.next().text);
                        String nm = p.next().text;
                        params.add(nm + ": " + ty);
                    } else {
                        params.add(p.next().text);
                    }
                    if (p.at(",")) p.next();
                }
                p.expect(")");
                List<String> body = parseStatementBlock();
                methods++;
                if (methods > 1) {
                    throw new TranslateException(
                            "anonymous class with more than one method is not a single-abstract-method "
                            + "functional interface — it cannot lower to a lambda — manual review");
                }
                lambdaParams = params;
                lambdaBody = body;
            }
            p.expect("}");
            if (methods == 0) {
                throw new TranslateException(
                        "anonymous class with an empty body has no abstract method to lower into a "
                        + "lambda — manual review");
            }
            return "(" + String.join(", ", lambdaParams) + ") -> { "
                    + String.join(" ", lambdaBody) + " }";
        }

        /** Lookahead: `(Type) expr` — cast Java. Kof usa `expr as Type`. */
        boolean isCastAhead() {
            // já consumimos '('; olha o próximo token
            String first = p.peek().text;
            if (!TranslateTypes.isPrimitiveOrType(first) && !first.equals("int") && !first.equals("boolean")
                    && !first.equals("char") && !first.equals("long") && !first.equals("double")) {
                return false;
            }
            int i = p.pos + 1;
            // genéricos: (List<String>) x
            if (i < p.toks.size() && p.toks.get(i).text.equals("<")) {
                int depth = 0;
                while (i < p.toks.size()) {
                    String t = p.toks.get(i).text;
                    if (t.equals("<")) depth++;
                    else if (t.equals(">")) { depth--; if (depth == 0) { i++; break; } }
                    i++;
                }
            }
            while (i + 1 < p.toks.size() && p.toks.get(i).text.equals("[")
                    && p.toks.get(i + 1).text.equals("]")) {
                i += 2;
            }
            return i < p.toks.size() && p.toks.get(i).text.equals(")");
        }

        /** `(String) o` → `o as String` (cast de conversão Kof). */
        String parseCast() {
            String type = parseType();
            p.expect(")");
            String operand = parseUnary();
            return operand + " as " + type;
        }

        // ── types ───────────────────────────────────────────────────────────

        String parseType() {
            if (p.at("?")) {
                // Wildcard genérico Java `?`/`? extends X`/`? super X` — Kof
                // rejeita (PARSE086: "Wildcard types ... not supported; use a
                // concrete type or nullable T?"). Sem equivalente direto →
                // gap honesto R6 (bug latente Q4 13/09).
                throw new TranslateException(
                        "generic wildcard (`?`, `? extends`, `? super`) is not supported in Kof "
                        + "(PARSE086; use a concrete type or `T?`) — manual review");
            }
            String base = p.next().text;
            // Tipo qualificado `java.util.Map` → `Map` (stripa o pacote; o
            // translator ignora imports e Kof referencia tipos pelo nome
            // simples). `Map`/`List`/`Set` são builtins Kof.
            while (p.at(".") && p.peek(1).type == T.IDENT) {
                p.next();
                base = p.next().text;
            }
            StringBuilder sb = new StringBuilder(TranslateTypes.kofType(base));
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
                params.add(parseParam());
                while (p.at(",")) { p.next(); params.add(parseParam()); }
            }
            p.expect(")");
            return params;
        }

        private String parseParam() {
            // `final T x` — Kof has no final em parâmetro → descarta.
            while (p.at("final")) p.next();
            String ty = parseType();
            if (p.at(".") && p.peek(1).text.equals(".") && p.peek(2).text.equals(".")) {
                // Java varargs `T...` has no equivalent em função Kof
                // (só builtins setOf/listOf são variádicos). Revisão manual
                // (R6: nunca silencioso).
                throw new TranslateException(
                        "varargs (`T...`) has no direct equivalent in Kof "
                        + "(use `List<T>` or `T[]`) — manual review");
            }
            String nm = p.next().text;
            return ty + " " + nm;
        }

        String paramList(List<String> params) {
            return String.join(", ", params);
        }
}
