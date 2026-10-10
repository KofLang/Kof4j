package dev.kof.compiler;

import java.util.List;

/**
 * #339/#340/#341 — validação de FORMA de classe (a JVM rejeita no load;
 * o compilador deve dizer antes — R6, mesmo padrão de #321/#331/#332/#328).
 *
 *  - #341: `final abstract class X` é contradição de modificadores — a
 *    declaração não tem instância possível nem subclasse possível. SEM069.
 *  - #339: `class D extends F` onde `F` é `final` — a JVM grava a classe
 *    com ACC_FINAL (medido) e morre no load com
 *    `IncompatibleClassChangeError: class D cannot inherit from final
 *    class F`. SEM070 no compile.
 *  - #340: `new I()` onde `I` é `interface` — mesmo contrato do SEM041 de
 *    classe abstrata (SG-017): interface não é instanciável. SEM071.
 *
 * Todos aditivos: código que compila E RODA hoje não é tocado (esses
 * programas crashes no load na ponta — rejeitá-los no compile não é
 * regressão, é a correção pedida pelas próprias issues).
 */
public final class ClassShapeChecks {

    private ClassShapeChecks() {}

    /** Chamado em analyzeClass — contradição final+abstract (#341) e
     *  extends de classe final (#339). */
    static void checkClassDeclaration(SemanticAnalyzer sa, ClassDeclarationNode cls) {
        List<String> mods = cls.modifiers();
        if (mods.contains("final") && mods.contains("abstract")) {
            report(sa, cls.position(),
                    "class '" + cls.name() + "' cannot be both 'final' and 'abstract'"
                            + " — 'final' forbids subclasses, 'abstract' requires them",
                    "SEM069");
        }
        String superName = eraseGenerics(cls.superClass());
        if (superName != null && sa.finalClasses().contains(simpleName(superName))) {
            report(sa, cls.position(),
                    "cannot inherit from final class '" + superName + "'"
                            + " (declared 'final' — remove 'final' or the inheritance)",
                    "SEM070");
        }
        // X5.1 (D-X5-SURFACE): subtipo de tipo `sealed` só na mesma unidade.
        SealedTypeChecks.checkSubtype(sa, cls.position(), cls.name(),
                cls.superClass(), cls.interfaces());
        // X5.3 (D-TYPE-VARIANCE): restrição de posição de `out`/`in`.
        VarianceChecks.checkClass(sa, cls);
    }

    /**
     * #470: o extends de um record (inclusive o SINTÉTICO do parser —
     * `class Dog(String name) extends Animal` vira RecordDeclarationNode com
     * superClass) compilava limpo e morria no load com
     * IncompatibleClassChangeError (o record sai ACC_FINAL no bytecode).
     * A JVM nunca deixa herdar de record nem de classe final; o compilador
     * deve dizer antes (R6).
     */
    static void checkRecordDeclaration(SemanticAnalyzer sa, RecordDeclarationNode rec) {
        String superName = eraseGenerics(rec.superClass());
        // #689: o `return` antecipado quando o record NÃO tem super explícito
        // (caso comum) pulava TODOS os cheques abaixo — inclusive o de
        // variância X5.3. Agora o super só gateia os cheques de herança.
        boolean hasSuper = superName != null && !"Record".equals(superName) && !"Object".equals(superName);
        if (hasSuper) {
            if (isRecordNamed(sa, simpleName(superName))) {
                report(sa, rec.position(),
                        "cannot extend record '" + superName + "'"
                                + " — records are implicitly final; compose it (hold it in a field) or use a plain class",
                        "SEM070");
            } else if (sa.finalClasses().contains(simpleName(superName))) {
                report(sa, rec.position(),
                        "cannot inherit from final class '" + superName + "'"
                                + " (declared 'final' — remove 'final' or the inheritance)",
                        "SEM070");
            }
        }
        // X5.1 (D-X5-SURFACE): record é subtipo de interface `sealed`?
        SealedTypeChecks.checkSubtype(sa, rec.position(), rec.name(),
                rec.superClass(), rec.interfaces());
        // X5.3 (D-TYPE-VARIANCE): restrição de posição de `out`/`in`.
        VarianceChecks.checkRecord(sa, rec);
    }

    /** #470: nome é record? O ClassSymbol de um record tem super "Record"
     *  (alias gravado pelo SymbolTableBuilder) ou java/lang/Record. */
    private static boolean isRecordNamed(SemanticAnalyzer sa, String name) {
        SymbolTable.ClassSymbol cs = sa.getClass(name);
        if (cs == null) return false;
        String sup = cs.superClass();
        return sup != null && (sup.equals("Record") || sup.endsWith(".Record") || sup.endsWith("/Record"));
    }

    /** Chamado nas 2 faces de construção (`new I()` em SemExpressionTyper,
     *  `I()` implícita em BuiltinCallTyper) — #340. */
    static void checkInstantiable(SemanticAnalyzer sa, String typeName) {
        if (typeName != null && sa.interfaceNames().contains(typeName)) {
            report(sa, null,
                    "cannot instantiate interface '" + typeName + "'"
                            + " — declare a class that implements it",
                    "SEM071");
        }
    }

    private static void report(SemanticAnalyzer sa, SourcePosition pos, String msg, String code) {
        if (sa.diagnostics() == null) return;
        sa.diagnostics().error(pos != null ? pos.file() : "",
                pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0, msg, code);
    }

    static String eraseGenerics(String name) {
        if (name == null) return null;
        int lt = name.indexOf('<');
        return lt > 0 ? name.substring(0, lt).trim() : name;
    }

    static String simpleName(String qualified) {
        String q = qualified.replace('/', '.');
        int dot = q.lastIndexOf('.');
        return dot >= 0 ? q.substring(dot + 1) : q;
    }
}
