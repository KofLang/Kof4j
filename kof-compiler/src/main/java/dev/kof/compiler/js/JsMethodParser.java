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
    final JsCollectionOps coll;
    final JsRuntimeOps rt;

    JsMethodParser(JsLoweringContext lc) {
        this.lc = lc;
        this.flow = new JsControlFlowParser(this);
        this.sw = new JsSwitchParser(this);
        this.expr = new JsExpressionParser(this);
        this.calls = new JsCallEmitter(this);
        this.coll = new JsCollectionOps(this);
        this.rt = new JsRuntimeOps(this);
    }

    List<JsIr.JsStatement> parseMethodBody(MethodCtx ctx) {
        this.lc.currentCtxOpsDump = ctx.ops;
        for (int slot : parameterSlots(ctx)) {
            ctx.declared.add(slot);
        }
        int[] pos = {0};
        List<JsIr.JsStatement> body = flow.parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        if (pos[0] < ctx.ops.size()) {
            throw new IllegalStateException("KofJS: unconsumed ops in method "
                    + ctx.kofClassName + "." + (ctx.methodName == null ? "?" : ctx.methodName)
                    + " at " + ctx.ops.get(pos[0]));
        }
        List<JsIr.JsStatement> predecl = new ArrayList<>();
        int paramStart = ctx.instanceMethod ? 1 : 0;
        int paramEnd = paramStart + ctx.paramCount
                + (ctx.captureSlots.isEmpty() ? 0 : ctx.captureSlots.size());
        for (int slot : ctx.localNames.keySet()) {
            if (slot < paramEnd) continue;
            String name = ctx.localNames.get(slot);
            if (name != null && ctx.declared.add(slot)) {
                predecl.add(new JsIr.JsVarDecl(name, null, false));
            }
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
        if (isTopLevel) {
            // SG-011B: resolve pela ASSINATURA (twice(String) vs twice(Int) têm
            // a mesma aridade — por aridade colidiriam); não-sobrecarregadas
            // caem no caminho antigo (nome cru / $d de defaults).
            name = this.lc.jsFunctionName(name, method.parameterTypes(), method.parameterTypes().size());
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
