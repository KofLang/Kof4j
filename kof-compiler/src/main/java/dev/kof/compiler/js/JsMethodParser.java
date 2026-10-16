package dev.kof.compiler.js;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofDebugInfo;
import dev.kof.compiler.SourcePosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JsMethodParser — hub do lowering de UM método (REFACTOR-500 FASE 4).
 * Liga os parsers por domínio (controle, switch, expressão, chamadas,
 * coleções, runtime) e expõe a entrada parseMethodBody/lowerFunction.
 */
public final class JsMethodParser {

    final JsLoweringContext lc;
    final JsControlFlowParser flow;
    final JsSwitchParser sw;
    final JsExpressionParser expr;
    final JsCallEmitter calls;
    final JsOperatorEmitter ops;
    final JsCollectionOps coll;
    final JsRuntimeOps rt;
    final JsJsonOps jsonOps;
    final JsRecordSynthetics records;

    JsMethodParser(JsLoweringContext lc) {
        this.lc = lc;
        this.flow = new JsControlFlowParser(this);
        this.sw = new JsSwitchParser(this);
        this.expr = new JsExpressionParser(this);
        this.calls = new JsCallEmitter(this);
        this.ops = new JsOperatorEmitter(this);
        this.coll = new JsCollectionOps(this);
        this.rt = new JsRuntimeOps(this);
        this.jsonOps = new JsJsonOps(this);
        this.records = new JsRecordSynthetics(this);
    }

    List<JsIr.JsStatement> parseMethodBody(MethodCtx ctx) {
        this.lc.currentCtxOpsDump = ctx.ops;
        for (int slot : parameterSlots(ctx)) {
            ctx.declared.add(slot);
        }
        // §708: pré-declara TODOS os locais do método ANTES de parsear o corpo.
        // O `storeLocalStatement` emite `let x = v` no PRIMEIRO store de cada
        // slot; quando esse primeiro store está DENTRO de um ramo (o store do
        // topo foi removido pelo otimizador — dead-store/propagação), o `let`
        // caía no escopo do ramo e o nome ficava invisível fora dele
        // (`ReferenceError`). Declarando tudo no topo, todo store vira
        // ATRIBUIÇÃO e a declaração fica no escopo do método — robusto a IR
        // otimizado (a direção que o bloqueio da §3 O2 do assembly-opt nomeia).
        List<JsIr.JsStatement> predecl = new ArrayList<>();
        int paramStart = ctx.instanceMethod ? 1 : 0;
        int paramEnd = paramStart + ctx.paramCount
                + (ctx.captureSlots.isEmpty() ? 0 : ctx.captureSlots.size());
        // DD-01 (bug 45): #retVal primeiro — store acontece DENTRO do try
        // (é compiler-temp, não vira var-decl no ponto do store) e o epílogo
        // return-finally (fora do try) precisa do binding já declarado.
        for (int slot : ctx.localNames.keySet()) {
            if ("#retVal".equals(ctx.rawLocalNames.get(slot)) && ctx.declared.add(slot)) {
                predecl.add(new JsIr.JsVarDecl(ctx.localNames.get(slot), null, false));
            }
        }
        for (int slot : ctx.localNames.keySet()) {
            if (slot < paramEnd) continue;
            String name = ctx.localNames.get(slot);
            if (name != null && !name.startsWith("#") && ctx.declared.add(slot)) {
                predecl.add(new JsIr.JsVarDecl(name, null, false));
            }
        }
        int[] pos = {0};
        List<JsIr.JsStatement> body = flow.parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        if (pos[0] < ctx.ops.size()) {
            throw new IllegalStateException("KofJS: unconsumed ops in method "
                    + ctx.kofClassName + "." + (ctx.methodName == null ? "?" : ctx.methodName)
                    + " at " + ctx.ops.get(pos[0]));
        }
        if (!predecl.isEmpty() || !ctx.tempDecls.isEmpty()) {
            List<JsIr.JsStatement> withTemps = new ArrayList<>();
            for (JsIr.JsStatement d : predecl) {
                withTemps.add(d);
            }
            for (String decl : ctx.tempDecls) {
                withTemps.add(new JsIr.JsVarDecl(decl, null, false));
            }
            withTemps.addAll(body);
            return withTemps;
        }
        return body;
    }

    /**
     * Linha Kof da primeira instrução do método (para o source map V3) — vem do
     * {@code KofDebugInfo} que o driver já popula (mesma fonte das line tables
     * do JVM). Sintéticos (toString/toJSON/decode) não têm fonte → null.
     */
    static Integer firstKofLine(IRMethod method) {
        if (method.debugInfo() == null || method.debugInfo().positions().isEmpty()) return null;
        Integer min = null;
        for (SourcePosition p : method.debugInfo().positions().values()) {
            if (p != null && p.line() > 0) {
                min = (min == null) ? p.line() : Math.min(min, p.line());
            }
        }
        return min;
    }


    JsIr.JsFunction lowerFunction(IRMethod method, IRClass clazz, boolean isStatic) {
        return lowerFunction(method, clazz, isStatic, false);
    }

    JsIr.JsFunction lowerFunction(IRMethod method, IRClass clazz, boolean isStatic, boolean isTopLevel) {
        MethodCtx ctx = new MethodCtx(lc, method, clazz);
        String name = method.name();
        if ("<init>".equals(name)) name = "constructor";
        if (isTopLevel || !isTopLevel && clazz != null && !"<init>".equals(method.name())) {
            // SG-011B/§131: sobrecarregado resolve pela ASSINATURA (top-level
            // E método de classe — o JS não tem overload; classe não-sobre-
            // carregada mantém o nome cru, zero churn no structural dispatch).
            name = this.lc.jsFunctionName(name, method.parameterTypes(), method.parameterTypes().size());
        }
        // §722: um método cujo nome é uma PALAVRA RESERVADA do JS (`of`,
        // `class`, `delete`, ...) é DEFINIDO com o nome cru mas CHAMADO com o
        // `sanitizeName` do call site (→ `_of`), então o método não existe
        // (`TypeError: f._of is not a function`). A definição passa pelo MESMO
        // sanitize do call site. `<init>`/`constructor` ficam intactos.
        if (!"<init>".equals(method.name()) && !"constructor".equals(name)) {
            name = JsTypeMapper.sanitizeName(name);
        }
        return new JsIr.JsFunction(name, parameterNames(ctx), parseMethodBody(ctx), isStatic, false, isTopLevel,
                ctx.isAsync, firstKofLine(method));
    }

    List<String> parameterNames(MethodCtx ctx) {
        List<String> names = new ArrayList<>();
        for (int slot : parameterSlots(ctx)) {
            names.add(ctx.localNames.get(slot));
        }
        return names;
    }

    /**
     * Slots dos parâmetros que viram a assinatura da função JS (exclui
     * capturas de lambda, que ficam nos slots iniciais mas são locais
     * comuns no corpo — e exclui o `String[] args` injetado de `main`,
     * que não é parâmetro de origem). Fonte única para `parameterNames`
     * (nomes da assinatura) e `parseMethodBody` (semeadura de `declared`).
     */
    List<Integer> parameterSlots(MethodCtx ctx) {
        if ("main".equals(ctx.methodName) && ctx.paramCount == 1) {
            // The injected String[] parameter is not a source parameter.
            return List.of();
        }
        // known-bugs #64 / GitHub #47: `Long` e `Double` ocupam DOIS slots, então
        // os índices são ESPARSOS — em `f(Long a, Int b)` o mapa é {0:a, 2:b}.
        // Percorrer `0..localNames.size()` parava antes do slot 2 e descartava
        // `b` da assinatura (lido como `undefined`, sem diagnóstico). Percorre
        // as chaves REAIS em ordem crescente.
        List<Integer> ordered = new ArrayList<>(ctx.localNames.keySet());
        java.util.Collections.sort(ordered);
        List<Integer> slots = new ArrayList<>();
        int start = ctx.instanceMethod ? 1 : 0;
        for (int slot : ordered) {
            if (slots.size() == ctx.paramCount) break;
            if (slot < start) continue;
            if (ctx.captureSlots.contains(slot)) continue;
            if (ctx.localNames.get(slot) != null) {
                slots.add(slot);
            }
        }
        return slots;
    }



}
