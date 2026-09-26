package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * TopLevelCallTyper — resolução de chamada SEM receiver a função top-level e a
 * `extern`/FFI: candidatos homônimos, overload (TopLevelOverload), defaults,
 * SEM013/SEM014/SEM015/SEM048/SEM057 e o registro do tipo de retorno para a
 * inferência do `var` local. Extraído de BuiltinCallTyper no gate ≤500
 * (REFACTOR-500: a classe cruzou 600 linhas): o bloco era a cauda do
 * `inferTail` e continua delegado no MESMO ponto da cadeia — ordem, guards e
 * diagnósticos preservados (freeze regra 3, refactor sem mudança de
 * comportamento). Uma `extern` é função DECLARADA pelo programa com binding
 * externo, mais próxima de uma função top-level do que de um builtin.
 */
final class TopLevelCallTyper {

    private TopLevelCallTyper() {}

    /** Nomes que pertencem a builtins/namespaces e nunca são função top-level. */
    private static boolean isTopLevelCandidate(SemanticAnalyzer sa, MethodCallExpr mc) {
        return mc.receiver() == null && sa.unit() != null
                && !"println".equals(mc.methodName()) && !"print".equals(mc.methodName())
                && !"listOf".equals(mc.methodName()) && !"mapOf".equals(mc.methodName()) && !"setOf".equals(mc.methodName())
                && !"now".equals(mc.methodName()) && !"readLine".equals(mc.methodName())
                && !"readFile".equals(mc.methodName()) && !"writeFile".equals(mc.methodName())
                && !"super".equals(mc.methodName())
                && !KofIo.isConstructor(mc.methodName())
                && !KofUi.isConstructor(mc.methodName())
                && !KofWeb.isContextFunction(mc.methodName())
                && !KofScheduler.isSchedulerMethod(mc.methodName())
                && !"transaction".equals(mc.methodName())
                && !"uiNodesLive".equals(mc.methodName())
                && !"emit".equals(mc.methodName())
                && !"storesLive".equals(mc.methodName())
                && !"subscriptionsLive".equals(mc.methodName());
    }

    /**
     * Resolve a chamada. Devolve o tipo (não-void) quando a função top-level /
     * `extern` o determina; null quando a chamada segue a cadeia (void, guard
     * não aplicável ou função inexistente já diagnosticada).
     */
    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        if (isTopLevelCandidate(sa, mc)) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            // SG-011B: junta TODOS os candidatos homônimos ELIGÍVEIS (mesmo
            // predicado de antes: sem type params próprios, e com args cobrindo
            // os parâmetros quando há defaults) e resolve por assinatura. Um
            // único candidato → caminho idêntico ao antigo (zero regressão);
            // ≥2 → TopLevelOverload.pick (oracle JVM); ambíguo → SEM057.
            boolean found = false;
            List<TopLevelOverload.Candidate> cands = new ArrayList<>();
            for (AstNode d : sa.unit().declarations()) {
                if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                    found = true;
                    boolean hasDefaults = fn.parameters().stream()
                            .anyMatch(p -> p.defaultExpression() != null);
                    if (fn.typeParameters().isEmpty() && (!hasDefaults
                            || mc.arguments().size() >= TopLevelOverload.requiredArityOf(fn))) {
                        List<Type> paramTypes = new ArrayList<>();
                        for (FormalParameterNode p : fn.parameters()) paramTypes.add(MemberResolver.resolveType(sa, p.type(), scope));
                        cands.add(new TopLevelOverload.Candidate(fn, paramTypes,
                                TopLevelOverload.requiredArityOf(fn)));
                    }
                } else if (d instanceof ExternalFunctionNode ext && ext.name().equals(mc.methodName())) {
                    // FFI (TIER 2.1): chamada a `extern` declarado resolve pelo
                    // contrato (tipo de retorno), nunca SEM015 — o binding real é
                    // lowering por target (JVM/Native); JS emite FFI002.
                    found = true;
                    // #431 (Native-safe): aridade/tipos contra o contrato declarado.
                    // Sem isto, um `abs(1,2,3)` (que na JVM só falha em runtime no
                    // spread do kof_ffi) baixaria no Native com N slots na pilha de
                    // operandos e 1 pop no shim — corrupção silenciosa de stack.
                    // Mesma política do #266(c)/SEM048: o compile-time pega o que o
                    // runtime pagaria caro (posição do CALL SITE, §350).
                    List<Type> extFormals = new ArrayList<>();
                    for (FormalParameterNode p : ext.parameters()) {
                        extFormals.add(MemberResolver.resolveType(sa, p.type(), scope));
                    }
                    if (argTypes.size() != extFormals.size()) {
                        if (sa.diagnostics() != null) {
                            sa.diagnostics().error(mc.position() != null ? mc.position().file() : "",
                                    mc.position() != null ? mc.position().line() : 0,
                                    mc.position() != null ? mc.position().column() : 0, 0,
                                    "Wrong number of arguments for '" + ext.name() + "': expected "
                                            + extFormals.size() + " but got " + argTypes.size(), "SEM013");
                        }
                    } else {
                        for (int ai = 0; ai < argTypes.size(); ai++) {
                            Type at = argTypes.get(ai), ft = extFormals.get(ai);
                            if (!Type.isUnknown(at) && !Type.isUnknown(ft)
                                    && !TypeChecker.isAssignable(at, ft)) {
                                if (sa.diagnostics() != null) {
                                    sa.diagnostics().error(mc.position() != null ? mc.position().file() : "",
                                            mc.position() != null ? mc.position().line() : 0,
                                            mc.position() != null ? mc.position().column() : 0, 0,
                                            "Argument " + (ai + 1) + " of '" + ext.name() + "': expected '"
                                                    + Type.display(ft) + "' but got '" + Type.display(at) + "'",
                                            "SEM014");
                                }
                                break;
                            }
                        }
                    }
                    Type extRet = MemberResolver.resolveType(sa, ext.returnType(), scope);
                    if (!Type.isVoid(extRet)) {
                        sa.putExpressionType(mc, extRet);
                        return extRet;
                    }
                }
            }
            if (!cands.isEmpty()) {
                TopLevelOverload.Status[] st = new TopLevelOverload.Status[1];
                int sel = TopLevelOverload.pick(cands, argTypes, st);
                if (sel < 0 && st[0] == TopLevelOverload.Status.AMBIGUOUS) {
                    if (sa.diagnostics() != null) {
                        sa.diagnostics().error(mc.position() != null ? mc.position().file() : "",
                                mc.position() != null ? mc.position().line() : 0,
                                mc.position() != null ? mc.position().column() : 0, 0,
                                "call to '" + mc.methodName() + "' is ambiguous between "
                                        + cands.size() + " overloads — add a cast to pick one",
                                "SEM057");
                    }
                } else {
                    if (sel < 0) sel = 0; // NO_MATCH → reporta SEM013/SEM014 no candidato 0, como antes
                    TopLevelOverload.Candidate chosen = cands.get(sel);
                    // §231: chamada curta num candidato com default resolve pelo
                    // WRAPPER de prefixo (mesmo `subList(0, nArgs)` que o
                    // ExpressionMethodCallLowerer emite) — validar contra os
                    // parâmetros recebidos, não contra a assinatura total, senão
                    // a chamada boa cai em SEM013 "expected N but got nArgs".
                    List<Type> chosenFormals = chosen.paramTypes();
                    if (argTypes.size() < chosen.totalArity()
                            && argTypes.size() >= chosen.requiredArity()) {
                        chosenFormals = chosenFormals.subList(0, argTypes.size());
                    }
                    TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes, chosenFormals, mc.arguments());
                    // #266 (c) — DECISIONS §7: `null` literal em parâmetro
                    // primitivo NÃO-nullable é SEM048 em compile-time, nunca
                    // VerifyError silencioso no load (a chamada top-level não
                    // passa por resolvedMethods, por isso o check direto aqui).
                    SemanticAnalyzer.checkNullArgs(sa.diagnostics(), mc.arguments(),
                            mc.position(), chosenFormals, mc.methodName());
                    // registra o tipo de retorno da função top-level para o var
                    // local inferir (evita Unknown que quebra a resolução de
                    // métodos do receiver)
                    Type fnRet = MemberResolver.resolveType(sa, chosen.fn().returnType(), scope);
                    if (!Type.isVoid(fnRet)) {
                        sa.putExpressionType(mc, fnRet);
                        return fnRet;
                    }
                }
            }
            if (!found && !sa.allClasses().containsKey(mc.methodName())) {
                // §393 (#568): nome que NAO e funcao top-level nem classe do
                // programa pode ser construtor IMPLICITO de classe externa
                // (--classpath/--deps, §134) — resolve pela tabela de
                // construtores publicos do .class ANTES do SEM015, que e
                // mentira para classe que existe la fora. Classe existente sem
                // ctor publico compativel = SEM023 honesto (diagnosed);
                // nem uma coisa nem outra = SEM015 de sempre (R6).
                ExternalCtorTyper.Outcome ext = ExternalCtorTyper.infer(sa, mc, argTypes);
                if (ext.type() != null) return ext.type();
                if (!ext.diagnosed() && sa.diagnostics() != null) {
                    sa.diagnostics().error(mc,
                            "Undefined function: '" + mc.methodName() + "'", "SEM015");
                }
            }
        }
        return null;
    }
}
