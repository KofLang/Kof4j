package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * #1124 fatia 2 — inferência lower-time de chamada ESTÁTICA sobre classe
 * EXTERNA importada (`Optional.of("hi")`), extraída do MethodCallTyper
 * (gate ≤600; a família é autocontida: qualificação pelo import, assinatura
 * apagada do classpath e substituição do retorno genérico).
 *
 * <p>O cache semântico é limpo a cada passe e o {@code getResolvedMethod}
 * não sobrevive para estáticos — sem ESTA face a inferência lower-time
 * devolve Unknown e o local do {@code var} perde o tipo (o javap do ciclo
 * mediu {@code invokevirtual "".get} — owner vazio → ClassFormatError no
 * load). Nomes builtin ficam DE FORA (String/Int/Long/Float/Double/Bool/
 * Char já têm ramos próprios no MethodCallTyper — faces verdes não mudam
 * um byte). Devolve null quando o caso não é desta família (o chamador
 * continua na sua cadeia de fallbacks).
 */
final class ExternalStaticCallTyper {

    private ExternalStaticCallTyper() {}

    static boolean isBuiltinTypeName(String name) {
        return switch (name) {
            case "String", "Int", "Integer", "Long", "Float",
                    "Double", "Bool", "Boolean", "Char" -> true;
            default -> false;
        };
    }

    /** Retorno da chamada estática externa, ou null fora da família. */
    static Type inferType(CompilerDriver driver, MethodCallExpr mc,
                          List<IRLocalVariable> locals) {
        if (!(mc.receiver() instanceof IdentifierExpr xrid)
                || driver.findLocalVar(xrid.name(), locals) != null
                || isBuiltinTypeName(xrid.name())
                || driver.semanticAnalyzer == null
                || driver.semanticAnalyzer.getClass(xrid.name()) != null) {
            return null;
        }
        Type xq = MemberResolver.qualifyViaImports(driver.semanticAnalyzer.unit(),
                xrid.name(), driver.externalClasspath);
        if (!(xq instanceof Type.ClassType xqt)
                || !driver.externalClasspath.knows(xqt.internalName())) {
            return null;
        }
        List<Type> xargs = new ArrayList<>();
        for (ExpressionNode xarg : mc.arguments()) {
            xargs.add(ExpressionTyper.inferExprType(driver, xarg, locals));
        }
        ExternalClasspath.MethodSignature xsig = driver.externalClasspath.resolveMethodWithArgs(
                xqt.internalName(), mc.methodName(), mc.arguments().size(), xargs);
        if (xsig == null) {
            return null;
        }
        Type xret = ExternalClasspath.typeFromDescriptor(xsig.returnDescriptor());
        Type xgen = JdkReflectionResolver.genericReturnFromArgs(
                xqt.internalName(), mc.methodName(),
                mc.arguments().size(), xargs, xqt);
        if (xgen != null) {
            xret = xgen;
        }
        return xret;
    }
}
