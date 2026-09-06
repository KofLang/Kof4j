package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JsMethodParser — hub do lowering de UM método (REFACTOR-500 FASE 4).
 * Liga os parsers por domínio (controle, switch, expressão, chamadas,
 * coleções, runtime) e expõe a entrada parseMethodBody/lowerFunction.
 */
final class JsMethodParser {

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
            name = this.lc.jsFunctionName(name, method.parameterTypes().size());
        }
        return new JsIr.JsFunction(name, parameterNames(ctx), parseMethodBody(ctx), isStatic, false, isTopLevel,
                ctx.isAsync, firstKofLine(method));
    }

    List<String> parameterNames(MethodCtx ctx) {
        if ("main".equals(ctx.methodName) && ctx.paramCount == 1) {
            // The injected String[] parameter is not a source parameter.
            return List.of();
        }
        List<String> names = new ArrayList<>();
        int start = ctx.instanceMethod ? 1 : 0;
        for (int i = start; i < ctx.localNames.size() && names.size() < ctx.paramCount; i++) {
            if (ctx.captureSlots.contains(i)) continue;
            String name = ctx.localNames.get(i);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }



}
