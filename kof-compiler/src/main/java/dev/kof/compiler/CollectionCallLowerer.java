package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de métodos de coleção (List/Channel/Map/Set) no emitExpression.
 * Retorna -1 se nenhum método de coleção foi reconhecido (cai no genérico).
 */
public final class CollectionCallLowerer {

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
                // (#57: IfExpr/switch heterogêneo já boxeou in-branch → pular)
                if (TypeMetrics.isPrimitiveType(argT) && driver.target == Target.JVM
                        && !ExpressionTyper.boxesOwnBranches(driver, arg, locals)) {
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
            // §122 (opção B, família SEM051/052/053/054): o índice de
            // get/set/remove é Int (learn/12: remove(0) devolve o elemento);
            // String/record/array no índice era ACEITO em silêncio e quebrava
            // feio: JVM VerifyError "not assignable to integer" na carga da
            // classe, Native usa o PONTEIRO como índice (array index out of
            // bounds — RM/RM5/IX/IX2 probes 11/09). Unknown/Nullable NÃO
            // flagados (SG-008: pode chegar Int em runtime); numéricos passam
            // (Int é o contrato; o verifier cuida do resto).
            if (("kof_list_get".equals(listFn) || "kof_list_set".equals(listFn)
                    || "kof_list_remove".equals(listFn))
                    && !argTypes.isEmpty() && driver.currentDiagnostics != null) {
                Type idxT = argTypes.get(0);
                if (isReferenceIndexType(idxT)) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0,
                            pos != null ? pos.column() : 0, 0,
                            "List." + mc.methodName() + " pega ÍNDICE Int; " + typeNameFor(idxT)
                                    + " não é índice (para buscar por valor use contains)",
                            "SEM055");
                    return localIdx;
                }
            }
                // listOf() with no type argument produces
            // List<Unknown>; the first add() pins the element
            // type on the local so later get() calls are
            // typed (records, classes) instead of Object.
            if ("kof_list_add".equals(listFn) || "kof_list_set".equals(listFn)) {
                // §126 (ii): add/set de tipo ≠ elemType PINADA polui o heap —
                // o JVM já VerifyError no get/unbox, o Native SIGSEGV no
                // scan com tag String. Rejeitar em compile-time (SEM056).
                // Só quando elemType já é concreto (o add que PINA um
                // List<Unknown> não é poluição — é a definição do tipo).
                // set: o VALOR é o arg 1 (o índice já foi checado em SEM055).
                int valIdx = "kof_list_set".equals(listFn) ? 1 : 0;
                if (argTypes.size() > valIdx && pollutesPinned(elemType, argTypes.get(valIdx))
                        && driver.currentDiagnostics != null) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0,
                            pos != null ? pos.column() : 0, 0,
                            "List." + mc.methodName() + ": elemento " + typeNameFor(argTypes.get(valIdx))
                                    + " não casa com o tipo da lista (" + typeNameFor(elemType)
                                    + ") — coleções Kof são homogêneas",
                            "SEM056");
                    return localIdx;
                }
            }
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

                // §126: equals de String só quando AMBOS elemType e arg são
                // String conhecidos; senão raw cmpq (nunca deref → miss seguro
                // = false do JVM). Int-arg em String-list era SIGSEGV (E1).
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT,
                        stringTag(elemType, argTypes, 0)));
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
            // §126 (ii): put CHAVE ou VALOR de tipo ≠ pinado polui o mapa.
            // Chave errada = scan tag=1 sobre Int cru → SIGSEGV no Native
            // (A2/H4); valor errado = ClassCastException no JVM no get/unbox.
            // Rejeição cobre os dois lados (decisão "put heterogêneo").
            if ("kof_map_put".equals(mapFn) && driver.currentDiagnostics != null) {
                String badSlot = null; Type badType = null, slotType = null;
                if (argTypes.size() >= 1 && pollutesPinned(keyType, argTypes.get(0))) {
                    badSlot = "chave"; badType = argTypes.get(0); slotType = keyType;
                } else if (argTypes.size() >= 2 && pollutesPinned(valueType, argTypes.get(1))) {
                    badSlot = "valor"; badType = argTypes.get(1); slotType = valueType;
                }
                if (badSlot != null) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0,
                            pos != null ? pos.column() : 0, 0,
                            "Map.put: " + badSlot + " " + typeNameFor(badType)
                                    + " não casa com o tipo do mapa (" + typeNameFor(slotType)
                                    + ") — coleções Kof são homogêneas",
                            "SEM056");
                    return localIdx;
                }
            }
            Type retType = switch (mapFn) {
                case "kof_map_put", "kof_map_remove" -> valueType;
                // get() devolve V? (SG-008/bug 87): ausência é null comparável
                // (`x == null`), nunca NPE por unbox. O unbox acontece no
                // USE (aritmética), guiado pelo tipo do slot.
                case "kof_map_get" -> new Type.NullableType(valueType);
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
            // §126 (ii): Set.add com tipo ≠ elemType pinada = heap poluído
            // (o scan tag=1 chama kof_string_equals sobre Int cru → SIGSEGV
            // no Native — ST1/H3). JVM tolera; a linguagem NÃO (homogênea).
            if ("kof_set_add".equals(setFn) && !argTypes.isEmpty()
                    && pollutesPinned(elemType, argTypes.get(0))
                    && driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0,
                        pos != null ? pos.column() : 0, 0,
                        "Set.add: elemento " + typeNameFor(argTypes.get(0))
                                + " não casa com o tipo do set (" + typeNameFor(elemType)
                                + ") — coleções Kof são homogêneas",
                        "SEM056");
                return localIdx;
            }
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
                // §126: conjunção elem×arg — senão raw cmpq, que nunca deref
                // (Int-arg em String-set era SIGSEGV: ST1/ST2).
                int tag = stringTag(elemType, argTypes, 0);
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

    /** §122: tipos que NUNCA são um índice válido p/ get/set/remove de List. */
    private static boolean isReferenceIndexType(Type t) {
        if (t == null || Type.UnknownType.UNKNOWN.equals(t)) return false;
        if (t instanceof Type.NullableType nt) return isReferenceIndexType(nt.inner());
        if (TypeMetrics.isPrimitiveType(t)) return false;
        return t instanceof Type.ClassType || t instanceof Type.ArrayType
                || t instanceof Type.TypeVariable;
    }

    /**
     * §126 (opção ii — decisão da mantenedora 11/09): um add/put cujo valor é
     * do TIPO ERRADO para o elemento/chave PINADA do container polui o heap —
     * uma futura varredura com tag String (elem String) chama kof_string_equals
     * sobre o candidato não-String e o trata como ponteiro → SIGSEGV no Native
     * (H3/H4; JVM tolera com HashMap heterogêneo). A correção é ESTÁTICA:
     * rejeitar em compile-time (SEM056), família SEM055/§122 — "Kof estático".
     *
     * Rejeita APENAS a família que quebra de verdade: ambos os lados conhecidos
     * e concretos, e um é String enquanto o outro NÃO é (é isto que vira tag=1
     * sobre um inteiro). Casos apenas de "miss" (non-String-pinned recebe
     * String → raw cmpq, nunca deref → false/null como o JVM) e widening
     * numérico (Int em Long) e Unknown/TypeVariable (SG-008: pode casar em
     * runtime) PASSAM — não rejeitar o que não é perigoso.
     */
    private static boolean pollutesPinned(Type pinned, Type arg) {
        if (pinned == null || arg == null) return false;
        Type p = pinned instanceof Type.NullableType pn ? pn.inner() : pinned;
        Type a = arg instanceof Type.NullableType an ? an.inner() : arg;
        if (p instanceof Type.UnknownType || a instanceof Type.UnknownType) return false;
        if (p instanceof Type.TypeVariable || a instanceof Type.TypeVariable) return false;
        if (BuiltinTypes.isString(p) == BuiltinTypes.isString(a)) return false;
        return true;
    }

    /**
     * §126: tag de comparação do Native (1 = kof_string_equals, 0 = raw
     * cmpq). O equals de String só é SEGURO quando ambos os lados são
     * String conhecidos: o lado desconhecido pode ser um Int cru que o
     * kof_string_equals trataria como ponteiro → SIGSEGV (A1/E1/ST1).
     * Qualquer outro par cai no raw cmpq, que nunca deref e devolve o
     * miss silencioso (false/null) — exatamente o que o JVM faz com
     * tipos incompatíveis no HashMap/HashSet/ArrayList reais.
     */
    static int stringTag(Type elemType, List<Type> argTypes, int argIdx) {
        Type at = argIdx < argTypes.size() ? argTypes.get(argIdx) : null;
        if (at instanceof Type.NullableType nt) at = nt.inner();
        Type et = elemType instanceof Type.NullableType ent ? ent.inner() : elemType;
        boolean etKnown = et != null && !(et instanceof Type.UnknownType);
        boolean atKnown = at != null && !(at instanceof Type.UnknownType);
        if (etKnown && atKnown) {
            return BuiltinTypes.isString(et) && BuiltinTypes.isString(at) ? 1 : 0;
        }
        if (etKnown) return BuiltinTypes.isString(et) ? 1 : 0;
        if (atKnown) return BuiltinTypes.isString(at) ? 1 : 0;
        return 1;
    }

    private static String typeNameFor(Type t) {
        if (t instanceof Type.NullableType nt) return typeNameFor(nt.inner()) + "?";
        if (t instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "int" -> "Int"; case "long" -> "Long"; case "double" -> "Double";
                case "float" -> "Float"; case "bool" -> "Bool"; case "char" -> "Char";
                case "byte" -> "Byte"; case "short" -> "Short"; default -> pt.name();
            };
        }
        if (t instanceof Type.ClassType ct) return ct.name();
        if (t instanceof Type.ArrayType a) return typeNameFor(a.componentType()) + "[]";
        return String.valueOf(t);
    }
}