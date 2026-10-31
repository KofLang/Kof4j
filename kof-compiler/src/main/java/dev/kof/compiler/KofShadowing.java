package dev.kof.compiler;

/**
 * Lei do sombreamento no nível de FUNÇÃO (#931/#932, mesmo princípio do §243
 * que vale para CLASSES): uma função top-level ou `extern` DECLARADA pelo
 * programa vence qualquer builtin interceptado pelo nome — nunca o contrário,
 * e nunca em silêncio. Antes da lei, um `writeFile(String, String)` do usuário
 * ou um `extern poll(Long, Long, Int)` eram roubados pelo branch de nome fixo
 * do builtin (`kof_write_file` / o typer aridade-cego do `poll`) em TODOS os
 * alvos — JVM, Native e JS compilação limpa, execução errada.
 *
 * <p>Um só oráculo: o helper único alimenta typer semântico, typer de emit e
 * lowerer; o caminho de chamada a função declarada (bare call / extern) já
 * existe a jusante e faz o dispatch correto quando os branches de builtin
 * saem da frente.
 */
final class KofShadowing {

    private KofShadowing() {}

    /** O programa declara função top-level OU `extern` com este nome. */
    static boolean userDeclares(SemanticAnalyzer sa, String name) {
        return sa != null && sa.unit() != null && declares(sa.unit(), name);
    }

    /** Mesma pergunta no lado do emit/lowering (driver). */
    static boolean userDeclares(CompilerDriver driver, String name) {
        return driver != null && driver.currentUnit != null && declares(driver.currentUnit, name);
    }

    private static boolean declares(CompilationUnitNode unit, String name) {
        for (AstNode d : unit.declarations()) {
            if (d instanceof FunctionDeclarationNode fn && fn.name().equals(name)) return true;
            if (d instanceof ExternalFunctionNode ext && ext.name().equals(name)) return true;
        }
        return false;
    }
}
