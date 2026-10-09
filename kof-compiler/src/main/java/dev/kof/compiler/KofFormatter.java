package dev.kof.compiler;
import dev.kof.compiler.lang.LanguageProfile;
import dev.kof.compiler.lang.SurfaceNames;
import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * KofFormatter — pretty-printer via parser real.
 * Usado por kof fmt (kof-cli). Se o parse falhar, retorna null para fallback token-based.
 *
 * <p>F7.3 (D-PORTUKOF): o printer é DIRIGIDO POR PERFIL. O source `.ptkf` é
 * parseado DIRETO pelo Lexer de perfil (NUNca transpile PT→EN antes do parse):
 * slots de KEYWORD chegam canônicos no AST (medido: `se`→IF "if",
 * `texto`→string) e slots de IDENTIFICADOR/NOME chegam VERBATIM (medido:
 * `escrevaln`, `tamanho`, `principal`, nomes de usuário). O printer reimprime
 * cada slot estrutural pela ponte ÚNICA {@link SurfaceNames#keyword}
 * (keywords léxicas + contextuais do MESMO catálogo gateado; KOF = identidade)
 * e deixa cada slot de nome passar cru. Resultado: a superfície PT sai da AST
 * sem regex, sem replacement textual, sem segunda engine — e Kof fica
 * byte-idêntico ao comportamento histórico.
 */
public final class KofFormatter {
    private static final KofFormatterComments.Pending KOF_PENDING_VOID =
            new KofFormatterComments.Pending(java.util.List.of());

    private KofFormatter() {}

    public static String format(String src, String fileName) {
        LanguageProfile profile = LanguageProfile.forFileName(fileName);
        try {
            DiagnosticCollector diagnostics = new DiagnosticCollector();
            Lexer lexer = new Lexer(src, fileName, diagnostics, profile);
            List<Token> tokens = lexer.tokenize();
            if (diagnostics.hasErrors()) return null;
            Parser parser = new Parser(tokens, diagnostics, fileName, profile);
            CompilationUnitNode unit = parser.parse();
            if (diagnostics.hasErrors()) return null;
            StringBuilder out = new StringBuilder();
            int indent = 0;
            if (!unit.packageName().isEmpty()) {
                out.append(kw(profile, "package")).append(' ').append(unit.packageName()).append("\n\n");
            }
            for (String imp : unit.imports()) {
                if (imp.equals("*")) out.append(kw(profile, "import")).append(" *\n");
                else out.append(kw(profile, "import")).append(' ').append(imp).append("\n");
            }
            if (!unit.imports().isEmpty()) out.append("\n");
            var pending = new KofFormatterComments.Pending(KofFormatterComments.scan(src));
            // O `ForeignModuleNode` (fatia A, `D-CONNECTORS`) nao emite codigo: o
            // parser ja o desdobrou nos `extern` que ele continha, cada um
            // carregando a `library` do cabecalho. Formata-lo como no produz
            // uma linha em branco que sobe no `format()`, quebra o round-trip
            // e polui a saida — entao ele e simplesmente IGNORADO aqui (o
            // `formatDecl` ainda tem o caso, defensivo).
            for (AstNode decl : unit.declarations()) {
                if (decl instanceof ForeignModuleNode) continue;
                if (decl != null) {
                    pending.flushUpTo(out, indent, decl.position());
                }
                formatDecl(decl, out, indent, pending, profile);
                out.append("\n");
            }
            pending.flushAll(out, 0);
            String result = out.toString().trim() + "\n";
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /** Slot estrutural (keyword/tipo/palavra do perfil) na grafia do perfil. */
    static String kw(LanguageProfile p, String canonical) {
        return SurfaceNames.keyword(p, canonical);
    }

    /**
     * Tipo canônico (chegado normalizado do lexer) → grafia do perfil.
     * A string é decomposta em runs de letra/dígito/`_` e o resto (pontos,
     * colchetes, `<`,`>`, `?`, espaços) é costurado cru: `List<Int>?` →
     * `Lista<Int?>?`-style por RUN, sem tocar pontuação. Um run que não é
     * keyword do perfil (nome de usuário, `Ponto`, `Int`) passa IDÊNTICO —
     * regra-ouro: identificador nunca é traduzido.
     */
    static String typeText(LanguageProfile p, String t) {
        if (p == LanguageProfile.KOF || t == null || t.isEmpty()) return t;
        StringBuilder sb = new StringBuilder(t.length());
        int i = 0;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                int j = i;
                while (j < t.length() && (Character.isLetterOrDigit(t.charAt(j)) || t.charAt(j) == '_')) j++;
                sb.append(kw(p, t.substring(i, j)));
                i = j;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** Modificadores/throws na grafia do perfil (runs idênticos ao de tipo). */
    static String joinKw(LanguageProfile p, List<String> words) {
        if (p == LanguageProfile.KOF) return String.join(" ", words);
        List<String> out = new ArrayList<>(words.size());
        for (String w : words) out.add(typeText(p, w));
        return String.join(" ", out);
    }

    static void formatDecl(AstNode decl, StringBuilder out, int indent,
                           KofFormatterComments.Pending pending, LanguageProfile p) {
        if (decl != null) pending.flushUpTo(out, indent, decl.position());
        String pad = "    ".repeat(indent);
        switch (decl) {
            case FunctionDeclarationNode fn -> {
                for (AnnotationNode ann : fn.annotations()) out.append(pad).append(formatAnnotation(ann, p)).append("\n");
                if (!fn.modifiers().isEmpty()) out.append(pad).append(joinKw(p, fn.modifiers())).append(" ");
                else out.append(pad);
                if (!"void".equals(fn.returnType())) out.append(typeText(p, fn.returnType())).append(" ");
                out.append(fn.name());
                if (!fn.typeParameters().isEmpty()) out.append("<").append(joinKw(p, fn.typeParameters())).append(">");
                out.append("(");
                for (int i = 0; i < fn.parameters().size(); i++) {
                    if (i > 0) out.append(", ");
                    out.append(formatParam(fn.parameters().get(i), p));
                }
                out.append(")");
                if (!fn.thrownExceptions().isEmpty()) out.append(" ").append(kw(p, "throw")).append(" ").append(joinKw(p, fn.thrownExceptions()));
                if (fn.body().isEmpty()) {
                    out.append(";\n");
                } else if (fn.body().size() == 1 && fn.body().get(0) instanceof ReturnStmt rs && rs.value() != null) {
                    out.append(" = ").append(KofFormatterExpr.formatExpr(rs.value(), p)).append("\n");
                } else {
                    out.append(" {\n");
                    for (StatementNode st : fn.body()) formatStmt(st, out, indent + 1, pending, p);
                    out.append(pad).append("}\n");
                }
            }
            case ClassDeclarationNode cls -> {
                for (AnnotationNode ann : cls.annotations()) out.append(pad).append(formatAnnotation(ann, p)).append("\n");
                if (!cls.modifiers().isEmpty()) out.append(pad).append(joinKw(p, cls.modifiers())).append(" ");
                else out.append(pad);
                out.append(kw(p, "class")).append(' ').append(cls.name());
                if (!cls.typeParameters().isEmpty()) out.append("<").append(joinKw(p, cls.typeParameters())).append(">");
                if (cls.superClass() != null) out.append(" ").append(kw(p, "extends")).append(' ').append(typeText(p, cls.superClass()));
                if (!cls.interfaces().isEmpty()) out.append(" ").append(kw(p, "implements")).append(' ').append(joinKw(p, cls.interfaces()));
                out.append(" {\n");
                for (AstNode m : cls.members()) {
                    if (m instanceof FieldDeclarationNode f) {
                        for (AnnotationNode ann : f.annotations()) out.append("    ".repeat(indent + 1)).append(formatAnnotation(ann, p)).append("\n");
                        if (!f.modifiers().isEmpty()) out.append("    ".repeat(indent + 1)).append(joinKw(p, f.modifiers())).append(" ");
                        else out.append("    ".repeat(indent + 1));
                        out.append(typeText(p, f.type())).append(" ").append(f.name());
                        if (f.initializer() != null) out.append(" = ").append(KofFormatterExpr.formatExpr(f.initializer(), p));
                        out.append("\n");
                    } else if (m instanceof MethodDeclarationNode md) {
                        for (AnnotationNode ann : md.annotations()) out.append("    ".repeat(indent + 1)).append(formatAnnotation(ann, p)).append("\n");
                        if (!md.modifiers().isEmpty()) out.append("    ".repeat(indent + 1)).append(joinKw(p, md.modifiers())).append(" ");
                        else out.append("    ".repeat(indent + 1));
                        if (!"void".equals(md.returnType())) out.append(typeText(p, md.returnType())).append(" ");
                        out.append(md.name()).append("(");
                        for (int i = 0; i < md.parameters().size(); i++) {
                            if (i > 0) out.append(", ");
                            out.append(formatParam(md.parameters().get(i), p));
                        }
                        out.append(")");
                        if (!md.thrownExceptions().isEmpty()) out.append(" ").append(kw(p, "throw")).append(" ").append(joinKw(p, md.thrownExceptions()));
                        if (md.body().isEmpty()) out.append(";\n");
                        else if (md.body().size() == 1 && md.body().get(0) instanceof ReturnStmt rs && rs.value() != null) {
                            out.append(" = ").append(KofFormatterExpr.formatExpr(rs.value(), p)).append("\n");
                        } else {
                            out.append(" {\n");
                            for (StatementNode st : md.body()) formatStmt(st, out, indent + 2, pending, p);
                            out.append("    ".repeat(indent + 1)).append("}\n");
                        }
                    } else if (m instanceof ConstructorDeclarationNode ctor) {
                        for (AnnotationNode ann : ctor.annotations()) out.append("    ".repeat(indent + 1)).append(formatAnnotation(ann, p)).append("\n");
                        if (!ctor.modifiers().isEmpty()) out.append("    ".repeat(indent + 1)).append(joinKw(p, ctor.modifiers())).append(" ");
                        else out.append("    ".repeat(indent + 1));
                        out.append(kw(p, "constructor")).append("(");
                        for (int i = 0; i < ctor.parameters().size(); i++) {
                            if (i > 0) out.append(", ");
                            out.append(formatParam(ctor.parameters().get(i), p));
                        }
                        out.append(")");
                        if (!ctor.thrownExceptions().isEmpty()) out.append(" ").append(kw(p, "throw")).append(" ").append(joinKw(p, ctor.thrownExceptions()));
                        if (ctor.body().isEmpty()) out.append(" {}\n");
                        else {
                            out.append(" {\n");
                            for (StatementNode st : ctor.body()) formatStmt(st, out, indent + 2, pending, p);
                            out.append("    ".repeat(indent + 1)).append("}\n");
                        }
                    }
                }
                out.append(pad).append("}\n");
            }
            case RecordDeclarationNode rec -> {
                for (AnnotationNode ann : rec.annotations()) out.append(pad).append(formatAnnotation(ann, p)).append("\n");
                if (!rec.modifiers().isEmpty()) out.append(pad).append(joinKw(p, rec.modifiers())).append(" ");
                else out.append(pad);
                out.append(kw(p, "record")).append(' ').append(rec.name());
                out.append("(");
                for (int i = 0; i < rec.components().size(); i++) {
                    if (i > 0) out.append(", ");
                    RecordComponentNode c = rec.components().get(i);
                    if (!c.modifiers().isEmpty()) out.append(joinKw(p, c.modifiers())).append(" ");
                    out.append(typeText(p, c.type())).append(" ").append(c.name());
                    if (c.initializer() != null) out.append(" = ").append(KofFormatterExpr.formatExpr(c.initializer(), p));
                }
                out.append(")");
                if (rec.superClass() != null) out.append(" ").append(kw(p, "extends")).append(' ').append(typeText(p, rec.superClass()));
                if (!rec.interfaces().isEmpty()) out.append(" ").append(kw(p, "implements")).append(' ').append(joinKw(p, rec.interfaces()));
                if (rec.members().isEmpty()) out.append("\n");
                else {
                    out.append(" {\n");
                    for (AstNode m : rec.members()) formatDecl(m, out, indent + 1, pending, p);
                    out.append(pad).append("}\n");
                }
            }
            case EnumDeclarationNode en -> {
                for (AnnotationNode ann : en.annotations()) out.append(pad).append(formatAnnotation(ann, p)).append("\n");
                if (!en.modifiers().isEmpty()) out.append(pad).append(joinKw(p, en.modifiers())).append(" ");
                else out.append(pad);
                out.append(kw(p, "enum")).append(' ').append(en.name()).append(" {\n");
                for (int i = 0; i < en.constants().size(); i++) {
                    out.append("    ".repeat(indent + 1)).append(en.constants().get(i));
                    if (i + 1 < en.constants().size()) out.append(",");
                    out.append("\n");
                }
                out.append(pad).append("}\n");
            }
            case EntityDeclarationNode ent -> {
                for (AnnotationNode ann : ent.annotations()) out.append(pad).append(formatAnnotation(ann, p)).append("\n");
                if (!ent.modifiers().isEmpty()) out.append(pad).append(joinKw(p, ent.modifiers())).append(" ");
                else out.append(pad);
                out.append(kw(p, "entity")).append(' ').append(ent.name()).append(" {\n");
                for (EntityFieldNode f : ent.fields()) {
                    out.append("    ".repeat(indent + 1)).append(f.name()).append(": ").append(typeText(p, f.type()));
                    if (f.generated()) out.append(" ").append(kw(p, "generated"));
                    if (f.unique()) out.append(" ").append(kw(p, "unique"));
                    out.append("\n");
                }
                out.append(pad).append("}\n");
            }
            case TestDeclarationNode t -> {
                out.append(pad).append(kw(p, "test")).append(" \"").append(t.name()).append("\"");
                for (String tag : t.tags()) out.append(", \"").append(tag).append("\"");
                out.append(" {\n");
                for (StatementNode st : t.body()) formatStmt(st, out, indent + 1, pending, p);
                out.append(pad).append("}\n");
            }
            // #719: `extern` e o bloco `foreign module` (fatia A, `D-CONNECTORS`)
            // NAO tinham caso aqui, entao caiam no `default` e eram
            // reimprimidos com `decl.toString()` — um blob
            // `ExternalFunctionNode[position=SourcePosition[...]]` no lugar da
            // declaracao. Com `-w` o CLI escrevia esse blob no arquivo e ainda
            // reportava `1 file(s) reformatted`: perda silenciosa de codigo.
            // Imprimir a declaracao de verdade (a forma e obvia, sem decisao).
            case ExternalFunctionNode ext -> {
                out.append(pad).append(kw(p, "extern"));
                if (ext.library() != null) out.append(" \"").append(ext.library()).append("\"");
                out.append(" ").append(ext.name()).append("(");
                for (int i = 0; i < ext.parameters().size(); i++) {
                    if (i > 0) out.append(", ");
                    out.append(formatParam(ext.parameters().get(i), p));
                }
                out.append(")");
                if (!"void".equals(ext.returnType())) out.append(": ").append(typeText(p, ext.returnType()));
                out.append(";\n");
            }
            // O bloco so tem utilidade com os `extern` que ele contem (o parser
            // ja o desdobrou em `ExternalFunctionNode` herdando a library do
            // cabecalho) — entao nao ha nada a imprimir alem dos `extern`.
            case ForeignModuleNode _ -> {
                // bloco vazio (sem `extern`) nao produz codigo util: o no e
                // informativo e o arquivo segue igual (round-trip estavel).
            }
            case null, default -> {  // null cai aqui (como no if-else: instanceof null == false)
                out.append(pad).append(decl.toString()).append("\n");
            }
        }
    }

    static String formatAnnotation(AnnotationNode ann, LanguageProfile p) {
        if (ann.pairs().isEmpty()) return "@" + ann.name();
        if (ann.singleValue()) return "@" + ann.name() + "(\"" + ann.pairs().get(0).value() + "\")";
        StringBuilder sb = new StringBuilder("@").append(ann.name()).append("(");
        for (int i = 0; i < ann.pairs().size(); i++) {
            if (i > 0) sb.append(", ");
            AnnotationPair ap = ann.pairs().get(i);
            sb.append(ap.key()).append(" = ");
            if (ap.value() instanceof String s) sb.append("\"").append(s).append("\"");
            else sb.append(ap.value());
        }
        sb.append(")");
        return sb.toString();
    }

    static String formatParam(FormalParameterNode fp, LanguageProfile p) {
        StringBuilder sb = new StringBuilder();
        for (AnnotationNode ann : fp.annotations()) sb.append(formatAnnotation(ann, p)).append(" ");
        if (!fp.modifiers().isEmpty()) sb.append(joinKw(p, fp.modifiers())).append(" ");
        if (fp.type() != null && !fp.type().isEmpty() && !"var".equals(fp.type())) {
            sb.append(fp.name()).append(": ").append(typeText(p, fp.type()));
        } else {
            sb.append(typeText(p, fp.type())).append(" ").append(fp.name());
        }
        if (fp.defaultExpression() != null) sb.append(" = ").append(KofFormatterExpr.formatExpr(fp.defaultExpression(), p));
        return sb.toString().trim();
    }

    static void formatStmt(StatementNode st, StringBuilder out, int indent,
                           KofFormatterComments.Pending pending, LanguageProfile p) {
        if (st != null) pending.flushUpTo(out, indent, st.position());
        String pad = "    ".repeat(indent);
        switch (st) {
            case ExpressionStmt es -> {
                if (es.expression() == null) out.append(pad).append(";\n");
                else out.append(pad).append(KofFormatterExpr.formatExpr(es.expression(), p)).append("\n");
            }
            case ReturnStmt rs -> {
                if (rs.value() == null) out.append(pad).append(kw(p, "return")).append("\n");
                else out.append(pad).append(kw(p, "return")).append(' ').append(KofFormatterExpr.formatExpr(rs.value(), p)).append("\n");
            }
            case BlockStmt bs -> {
                out.append(pad).append("{\n");
                for (StatementNode s : bs.statements()) formatStmt(s, out, indent + 1, pending, p);
                out.append(pad).append("}\n");
            }
            case IfStmt is -> {
                out.append(pad).append(kw(p, "if")).append(" (").append(KofFormatterExpr.formatExpr(is.condition(), p)).append(") ");
                formatBody(is.thenBranch(), out, indent, pending, p);
                if (is.elseBranch() != null) {
                    out.append(pad).append(kw(p, "else")).append(' ');
                    formatBody(is.elseBranch(), out, indent, pending, p);
                }
            }
            case WhileStmt ws -> {
                out.append(pad).append(kw(p, "while")).append(" (").append(KofFormatterExpr.formatExpr(ws.condition(), p)).append(") ");
                formatBody(ws.body(), out, indent, pending, p);
            }
            case ForStmt fs -> {
                out.append(pad).append(kw(p, "for")).append(" (");
                if (fs.init() != null) {
                    StringBuilder tmp = new StringBuilder();
                    formatStmt(fs.init(), tmp, 0, KOF_PENDING_VOID, p);
                    out.append(tmp.toString().trim().replace(";", "").trim());
                }
                out.append("; ");
                if (fs.condition() != null) out.append(KofFormatterExpr.formatExpr(fs.condition(), p));
                out.append("; ");
                if (fs.update() != null) out.append(KofFormatterExpr.formatExpr(fs.update(), p));
                out.append(") ");
                formatBody(fs.body(), out, indent, pending, p);
            }
            case ForInStmt fis -> {
                out.append(pad).append(kw(p, "for")).append(" (").append(kw(p, "var")).append(' ')
                        .append(fis.varName()).append(' ').append(kw(p, "in")).append(' ')
                        .append(KofFormatterExpr.formatExpr(fis.collection(), p)).append(") ");
                formatBody(fis.body(), out, indent, pending, p);
            }
            case DoWhileStmt dws -> {
                out.append(pad).append(kw(p, "do")).append(' ');
                formatBody(dws.body(), out, indent, pending, p);
                out.append(pad).append(kw(p, "while")).append(" (").append(KofFormatterExpr.formatExpr(dws.condition(), p)).append(")\n");
            }
            case VarDeclStmt vds -> {
                out.append(pad);
                out.append(typeText(p, vds.type())).append(" ");
                out.append(vds.name());
                if (vds.initializer() != null) out.append(" = ").append(KofFormatterExpr.formatExpr(vds.initializer(), p));
                out.append("\n");
            }
            case ThrowStmt ts -> {
                out.append(pad).append(kw(p, "throw")).append(' ').append(KofFormatterExpr.formatExpr(ts.expression(), p)).append("\n");
            }
            case SpawnStmt ss -> {
                out.append(pad).append(kw(p, "spawn")).append(' ').append(KofFormatterExpr.formatExpr(ss.expression(), p)).append("\n");
            }
            case AssertStmt as -> {
                out.append(pad).append(kw(p, "assert")).append("(").append(KofFormatterExpr.formatExpr(as.condition(), p));
                if (as.message() != null) out.append(", \"").append(as.message()).append("\"");
                out.append(")\n");
            }
            case BreakStmt _ -> {
                out.append(pad).append(kw(p, "break")).append("\n");
            }
            case ContinueStmt _ -> {
                out.append(pad).append(kw(p, "continue")).append("\n");
            }
            case SwitchStmt sw -> {
                out.append(pad).append(kw(p, "switch")).append(" (").append(KofFormatterExpr.formatExpr(sw.expression(), p)).append(") {\n");
                for (SwitchCase c : sw.cases()) {
                    out.append("    ".repeat(indent + 1)).append(kw(p, "case")).append(' ').append(KofFormatterExpr.formatExpr(c.value(), p)).append(":\n");
                    for (StatementNode s : c.body()) formatStmt(s, out, indent + 2, pending, p);
                }
                if (!sw.defaultBody().isEmpty()) {
                    out.append("    ".repeat(indent + 1)).append(kw(p, "default")).append(":\n");
                    for (StatementNode s : sw.defaultBody()) formatStmt(s, out, indent + 2, pending, p);
                }
                out.append(pad).append("}\n");
            }
            case TryStmt ts -> {
                out.append(pad).append(kw(p, "try")).append(" {\n");
                for (StatementNode s : ts.tryBody()) formatStmt(s, out, indent + 1, pending, p);
                out.append(pad).append("}");
                for (CatchClause cc : ts.catchClauses()) {
                    out.append(" ").append(kw(p, "catch")).append(" (").append(typeText(p, cc.exceptionType()))
                            .append(" ").append(cc.exceptionName()).append(") {\n");
                    for (StatementNode s : cc.body()) formatStmt(s, out, indent + 1, pending, p);
                    out.append(pad).append("}");
                }
                if (!ts.finallyBody().isEmpty()) {
                    out.append(" ").append(kw(p, "finally")).append(" {\n");
                    for (StatementNode s : ts.finallyBody()) formatStmt(s, out, indent + 1, pending, p);
                    out.append(pad).append("}");
                }
                out.append("\n");
            }
            case null, default -> {  // null cai aqui (como no if-else: instanceof null == false)
                out.append(pad).append(st.toString()).append("\n");
            }
        }
    }

    /** Corpo de if/while/for/do: bloco inline ou statement indentado. */
    static void formatBody(StatementNode body, StringBuilder out, int indent,
                           KofFormatterComments.Pending pending, LanguageProfile p) {
        if (body instanceof BlockStmt bs) {
            out.append("{\n");
            for (StatementNode s : bs.statements()) formatStmt(s, out, indent + 1, pending, p);
            out.append("    ".repeat(indent)).append("}\n");
        } else {
            out.append("\n");
            formatStmt(body, out, indent + 1, pending, p);
        }
    }


}
