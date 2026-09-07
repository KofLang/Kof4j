package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de métodos de coleção (List/Channel/Map/Set) no emitExpression.
 * Retorna -1 se nenhum método de coleção foi reconhecido (cai no genérico).
 */
final class CollectionCallLowerer {

    private CollectionCallLowerer() {}

    static int lower(CompilerDriver driver, Type recvType, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    if (BuiltinTypes.isList(recvType)
            && ("map".equals(mc.methodName()) || "filter".equals(mc.methodName())
                || "reduce".equals(mc.methodName()))) {
        String hoFn = "kof_list_" + mc.methodName();
        // receiver já empilhado acima (3396) — não duplicar
        Type lambdaT = Type.UnknownType.UNKNOWN;
        // reduce: init antes; lambda por último
        for (ExpressionNode arg : mc.arguments()) {
            if (!(arg instanceof LambdaExpr)) {
                Type argT = ExpressionTyper.inferExprType(driver, arg, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                if (TypeMetrics.isPrimitiveType(argT) && driver.target == Target.JVM) {
                    Type boxed = TypeMetrics.boxedTypeFor(argT);
                    ops.add(new KofCall(boxed, "kof_box", List.of(argT), boxed, KofCallKind.FUNCTION));
                }
            }
        }
        for (ExpressionNode arg : mc.arguments()) {
            if (arg instanceof LambdaExpr lam) {
                lambdaT = ExpressionTyper.inferExprType(driver, lam, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, lam, ops, owner, localIdx, locals);
            }
        }
        List<Type> callParams = new ArrayList<>();
        callParams.add(new Type.ClassType("java.util", "ArrayList", List.of()));
        if ("reduce".equals(mc.methodName())) callParams.add(new Type.ClassType("java.lang", "Object", List.of()));
        callParams.add(new Type.ClassType("java.lang", "Object", List.of()));
        Type ret;
        if ("filter".equals(mc.methodName())) ret = recvType;
        else if ("map".equals(mc.methodName())) {
            Type elem = (lambdaT instanceof Type.FunctionType ft && !(ft.returnType() instanceof Type.UnknownType)) ? ft.returnType() : Type.UnknownType.UNKNOWN;
            ret = new Type.ClassType("kof", "List", List.of(elem));
        } else {
            ret = (lambdaT instanceof Type.FunctionType ft) ? ft.returnType() : Type.UnknownType.UNKNOWN;
        }
        ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()), hoFn, callParams, ret,
                KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofProcess.isHandle(recvType)) {
        // F10: h.write/readLine/exitCode/kill/alive — o handle
        // empilhado entra como 1º parâmetro do call estático
        KofProcess.ProcessCall hm = KofProcess.handleMethod(mc.methodName(),
                mc.arguments().stream().map(a -> ExpressionTyper.inferExprType(driver, a, locals)).toList());
        if (hm != null) {
            List<Type> params = new ArrayList<>();
            params.add(KofProcess.HANDLE);
            for (int pi = 1; pi < hm.parameterTypes().size(); pi++) {
                params.add(hm.parameterTypes().get(pi));
            }
            for (ExpressionNode arg : mc.arguments()) {
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                    hm.function(), params, hm.returnType(), KofCallKind.FUNCTION));
            return localIdx;
        }
    }
    if (BuiltinTypes.isChannel(recvType)) {
        // Canais tipados: c.send(v) enfileira; c.receive() retira.
        // O receiver (Channel) está empilhado; o elemento vai
        // após — o backend faz a ordem (send: chan,elem; receive: chan).
        Type elemT = BuiltinTypes.channelElement(recvType);
        if ("send".equals(mc.methodName()) && mc.arguments().size() == 1) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
            ops.add(new KofCall(recvType, "kof_channel_send", List.of(elemT),
                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
            return localIdx;
        }
        if ("receive".equals(mc.methodName()) && mc.arguments().isEmpty()) {
            ops.add(new KofCall(recvType, "kof_channel_receive", List.of(),
                    elemT, KofCallKind.INSTANCE));
            return localIdx;
        }
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + mc.methodName() + "' on type 'Channel' (valid: send, receive)",
                    "SEM025");
            return localIdx;
        }
    }
    if (BuiltinTypes.isList(recvType)) {
        String listFn = switch (mc.methodName()) {
            case "add", "push", "append" -> "kof_list_add";
            case "get" -> "kof_list_get";
            case "set" -> "kof_list_set";
            case "size", "length", "count" -> "kof_list_size";
            case "contains" -> "kof_list_contains";
            case "isEmpty" -> "kof_list_is_empty";
            case "remove" -> "kof_list_remove";
            case "clear" -> "kof_list_clear";
            default -> null;
        };
        // R6: método desconhecido em List não pode ser silencioso (bug Set.first)
        if (listFn == null && driver.currentDiagnostics != null
                && !"toArray".equals(mc.methodName()) && !"sublist".equals(mc.methodName())
                && !"subSet".equals(mc.methodName()) && !"map".equals(mc.methodName())
                && !"filter".equals(mc.methodName()) && !"reduce".equals(mc.methodName())) {
            String m = mc.methodName();
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + m + "' on type 'List' (valid: add/get/set/remove/contains/size/isEmpty/clear/map/filter/reduce)",
                    "SEM025");
            return localIdx;
        }
        if (listFn != null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type elemType = driver.listElementType(recvType);
            // listOf() with no type argument produces
            // List<Unknown>; the first add() pins the element
            // type on the local so later get() calls are
            // typed (records, classes) instead of Object.
            if ("kof_list_add".equals(listFn)
                    && Type.UnknownType.UNKNOWN.equals(elemType)
                    && !argTypes.isEmpty()
                    && !(argTypes.get(0) instanceof Type.UnknownType)
                    && mc.receiver() instanceof IdentifierExpr rid) {
                for (int li = 0; li < locals.size(); li++) {
                    IRLocalVariable lv = locals.get(li);
                    if (lv.name().equals(rid.name())) {
                        locals.set(li, new IRLocalVariable(lv.index(), lv.name(),
                                new Type.ClassType("kof", "List", List.of(argTypes.get(0)))));
                        break;
                    }
                }
            }
            for (ExpressionNode arg : mc.arguments()) localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            Type retType = switch (listFn) {
                case "kof_list_add", "kof_list_set", "kof_list_clear" -> Type.PrimitiveType.VOID;
                case "kof_list_contains", "kof_list_is_empty" -> Type.PrimitiveType.BOOL;
                case "kof_list_remove" -> elemType;
                default -> elemType;
            };
            if ("kof_list_contains".equals(listFn)) {

                int tag = BuiltinTypes.isString(elemType) ? 1 : 0;
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                argTypes = new ArrayList<>(argTypes);
                argTypes.add(Type.PrimitiveType.INT);
            }
            ops.add(new KofCall(recvType, listFn, argTypes, retType, KofCallKind.INSTANCE));
            return localIdx;
        }
    }
    if (BuiltinTypes.isMap(recvType)) {

        String mapFn = switch (mc.methodName()) {
            case "put" -> "kof_map_put";
            case "get" -> "kof_map_get";
            case "remove" -> "kof_map_remove";
            case "containsKey", "contains" -> "kof_map_contains";
            case "size", "length", "count" -> "kof_map_size";
            case "clear" -> "kof_map_clear";
            case "isEmpty" -> "kof_map_is_empty";
            case "keys" -> "kof_map_keys";
            case "values" -> "kof_map_values";
            default -> null;
        };
        if (mapFn == null && driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + mc.methodName() + "' on type 'Map' (valid: put/get/remove/containsKey/contains/size/clear/isEmpty/keys/values)",
                    "SEM025");
            return localIdx;
        }
        if (mapFn != null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type keyType = Type.UnknownType.UNKNOWN;
            Type valueType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) {
                keyType = ct.typeArguments().get(0);
                valueType = ct.typeArguments().get(1);
            }
            // mapOf() nasce Map<Unknown,Unknown>: o primeiro put()
            // pina os tipos no local para que get()/remove() tenham
            // tipo concreto (comparações e unboxing corretos)
            if ("kof_map_put".equals(mapFn)
                    && keyType instanceof Type.UnknownType
                    && argTypes.size() == 2
                    && !(argTypes.get(0) instanceof Type.UnknownType)
                    && mc.receiver() instanceof IdentifierExpr rid) {
                for (int li = 0; li < locals.size(); li++) {
                    IRLocalVariable lv = locals.get(li);
                    if (lv.name().equals(rid.name())) {
                        locals.set(li, new IRLocalVariable(lv.index(), lv.name(),
                                new Type.ClassType("kof", "Map", List.of(argTypes.get(0), argTypes.get(1)))));
                        break;
                    }
                }
            }
            Type retType = switch (mapFn) {
                case "kof_map_put", "kof_map_remove" -> valueType;
                // get() devolve V? para valores de REFERÊNCIA (ausência = null,
                // narrowing via `if (x != null)`); para primitivos/UI a ausência
                // não é representável no modelo atual (storage é o primitivo) —
                // ficam como V e a ausência vira exceção/erro de runtime.
                case "kof_map_get" -> valueType instanceof Type.ClassType ct
                        && !KofUi.isUiType(ct) && !KofMedia.isHandleType(ct)
                        ? new Type.NullableType(valueType) : valueType;
                case "kof_map_contains", "kof_map_is_empty" -> Type.PrimitiveType.BOOL;
                case "kof_map_size" -> Type.PrimitiveType.INT;
                case "kof_map_clear" -> Type.PrimitiveType.VOID;
                case "kof_map_keys", "kof_map_values" -> new Type.ClassType("kof", "List", List.of(mapFn.equals("kof_map_keys") ? keyType : valueType));
                default -> Type.UnknownType.UNKNOWN;
            };
            for (ExpressionNode arg : mc.arguments()) localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            ops.add(new KofCall(recvType, mapFn, argTypes, retType, KofCallKind.INSTANCE));
            return localIdx;
        }
    }
    if (BuiltinTypes.isSet(recvType)) {

        String setFn = switch (mc.methodName()) {
            case "add" -> "kof_set_add";
            case "contains" -> "kof_set_contains";
            case "remove" -> "kof_set_remove";
            // add/contains/remove recebem tag de tipo (1=string)
            case "size", "length", "count" -> "kof_set_size";
            case "clear" -> "kof_set_clear";
            case "isEmpty" -> "kof_set_is_empty";
            default -> null;
        };
        if (setFn == null && driver.currentDiagnostics != null
                && !"toArray".equals(mc.methodName()) && !"subSet".equals(mc.methodName()) && !"sublist".equals(mc.methodName())) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + mc.methodName() + "' on type 'Set' (valid: add/contains/remove/size/clear/isEmpty)",
                    "SEM025");
            return localIdx;
        }
        if (setFn != null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type elemType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) elemType = ct.typeArguments().get(0);
            Type retType = switch (setFn) {
                case "kof_set_add", "kof_set_remove" -> Type.PrimitiveType.BOOL;
                case "kof_set_contains", "kof_set_is_empty" -> Type.PrimitiveType.BOOL;
                case "kof_set_size" -> Type.PrimitiveType.INT;
                case "kof_set_clear" -> Type.PrimitiveType.VOID;
                default -> Type.UnknownType.UNKNOWN;
            };
            for (ExpressionNode arg : mc.arguments()) localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            if (driver.target.isNative()
                    && ("kof_set_add".equals(setFn) || "kof_set_contains".equals(setFn)
                        || "kof_set_remove".equals(setFn))) {
                // tag de tipo só no Native (HashSet usa equals no JVM)
                int tag = BuiltinTypes.isString(elemType) ? 1 : 0;
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                argTypes = new ArrayList<>(argTypes);
                argTypes.add(Type.PrimitiveType.INT);
            }
            ops.add(new KofCall(recvType, setFn, argTypes, retType, KofCallKind.INSTANCE));
            return localIdx;
        }
    }
    // bug 16: `toArray()` não é suportado (nem documentado) e
    // caía no caminho genérico → bytecode inválido (JVM) /
    // undefined reference (Native). Diagnóstico limpo em vez de
    // saída quebrada.
    if ("toArray".equals(mc.methodName())
            && (BuiltinTypes.isList(recvType) || BuiltinTypes.isSet(recvType))
            && driver.currentDiagnostics != null) {
        driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                mc.position() != null ? mc.position().line() : 0,
                mc.position() != null ? mc.position().column() : 0, 0,
                "método '" + mc.methodName() + "' não é suportado em coleções;"
                        + " use um loop com new T[n] para materializar um array",
                "SEM029");
    }
    // bug 16 (cauda): `sublist()`/`subSet()` retornam COLEÇÃO —
    // o backend não sabe materializar o retorno de coleção e
    // emitia bytecode inválido (JVM) / undefined reference
    // (Native). Mesmo tratamento do toArray: diagnóstico limpo.
    if (("sublist".equals(mc.methodName()) || "subSet".equals(mc.methodName()))
            && (BuiltinTypes.isList(recvType) || BuiltinTypes.isSet(recvType))
            && driver.currentDiagnostics != null) {
        driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                mc.position() != null ? mc.position().line() : 0,
                mc.position() != null ? mc.position().column() : 0, 0,
                "método '" + mc.methodName() + "' não é suportado em coleções"
                        + " (retorno de coleção não é materializável);"
                        + " copie os elementos com um loop",
                "SEM034");
    }
    Type methodReturnType = Type.UnknownType.UNKNOWN;
    List<Type> methodParamTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) {
        methodParamTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    }
        return -1;
    }
}