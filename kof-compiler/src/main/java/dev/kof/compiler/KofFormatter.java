package dev.kof.compiler;

import java.util.List;

/**
 * KofFormatter — pretty-printer via parser real.
 * Usado por kof fmt (kof-cli). Se o parse falhar, retorna null para fallback token-based.
 */
public final class KofFormatter {
    private KofFormatter() {}

    public static String format(String src, String fileName) {
        try {
            DiagnosticCollector diagnostics = new DiagnosticCollector();
            Lexer lexer = new Lexer(src, fileName, diagnostics);
            List<Token> tokens = lexer.tokenize();
            if (diagnostics.hasErrors()) return null;
            Parser parser = new Parser(tokens, diagnostics, fileName);
            CompilationUnitNode unit = parser.parse();
            if (diagnostics.hasErrors()) return null;
            StringBuilder out = new StringBuilder();
            int indent = 0;
            if (!unit.packageName().isEmpty()) {
                out.append("package ").append(unit.packageName()).append("\n\n");
            }
            for (String imp : unit.imports()) {
                if (imp.equals("*")) out.append("import *\n");
                else if (imp.endsWith(".*")) out.append("import ").append(imp).append("\n");
                else out.append("import ").append(imp).append("\n");
            }
            if (!unit.imports().isEmpty()) out.append("\n");
            for (AstNode decl : unit.declarations()) {
                formatDecl(decl, out, indent);
                out.append("\n");
            }
            String result = out.toString().trim() + "\n";
            if (result.length() < src.length() * 0.5) return null;
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    static void formatDecl(AstNode decl, StringBuilder out, int indent) {
        String pad = "    ".repeat(indent);
        if (decl instanceof FunctionDeclarationNode fn) {
            for (AnnotationNode ann : fn.annotations()) out.append(pad).append(formatAnnotation(ann)).append("\n");
            if (!fn.modifiers().isEmpty()) out.append(pad).append(String.join(" ", fn.modifiers())).append(" ");
            else out.append(pad);
            if (!"void".equals(fn.returnType())) out.append(fn.returnType()).append(" ");
            out.append(fn.name());
            if (!fn.typeParameters().isEmpty()) out.append("<").append(String.join(", ", fn.typeParameters())).append(">");
            out.append("(");
            for (int i = 0; i < fn.parameters().size(); i++) {
                if (i > 0) out.append(", ");
                out.append(formatParam(fn.parameters().get(i)));
            }
            out.append(")");
            if (!fn.thrownExceptions().isEmpty()) out.append(" throw ").append(String.join(", ", fn.thrownExceptions()));
            if (fn.body().isEmpty()) {
                out.append(";\n");
            } else if (fn.body().size() == 1 && fn.body().get(0) instanceof ReturnStmt rs && rs.value() != null) {
                out.append(" = ").append(formatExpr(rs.value())).append("\n");
            } else {
                out.append(" {\n");
                for (StatementNode st : fn.body()) formatStmt(st, out, indent + 1);
                out.append(pad).append("}\n");
            }
        } else if (decl instanceof ClassDeclarationNode cls) {
            for (AnnotationNode ann : cls.annotations()) out.append(pad).append(formatAnnotation(ann)).append("\n");
            if (!cls.modifiers().isEmpty()) out.append(pad).append(String.join(" ", cls.modifiers())).append(" ");
            else out.append(pad);
            out.append("class ").append(cls.name());
            if (!cls.typeParameters().isEmpty()) out.append("<").append(String.join(", ", cls.typeParameters())).append(">");
            if (cls.superClass() != null) out.append(" extends ").append(cls.superClass());
            if (!cls.interfaces().isEmpty()) out.append(" implements ").append(String.join(", ", cls.interfaces()));
            out.append(" {\n");
            for (AstNode m : cls.members()) {
                if (m instanceof FieldDeclarationNode f) {
                    for (AnnotationNode ann : f.annotations()) out.append("    ".repeat(indent + 1)).append(formatAnnotation(ann)).append("\n");
                    if (!f.modifiers().isEmpty()) out.append("    ".repeat(indent + 1)).append(String.join(" ", f.modifiers())).append(" ");
                    else out.append("    ".repeat(indent + 1));
                    out.append(f.type()).append(" ").append(f.name());
                    if (f.initializer() != null) out.append(" = ").append(formatExpr(f.initializer()));
                    out.append("\n");
                } else if (m instanceof MethodDeclarationNode md) {
                    for (AnnotationNode ann : md.annotations()) out.append("    ".repeat(indent + 1)).append(formatAnnotation(ann)).append("\n");
                    if (!md.modifiers().isEmpty()) out.append("    ".repeat(indent + 1)).append(String.join(" ", md.modifiers())).append(" ");
                    else out.append("    ".repeat(indent + 1));
                    if (!"void".equals(md.returnType())) out.append(md.returnType()).append(" ");
                    out.append(md.name()).append("(");
                    for (int i = 0; i < md.parameters().size(); i++) {
                        if (i > 0) out.append(", ");
                        out.append(formatParam(md.parameters().get(i)));
                    }
                    out.append(")");
                    if (!md.thrownExceptions().isEmpty()) out.append(" throw ").append(String.join(", ", md.thrownExceptions()));
                    if (md.body().isEmpty()) out.append(";\n");
                    else if (md.body().size() == 1 && md.body().get(0) instanceof ReturnStmt rs && rs.value() != null) {
                        out.append(" = ").append(formatExpr(rs.value())).append("\n");
                    } else {
                        out.append(" {\n");
                        for (StatementNode st : md.body()) formatStmt(st, out, indent + 2);
                        out.append("    ".repeat(indent + 1)).append("}\n");
                    }
                } else if (m instanceof ConstructorDeclarationNode ctor) {
                    for (AnnotationNode ann : ctor.annotations()) out.append("    ".repeat(indent + 1)).append(formatAnnotation(ann)).append("\n");
                    if (!ctor.modifiers().isEmpty()) out.append("    ".repeat(indent + 1)).append(String.join(" ", ctor.modifiers())).append(" ");
                    else out.append("    ".repeat(indent + 1));
                    out.append("constructor(");
                    for (int i = 0; i < ctor.parameters().size(); i++) {
                        if (i > 0) out.append(", ");
                        out.append(formatParam(ctor.parameters().get(i)));
                    }
                    out.append(")");
                    if (!ctor.thrownExceptions().isEmpty()) out.append(" throw ").append(String.join(", ", ctor.thrownExceptions()));
                    if (ctor.body().isEmpty()) out.append(" {}\n");
                    else {
                        out.append(" {\n");
                        for (StatementNode st : ctor.body()) formatStmt(st, out, indent + 2);
                        out.append("    ".repeat(indent + 1)).append("}\n");
                    }
                }
            }
            out.append(pad).append("}\n");
        } else if (decl instanceof RecordDeclarationNode rec) {
            for (AnnotationNode ann : rec.annotations()) out.append(pad).append(formatAnnotation(ann)).append("\n");
            if (!rec.modifiers().isEmpty()) out.append(pad).append(String.join(" ", rec.modifiers())).append(" ");
            else out.append(pad);
            out.append("record ").append(rec.name());
            out.append("(");
            for (int i = 0; i < rec.components().size(); i++) {
                if (i > 0) out.append(", ");
                RecordComponentNode c = rec.components().get(i);
                if (!c.modifiers().isEmpty()) out.append(String.join(" ", c.modifiers())).append(" ");
                out.append(c.type()).append(" ").append(c.name());
                if (c.initializer() != null) out.append(" = ").append(formatExpr(c.initializer()));
            }
            out.append(")");
            if (rec.superClass() != null) out.append(" extends ").append(rec.superClass());
            if (!rec.interfaces().isEmpty()) out.append(" implements ").append(String.join(", ", rec.interfaces()));
            if (rec.members().isEmpty()) out.append("\n");
            else {
                out.append(" {\n");
                for (AstNode m : rec.members()) formatDecl(m, out, indent + 1);
                out.append(pad).append("}\n");
            }
        } else if (decl instanceof EnumDeclarationNode en) {
            for (AnnotationNode ann : en.annotations()) out.append(pad).append(formatAnnotation(ann)).append("\n");
            if (!en.modifiers().isEmpty()) out.append(pad).append(String.join(" ", en.modifiers())).append(" ");
            else out.append(pad);
            out.append("enum ").append(en.name()).append(" {\n");
            for (int i = 0; i < en.constants().size(); i++) {
                out.append("    ".repeat(indent + 1)).append(en.constants().get(i));
                if (i + 1 < en.constants().size()) out.append(",");
                out.append("\n");
            }
            out.append(pad).append("}\n");
        } else if (decl instanceof EntityDeclarationNode ent) {
            for (AnnotationNode ann : ent.annotations()) out.append(pad).append(formatAnnotation(ann)).append("\n");
            if (!ent.modifiers().isEmpty()) out.append(pad).append(String.join(" ", ent.modifiers())).append(" ");
            else out.append(pad);
            out.append("entity ").append(ent.name()).append(" {\n");
            for (EntityFieldNode f : ent.fields()) {
                out.append("    ".repeat(indent + 1)).append(f.name()).append(": ").append(f.type());
                if (f.generated()) out.append(" generated");
                if (f.unique()) out.append(" unique");
                out.append("\n");
            }
            out.append(pad).append("}\n");
        } else if (decl instanceof TestDeclarationNode t) {
            out.append(pad).append("test \"").append(t.name()).append("\" {\n");
            for (StatementNode st : t.body()) formatStmt(st, out, indent + 1);
            out.append(pad).append("}\n");
        } else {
            out.append(pad).append(decl.toString()).append("\n");
        }
    }

    static String formatAnnotation(AnnotationNode ann) {
        if (ann.pairs().isEmpty()) return "@" + ann.name();
        if (ann.singleValue()) return "@" + ann.name() + "(\"" + ann.pairs().get(0).value() + "\")";
        StringBuilder sb = new StringBuilder("@").append(ann.name()).append("(");
        for (int i = 0; i < ann.pairs().size(); i++) {
            if (i > 0) sb.append(", ");
            AnnotationPair p = ann.pairs().get(i);
            sb.append(p.key()).append(" = ");
            if (p.value() instanceof String s) sb.append("\"").append(s).append("\"");
            else sb.append(p.value());
        }
        sb.append(")");
        return sb.toString();
    }

    static String formatParam(FormalParameterNode p) {
        StringBuilder sb = new StringBuilder();
        for (AnnotationNode ann : p.annotations()) sb.append(formatAnnotation(ann)).append(" ");
        if (!p.modifiers().isEmpty()) sb.append(String.join(" ", p.modifiers())).append(" ");
        if (p.type() != null && !p.type().isEmpty() && !"var".equals(p.type())) {
            sb.append(p.name()).append(": ").append(p.type());
        } else {
            sb.append(p.type()).append(" ").append(p.name());
        }
        if (p.defaultExpression() != null) sb.append(" = ").append(formatExpr(p.defaultExpression()));
        return sb.toString().trim();
    }

    static void formatStmt(StatementNode st, StringBuilder out, int indent) {
        String pad = "    ".repeat(indent);
        if (st instanceof ExpressionStmt es) {
            if (es.expression() == null) out.append(pad).append(";\n");
            else out.append(pad).append(formatExpr(es.expression())).append("\n");
        } else if (st instanceof ReturnStmt rs) {
            if (rs.value() == null) out.append(pad).append("return\n");
            else out.append(pad).append("return ").append(formatExpr(rs.value())).append("\n");
        } else if (st instanceof BlockStmt bs) {
            out.append(pad).append("{\n");
            for (StatementNode s : bs.statements()) formatStmt(s, out, indent + 1);
            out.append(pad).append("}\n");
        } else if (st instanceof IfStmt is) {
            out.append(pad).append("if (").append(formatExpr(is.condition())).append(") ");
            formatBody(is.thenBranch(), out, indent);
            if (is.elseBranch() != null) {
                out.append(pad).append("else ");
                formatBody(is.elseBranch(), out, indent);
            }
        } else if (st instanceof WhileStmt ws) {
            out.append(pad).append("while (").append(formatExpr(ws.condition())).append(") ");
            formatBody(ws.body(), out, indent);
        } else if (st instanceof ForStmt fs) {
            out.append(pad).append("for (");
            if (fs.init() != null) {
                StringBuilder tmp = new StringBuilder();
                formatStmt(fs.init(), tmp, 0);
                out.append(tmp.toString().trim().replace(";", "").trim());
            }
            out.append("; ");
            if (fs.condition() != null) out.append(formatExpr(fs.condition()));
            out.append("; ");
            if (fs.update() != null) out.append(formatExpr(fs.update()));
            out.append(") ");
            formatBody(fs.body(), out, indent);
        } else if (st instanceof ForInStmt fis) {
            out.append(pad).append("for (var ").append(fis.varName()).append(" in ").append(formatExpr(fis.collection())).append(") ");
            formatBody(fis.body(), out, indent);
        } else if (st instanceof DoWhileStmt dws) {
            out.append(pad).append("do ");
            formatBody(dws.body(), out, indent);
            out.append(pad).append("while (").append(formatExpr(dws.condition())).append(")\n");
        } else if (st instanceof VarDeclStmt vds) {
            out.append(pad);
            if (!"var".equals(vds.type()) && !"val".equals(vds.type())) out.append(vds.type()).append(" ");
            else out.append(vds.type()).append(" ");
            out.append(vds.name());
            if (vds.initializer() != null) out.append(" = ").append(formatExpr(vds.initializer()));
            out.append("\n");
        } else if (st instanceof ThrowStmt ts) {
            out.append(pad).append("throw ").append(formatExpr(ts.expression())).append("\n");
        } else if (st instanceof SpawnStmt ss) {
            out.append(pad).append("spawn ").append(formatExpr(ss.expression())).append("\n");
        } else if (st instanceof AssertStmt as) {
            out.append(pad).append("assert(").append(formatExpr(as.condition()));
            if (as.message() != null) out.append(", \"").append(as.message()).append("\"");
            out.append(")\n");
        } else if (st instanceof BreakStmt) {
            out.append(pad).append("break\n");
        } else if (st instanceof ContinueStmt) {
            out.append(pad).append("continue\n");
        } else if (st instanceof SwitchStmt sw) {
            out.append(pad).append("switch (").append(formatExpr(sw.expression())).append(") {\n");
            for (SwitchCase c : sw.cases()) {
                out.append("    ".repeat(indent + 1)).append("case ").append(formatExpr(c.value())).append(":\n");
                for (StatementNode s : c.body()) formatStmt(s, out, indent + 2);
            }
            if (!sw.defaultBody().isEmpty()) {
                out.append("    ".repeat(indent + 1)).append("default:\n");
                for (StatementNode s : sw.defaultBody()) formatStmt(s, out, indent + 2);
            }
            out.append(pad).append("}\n");
        } else if (st instanceof TryStmt ts) {
            out.append(pad).append("try {\n");
            for (StatementNode s : ts.tryBody()) formatStmt(s, out, indent + 1);
            out.append(pad).append("}");
            for (CatchClause cc : ts.catchClauses()) {
                out.append(" catch (").append(cc.exceptionType()).append(" ").append(cc.exceptionName()).append(") {\n");
                for (StatementNode s : cc.body()) formatStmt(s, out, indent + 1);
                out.append(pad).append("}");
            }
            if (!ts.finallyBody().isEmpty()) {
                out.append(" finally {\n");
                for (StatementNode s : ts.finallyBody()) formatStmt(s, out, indent + 1);
                out.append(pad).append("}");
            }
            out.append("\n");
        } else if (st instanceof DoWhileStmt dws) {
            out.append(pad).append("do ");
            formatStmt(dws.body(), out, indent);
            out.append(pad).append("while (").append(formatExpr(dws.condition())).append(")\n");
        } else {
            out.append(pad).append(st.toString()).append("\n");
        }
    }

    /** Corpo de if/while/for/do: bloco inline ou statement indentado. */
    static void formatBody(StatementNode body, StringBuilder out, int indent) {
        if (body instanceof BlockStmt bs) {
            out.append("{\n");
            for (StatementNode s : bs.statements()) formatStmt(s, out, indent + 1);
            out.append("    ".repeat(indent)).append("}\n");
        } else {
            out.append("\n");
            formatStmt(body, out, indent + 1);
        }
    }

    static String formatExpr(ExpressionNode expr) {
        if (expr == null) return "";
        if (expr instanceof IdentifierExpr ie) return ie.name();
        if (expr instanceof LiteralExpr le) {
            if (le.kind() == ConcreteLiteralKind.STRING) return "\"" + le.value() + "\"";
            if (le.kind() == ConcreteLiteralKind.CHAR) return "'" + le.value() + "'";
            return le.value();
        }
        if (expr instanceof BinaryExpr be) return formatExpr(be.left()) + " " + be.operator() + " " + formatExpr(be.right());
        if (expr instanceof UnaryExpr ue) {
            if (ue.prefix()) return ue.operator() + formatExpr(ue.operand());
            else return formatExpr(ue.operand()) + ue.operator();
        }
        if (expr instanceof AssignmentExpr ae) return formatExpr(ae.target()) + " " + ae.operator() + " " + formatExpr(ae.value());
        if (expr instanceof MethodCallExpr mce) {
            StringBuilder sb = new StringBuilder();
            if (mce.receiver() != null) sb.append(formatExpr(mce.receiver())).append(".");
            sb.append(mce.methodName());
            if (!mce.typeArguments().isEmpty()) sb.append("<").append(String.join(", ", mce.typeArguments())).append(">");
            sb.append("(");
            for (int i = 0; i < mce.arguments().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatExpr(mce.arguments().get(i)));
            }
            sb.append(")");
            return sb.toString();
        }
        if (expr instanceof NewExpr ne) {
            StringBuilder sb = new StringBuilder("new ").append(ne.typeName());
            if (!ne.typeArguments().isEmpty()) sb.append("<").append(String.join(", ", ne.typeArguments())).append(">");
            sb.append("(");
            for (int i = 0; i < ne.arguments().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatExpr(ne.arguments().get(i)));
            }
            sb.append(")");
            return sb.toString();
        }
        if (expr instanceof NewArrayExpr nae) return "new " + nae.elementType() + "[" + formatExpr(nae.size()) + "]";
        if (expr instanceof ArrayAccessExpr aae) return formatExpr(aae.receiver()) + "[" + formatExpr(aae.index()) + "]";
        if (expr instanceof FieldAccessExpr fae) return formatExpr(fae.receiver()) + "." + fae.fieldName();
        if (expr instanceof IfExpr ie) return "if (" + formatExpr(ie.condition()) + ") " + formatExpr(ie.thenExpr()) + " else " + formatExpr(ie.elseExpr());
        if (expr instanceof SwitchExpr se) {
            StringBuilder sb = new StringBuilder("switch (").append(formatExpr(se.expression())).append(") { ");
            for (SwitchExprCase sc : se.cases()) {
                sb.append("case ").append(formatExpr(sc.value())).append(" -> ").append(formatExpr(sc.body())).append("; ");
            }
            if (se.defaultValue() != null) {
                sb.append("default -> ").append(formatExpr(se.defaultValue())).append("; ");
            }
            sb.append("}");
            return sb.toString();
        }
        if (expr instanceof LambdaExpr le) {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < le.parameters().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatParam(le.parameters().get(i)));
            }
            sb.append(") -> ");
            if (le.body().size() == 1 && le.body().get(0) instanceof ReturnStmt rs && rs.value() != null) sb.append(formatExpr(rs.value()));
            else {
                sb.append("{\n");
                for (StatementNode s : le.body()) formatStmt(s, sb, 1);
                sb.append("}");
            }
            return sb.toString();
        }
        if (expr instanceof PatternExpr pe) {
            if (!pe.fieldVars().isEmpty()) return pe.typeName() + "(" + String.join(", ", pe.fieldVars()) + ")";
            if (pe.varName() != null) return pe.typeName() + " " + pe.varName();
            return pe.typeName();
        }
        return expr.toString();
    }
}
