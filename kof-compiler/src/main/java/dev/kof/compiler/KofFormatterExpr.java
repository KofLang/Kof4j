package dev.kof.compiler;

import dev.kof.compiler.lang.LanguageProfile;

/**
 * Impressor de EXPRESSÕES do `KofFormatter` (split por responsabilidade,
 * §Small-parts): cada slot estrutural (keyword/tipo/operador/literal) sai na
 * superficie do perfil pela ponte unica `SurfaceNames` (via
 * `KofFormatter.kw/typeText/joinKw`); slots de nome (identificador/metodo/
 * import) passam VERBATIM — regra-ouro PARTE 5. Precedencia/parenteses:
 * espelho EXATO do parser (#52), identico ao comportamento historico do Kof.
 */
final class KofFormatterExpr {

    private KofFormatterExpr() {}

    static String formatExpr(ExpressionNode expr, LanguageProfile p) {
        return formatExpr(expr, p, 0);
    }

    /**
     * #52 — impressão com reconstrução de agrupamento: parênteses são
     * re-inseridos onde a árvore os exige. A regra espelha EXATAMENTE o
     * parser (ExpressionParser.parseBinary: left = parseUnary, right =
     * parseBinary(prec+1) → operadores ESQUERDA-associativos): um filho
     * binário só é re-parseado sem parênteses quando sua precedência
     * respeita o contexto (left: prec >= prec do pai; right: prec > prec
     * do pai). Operadores unary/calls/if-expr/lambda são atômicos no
     * nível de parseUnary/parsePostfix (precedência 9) — o parser os
     * consome inteiros nesse nível, então nunca precisam de parênteses
     * em si, mas seus operandos/branches são expressões completas.
     */
    static String formatExpr(ExpressionNode expr, LanguageProfile p, int minPrec) {
        if (expr == null) return "";
        if (expr instanceof IdentifierExpr ie) return nameSlot(p, ie.name());
        if (expr instanceof LiteralExpr le) {
            if (le.kind() == ConcreteLiteralKind.STRING) return "\"" + escapeLiteral(le.value()) + "\"";
            if (le.kind() == ConcreteLiteralKind.CHAR) return "'" + escapeLiteral(le.value()) + "'";
            if (le.kind() == ConcreteLiteralKind.BOOLEAN || le.kind() == ConcreteLiteralKind.NULL) {
                return KofFormatter.kw(p, le.value());
            }
            return le.value();
        }
        if (expr instanceof BinaryExpr be) {
            int pr = precOf(be.operator());
            if (pr < minPrec) return "(" + formatExpr(be, p, 0) + ")";
            // left-associativo (parser: left = parseUnary antes do op):
            // filho esquerdo com precedência igual não precisa de parênteses
            // (a - (b - c) != (a - b) - c; a - b - c = (a - b) - c).
            String l = formatExpr(be.left(), p, pr);
            // parser: right = parseBinary(prec + 1) → filho direito com
            // precedência igual PRECISA de parênteses.
            String r = formatExpr(be.right(), p, pr + 1);
            return l + " " + KofFormatter.kw(p, be.operator()) + " " + r;
        }
        if (expr instanceof UnaryExpr ue) {
            if (ue.prefix()) return ue.operator() + formatExpr(ue.operand(), p, 9);
            else return formatExpr(ue.operand(), p, 9) + ue.operator();
        }
        if (expr instanceof AssignmentExpr ae) {
            // parseAssignment: right = parseAssignment (right-associativo,
            // precedência 0, a mais baixa) → a = b = c sem parênteses.
            return formatExpr(ae.target(), p) + " " + ae.operator() + " " + formatExpr(ae.value(), p, 0);
        }
        if (expr instanceof MethodCallExpr mce) {
            StringBuilder sb = new StringBuilder();
            if (mce.receiver() != null) sb.append(formatExpr(mce.receiver(), p)).append(".");
            sb.append(mce.methodName());
            if (!mce.typeArguments().isEmpty()) sb.append("<").append(KofFormatter.joinKw(p, mce.typeArguments())).append(">");
            sb.append("(");
            for (int i = 0; i < mce.arguments().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatExpr(mce.arguments().get(i), p));
            }
            sb.append(")");
            return sb.toString();
        }
        if (expr instanceof NewExpr ne) {
            StringBuilder sb = new StringBuilder(KofFormatter.kw(p, "new")).append(' ').append(KofFormatter.typeText(p, ne.typeName()));
            if (!ne.typeArguments().isEmpty()) sb.append("<").append(KofFormatter.joinKw(p, ne.typeArguments())).append(">");
            sb.append("(");
            for (int i = 0; i < ne.arguments().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatExpr(ne.arguments().get(i), p));
            }
            sb.append(")");
            return sb.toString();
        }
        if (expr instanceof NewArrayExpr nae) {
            StringBuilder sb2 = new StringBuilder(KofFormatter.kw(p, "new")).append(' ').append(KofFormatter.typeText(p, nae.elementType()))
                    .append("[").append(formatExpr(nae.size(), p)).append("]");
            for (ExpressionNode dim : nae.moreDims()) sb2.append("[").append(formatExpr(dim, p)).append("]");
            return sb2.toString();
        }
        if (expr instanceof ArrayAccessExpr aae) return formatExpr(aae.receiver(), p) + "[" + formatExpr(aae.index(), p) + "]";
        if (expr instanceof FieldAccessExpr fae) return formatExpr(fae.receiver(), p) + "." + fae.fieldName();
        if (expr instanceof IfExpr ie) return KofFormatter.kw(p, "if") + " (" + formatExpr(ie.condition(), p) + ") "
                + formatExpr(ie.thenExpr(), p) + " " + KofFormatter.kw(p, "else") + " " + formatExpr(ie.elseExpr(), p);
        if (expr instanceof SwitchExpr se) {
            // #229/§509: o parser do core NÃO aceita `;` entre cases de
            // switch-EXPRESSION (`case 0 -> "zero" default -> "other"`); o
            // printer histórico reimprimia `; ` e quebrava o round-trip em
            // AMBAS as superfícies. Separador = espaço (a forma canônica).
            StringBuilder sb = new StringBuilder(KofFormatter.kw(p, "switch")).append(" (").append(formatExpr(se.expression(), p)).append(") { ");
            for (SwitchExprCase sc : se.cases()) {
                sb.append(KofFormatter.kw(p, "case")).append(' ').append(formatExpr(sc.value(), p)).append(" -> ").append(formatExpr(sc.body(), p)).append(" ");
            }
            if (se.defaultValue() != null) {
                sb.append(KofFormatter.kw(p, "default")).append(" -> ").append(formatExpr(se.defaultValue(), p)).append(" ");
            }
            sb.append("}");
            return sb.toString();
        }
        if (expr instanceof LambdaExpr le) {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < le.parameters().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(KofFormatter.formatParam(le.parameters().get(i), p));
            }
            sb.append(") -> ");
            if (le.body().size() == 1 && le.body().get(0) instanceof ReturnStmt rs && rs.value() != null) sb.append(formatExpr(rs.value(), p));
            else {
                sb.append("{\n");
                for (StatementNode s : le.body()) KofFormatter.formatStmt(s, sb, 1, new KofFormatterComments.Pending(java.util.List.of()), p);
                sb.append("}");
            }
            return sb.toString();
        }
        if (expr instanceof PatternExpr pe) {
            if (!pe.fieldVars().isEmpty()) return KofFormatter.typeText(p, pe.typeName()) + "(" + String.join(", ", pe.fieldVars()) + ")";
            if (pe.varName() != null) return KofFormatter.typeText(p, pe.typeName()) + " " + pe.varName();
            return KofFormatter.typeText(p, pe.typeName());
        }
        return expr.toString();
    }

    /**
     * Slot de nome-identificador: `this`/`super` chegam canonicos do lexer
     * (medido: `este` → IdentifierExpr "this") e saem na grafia do perfil;
     * QUALQUER outro nome passa VERBATIM — o canônico de um identificador é a
     * grafia digitada (PARTE 5), inclusive builtins de chamada nua
     * (`escrevaln`, `tamanho` — medido U1).
     */
    private static String nameSlot(LanguageProfile p, String name) {
        if ("this".equals(name) || "super".equals(name)) return KofFormatter.kw(p, name);
        return name;
    }

    // #447/§305: LiteralExpr.value() chega ja DECODIFICADO pelo lexer
    // (readEscape resolve n t r \\ ' " 0 uXXXX). Reimprimir cru quebra o
    // round-trip ('\\' vira '\' = LEX004; '\t' vira TAB mentido). Re-emite
    // exatamente o vocabulario do Lexer; nao-ASCII fica cru (nao-lossy).
    static String escapeLiteral(String v) {
        if (v == null) return "";
        StringBuilder sb = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                case '\'' -> sb.append("\\'");
                case '"' -> sb.append("\\\"");
                case '\0' -> sb.append("\\0");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }


    /**
     * #52 — impressão com reconstrução de agrupamento: parênteses são
     * re-inseridos onde a árvore os exige. A regra espelha EXATAMENTE o
     * parser (ExpressionParser.parseBinary: left = parseUnary, right =
     * parseBinary(prec+1) → operadores ESQUERDA-associativos): um filho
     * binário só é re-parseado sem parênteses quando sua precedência
     * respeita o contexto (left: prec >= prec do pai; right: prec > prec
     * do pai). Operadores unary/calls/if-expr/lambda são atômicos no
     * nível de parseUnary/parsePostfix (precedência 9) — o parser os
     * consome inteiros nesse nível, então nunca precisam de parênteses
     * em si, mas seus operandos/branches são expressões completas.
     */
    /** Espelho de ExpressionParser.precedence (fonte: o parser, não a memória). */
    static int precOf(String op) {
        return switch (op) {
            case "||" -> 1;
            case "&&" -> 2;
            case "|", "^" -> 3;
            case "&" -> 4;
            case "==", "!=", "<", "<=", ">", ">=", "instanceof", "as" -> 5;
            case "<<", ">>", ">>>" -> 6;
            case "+", "-" -> 7;
            case "*", "/", "%" -> 8;
            default -> 0;
        };
    }
}
