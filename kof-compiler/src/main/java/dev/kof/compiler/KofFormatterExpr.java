package dev.kof.compiler;

import dev.kof.compiler.lang.LanguageProfile;

import java.util.List;

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
            // #1013: o receiver é sufixo POSTFIX (precedência 9 — o parser
            // consome `.m`/`(...)`/`[i]` em parsePostfix, acima de qualquer
            // binário) — um receiver binário (`("" + x).trim()`) PRECISA dos
            // parênteses de volta; sem isto o fmt reescreve outro programa.
            // #1013 + §786: o receiver de sufixo liga mais apertado que
            // qualquer operador — o helper receiver() formata em 10 E envolve
            // unary/assignment/if/switch/lambda em parênteses (o minPrec 9
            // inline deixava o receiver UNÁRIO nu: (-n).toString() →
            // -n.toString(), outro programa).
            if (mce.receiver() != null) sb.append(receiver(mce.receiver(), p)).append(mce.methodName().isEmpty() ? "" : ".");
            sb.append(mce.methodName());
            if (!mce.typeArguments().isEmpty()) sb.append("<").append(KofFormatter.typeList(p, mce.typeArguments())).append(">");
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
            if (!ne.typeArguments().isEmpty()) sb.append("<").append(KofFormatter.typeList(p, ne.typeArguments())).append(">");
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
        if (expr instanceof ArrayAccessExpr aae) return receiver(aae.receiver(), p) + "[" + formatExpr(aae.index(), p, 9) + "]";
        if (expr instanceof FieldAccessExpr fae) return receiver(fae.receiver(), p) + "." + fae.fieldName();
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
                FormalParameterNode fp = le.parameters().get(i);
                if ("Object".equals(fp.type()) && fp.modifiers().isEmpty() && fp.annotations().isEmpty()
                        && fp.defaultExpression() == null) {
                    sb.append(fp.name());
                } else {
                    sb.append(KofFormatter.formatParam(fp, p));
                }
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
            String head = KofFormatter.typeText(p, pe.typeName());
            if (!pe.fieldVars().isEmpty()) head += "(" + String.join(", ", pe.fieldVars()) + ")";
            else if (pe.varName() != null) head += " " + pe.varName();
            return pe.guard() == null ? head : head + " " + KofFormatter.kw(p, "if") + " " + formatExpr(pe.guard(), p);
        }
        return expr.toString();
    }

    /**
     * §786: the receiver of a call, field access or index binds tighter than
     * any operator (parsePostfix), so a binary/unary/assignment/if/switch/
     * lambda receiver keeps its parentheses: `("" + x).trim()` printed as
     * `"" + x.trim()` was a different program (and `(o as T).f()` lost its cast).
     */
    private static String receiver(ExpressionNode r, LanguageProfile p) {
        String s = formatExpr(r, p, 10);
        return r instanceof UnaryExpr || r instanceof AssignmentExpr || r instanceof IfExpr
                || r instanceof SwitchExpr || r instanceof LambdaExpr ? "(" + s + ")" : s;
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

    // ---- Kof.expr spike-0 (D-KOF-EXPR) — impressão do padrão ----------------
    // Um `expr` é código-fonte como qualquer outro: o round-trip com `-w`
    // devolve a MESMA gramática que o parser lê (unidades separadas por
    // espaço, quantificador com blocos, `either` com `or`).

    static void formatExprDecl(ExprDeclarationNode ex, StringBuilder out, int indent, LanguageProfile p) {
        String pad = "    ".repeat(indent);
        for (AnnotationNode ann : ex.annotations()) {
            out.append(pad).append(KofFormatter.formatAnnotation(ann, p)).append("\n");
        }
        if (!ex.modifiers().isEmpty()) out.append(pad).append(KofFormatter.joinKw(p, ex.modifiers())).append(" ");
        else out.append(pad);
        out.append(KofFormatter.kw(p, "expr")).append(' ').append(ex.name()).append(" {\n");
        formatPattern(ex.body(), out, indent + 1, p);
        out.append(pad).append("}\n");
    }

    /** Sequência como linhas do bloco; uma unidade por linha, indentada. */
    static void formatPattern(ExprNode node, StringBuilder out, int indent, LanguageProfile p) {
        String pad = "    ".repeat(indent);
        if (node instanceof ExprSequenceNode seq) {
            for (ExprNode u : seq.units()) formatPattern(u, out, indent, p);
            return;
        }
        out.append(pad).append(unitText(node, p)).append("\n");
    }

    /** Forma inline de uma unidade — re-parseável: `one_or_more { a b }`. */
    static String unitText(ExprNode node, LanguageProfile p) {
        if (node instanceof ExprAtomNode a) return atomText(a, p);
        if (node instanceof ExprReferenceNode r) return r.alias();
        if (node instanceof ExprSequenceNode s) return sequenceText(s, p);
        if (node instanceof ExprQuantifierNode q) {
            String head = KofFormatter.kw(p, q.kind());
            if (q.kind().equals("exactly")) head += "(" + q.min() + ")";
            else if (q.kind().equals("between")) head += "(" + q.min() + ", " + q.max() + ")";
            return head + " { " + sequenceText(asSequence(q.target()), p) + " }";
        }
        if (node instanceof ExprCaptureNode c) {
            return KofFormatter.kw(p, "capture") + "(\"" + escapeLiteral(c.name()) + "\")"
                    + " { " + sequenceText(asSequence(c.body()), p) + " }";
        }
        if (node instanceof ExprAlternationNode alt) {
            List<String> branches = new java.util.ArrayList<>();
            for (ExprNode b : alt.alternatives()) branches.add(sequenceText(asSequence(b), p));
            return KofFormatter.kw(p, "either") + " { "
                    + String.join(" " + KofFormatter.kw(p, "or") + " ", branches) + " }";
        }
        return "";
    }

    private static ExprSequenceNode asSequence(ExprNode node) {
        if (node instanceof ExprSequenceNode s) return s;
        return new ExprSequenceNode(node.position(), List.of(node));
    }

    private static String sequenceText(ExprSequenceNode seq, LanguageProfile p) {
        List<String> parts = new java.util.ArrayList<>();
        for (ExprNode u : seq.units()) parts.add(unitText(u, p));
        return String.join(" ", parts);
    }

    private static String atomText(ExprAtomNode a, LanguageProfile p) {
        if (a.kind().equals("literal") && a.values().size() == 1) {
            return "\"" + escapeLiteral(a.values().get(0)) + "\"";
        }
        if (a.kind().equals("one_of") || a.kind().equals("none_of") || a.kind().equals("literal")) {
            List<String> vals = new java.util.ArrayList<>();
            for (String v : a.values()) vals.add("\"" + escapeLiteral(v) + "\"");
            return KofFormatter.kw(p, a.kind()) + "(" + String.join(", ", vals) + ")";
        }
        return KofFormatter.kw(p, a.kind());
    }
}
