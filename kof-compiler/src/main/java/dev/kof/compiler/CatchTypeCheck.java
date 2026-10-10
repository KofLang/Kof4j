package dev.kof.compiler;

/**
 * #332/#328 — validação semântica do tipo de `catch`.
 *
 * Kof lança Strings (exceções são Strings), mas o `catch` também aceita
 * throwables do JDK para interop (`catch (RuntimeException e)` — #163/#241).
 * Antes deste check NADA era validado: `catch (Int e)` compilava e a
 * exception table saía `java/lang/Int` (classe inexistente) →
 * NoClassDefFoundError no load (#328); `catch (Foo e)` com `Foo` classe do
 * usuário NÃO-throwable produzia `java/lang/Foo` — e MESMO com o nome
 * correto a JVM rejeita no verifier ("Catch type is not a subclass of
 * Throwable", medido) → runtime, nunca diagnóstico. R6: o compilador deve
 * dizer na compilação o que a JVM diria (pior) no load.
 *
 * Regra (compartilhada por JVM/Native/JS/interpreter — um só lugar):
 *   - `String` (exceto quando o usuário declara `class String` — §243) → ok
 *   - nome QUALIFICADO (`java.lang.RuntimeException`) ou resolvido por
 *     import → ok (a JVM verifier é a autoridade final p/ hierarquia externa)
 *   - nome simples em JAVA_LANG_THROWABLES → ok (interop, #163/#241)
 *   - classe DECLARADA pelo usuário com ancestral Throwable (walk em
 *     `superClass`) → ok — o backend EMITE o nome interno correto (#332)
 *   - primitivo Kof → SEM067
 *   - classe de usuário NÃO-throwable → SEM068
 *   - nome simples desconhecido → SEM011 (invariante: nome desconhecido
 *     nunca compila)
 */
public final class CatchTypeCheck {

    private CatchTypeCheck() {}

    private static final java.util.Set<String> KOF_PRIMITIVES = java.util.Set.of(
            "Int", "Long", "Float", "Double", "Bool", "Char", "Byte", "Short",
            "int", "long", "float", "double", "bool", "boolean", "char", "byte", "short", "void");

    /**
     * @return true se o tipo é aceito (inclui externo — autoridade da JVM);
     *         false se um diagnóstico foi emitido (e o compile falha).
     */
    static boolean check(SemanticAnalyzer sa, CatchClause cc) {
        String name = cc.exceptionType();
        if (name == null || name.isEmpty() || sa == null || sa.diagnostics() == null) return true;
        if ("String".equals(name) && sa.getClass("String") == null) return true;
        if (name.indexOf('.') >= 0 || name.indexOf('/') >= 0) return true;
        if (CompilerTypes.JAVA_LANG_THROWABLES.contains(name)) return true;
        // D-INTEROP-ERR-TYPE: builtin do idioma, throwable por construcao — mas
        // a face so existe onde a ffi estrangeira baixa (JVM). Fora daqui, a
        // recusa e nomeada (INTEROP009), nunca stub silencioso (R6).
        if ("InteropError".equals(name)) {
            if (sa.interpreting() || sa.target() != Target.JVM) {
                sa.diagnostics().error(cc,
                        "catch type 'InteropError' needs the JVM foreign-call face — not"
                                + " available on target '" + sa.target() + "'"
                                + (sa.interpreting() ? " (script interpretation)" : "")
                                + " (" + KofInteropError.GAP_CODE + "); the failure there is"
                                + " named in the message of `catch (String e)`",
                        KofInteropError.GAP_CODE);
                return false;
            }
            return true;
        }
        if (KOF_PRIMITIVES.contains(name)) {
            sa.diagnostics().error(cc,
                    "catch type '" + name + "' is a primitive — primitives are not throwable"
                            + " (Kof exceptions are Strings: use `catch (String e)`)",
                    "SEM067");
            return false;
        }
        // externo resolvido por import (java.io.IOException etc.) → JVM decide.
        if (MemberResolver.qualifyViaImports(sa.unit(), name) instanceof Type.ClassType qct
                && !qct.packageName().isEmpty()) {
            return true;
        }
        SymbolTable.ClassSymbol cs = sa.getClass(name);
        if (cs == null) {
            sa.diagnostics().error(cc,
                    "Undefined variable or type: '" + name + "'",
                    "SEM011");
            return false;
        }
        String current = cs.name();
        int depth = 0;
        while (current != null && depth++ < 32) {
            if (CompilerTypes.JAVA_LANG_THROWABLES.contains(current)) return true;
            SymbolTable.ClassSymbol next = sa.getClass(current);
            current = next != null ? simpleName(next.superClass()) : null;
        }
        sa.diagnostics().error(cc,
                "catch type '" + name + "' is not a Throwable subclass — Kof exceptions"
                        + " are Strings (use `catch (String e)`) or a JDK/user throwable",
                "SEM068");
        return false;
    }

    private static String simpleName(String qualified) {
        if (qualified == null) return null;
        String q = qualified.replace('/', '.');
        int dot = q.lastIndexOf('.');
        return dot >= 0 ? q.substring(dot + 1) : q;
    }
}
