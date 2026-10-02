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
    // Higher-orders de List moram em CollectionHigherOrderLowerer (split do
    // gate 500 — o bloco cruzou a linha com os quantificadores any/all/none).
    int ho = CollectionHigherOrderLowerer.lowerHo(driver, recvType, mc, ops, owner, localIdx, locals);
    if (ho >= 0) return ho;
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
        // send/receive lowering em ChannelWrites (regra 7; §374 residual do
        // canal: box-by-ARG no bare — lá a lei única mora).
        int chIdx = ChannelWrites.lower(driver, recvType, mc, ops, owner, localIdx, locals);
        if (chIdx >= 0) return chIdx;
    }
    if (BuiltinTypes.isList(recvType)) {
        // D-MULTIPARADIGMA-PHASE1A slice 1i — zip lives in CollectionZipLowerer
        // (split do gate 500: o bloco cruzou a linha vermelha de 600).
        if ("zip".equals(mc.methodName())) {
            return CollectionZipLowerer.lower(driver, recvType, mc, ops, owner, localIdx, locals);
        }
        String listFn = switch (mc.methodName()) {
            case "add", "push", "append" -> "kof_list_add";
            case "get" -> "kof_list_get";
            case "set" -> "kof_list_set";
            case "size", "length", "count" -> "kof_list_size";
            case "contains" -> "kof_list_contains";
            case "isEmpty" -> "kof_list_is_empty";
            case "remove" -> "kof_list_remove";
            case "clear" -> "kof_list_clear";
            // #382 — indexOf/lastIndexOf/addAll/subList/sort
            case "indexOf" -> "kof_list_index_of";
            case "lastIndexOf" -> "kof_list_last_index_of";
            case "addAll" -> "kof_list_add_all";
            case "subList" -> "kof_list_sub_list";
            // pagination P1 — in-memory window ops (janela materializada).
            case "take" -> "kof_list_take";
            case "drop" -> "kof_list_drop";
            case "slice" -> "kof_list_slice";
            case "sort" -> "kof_list_sort";
            case "sorted" -> CollectionMultiparadigmaLowerer.sortedFn(mc);
            // D-MULTIPARADIGMA-PHASE1A slice 1e — distinct (dedup copy).
            case "distinct" -> "kof_list_distinct";
            case "groupBy" -> "kof_list_groupby";
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
                    "Cannot resolve method '" + m + "' on type 'List' (valid: add/get/set/remove/contains/size/isEmpty/clear/map/filter/reduce/indexOf/lastIndexOf/addAll/subList/take/drop/slice/sort/any/all/none/find/forEach/flatMap/distinct/sorted/groupBy/zip)",
                    "SEM025");
            return localIdx;
        }
        if (listFn != null) {
            // #382/#386 — gates compartilhados (aridade + domínio do sort)
            // em CollectionMethodGates; precedente SEM072/#336, SEM073/#361:
            // um gate na semântica compartilhada, os 4 alvos reportam igual
            // (sem ele, `l.indexOf()` empilhava 0 args e quebrava de um
            // jeito diferente em cada backend — R6).
            String arityMsg = CollectionMethodGates.arityError(
                    listFn, "List", mc.methodName(), mc.arguments().size());
            if (arityMsg != null && driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        arityMsg, "SEM025");
                return localIdx;
            }
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type elemType = driver.listElementType(recvType);
            // SEM097 (domínio natural do sort/sorted) — nunca ordem silenciosa
            // errada. Float×native era NAT001; FECHADO 21/09 (§352): o runtime
            // alarga os 32 bits crus do slot para Double e reusa o compare.
            // Só a forma natural (aridade 0): com comparador, a ordem vem da
            // lambda (D-MULTIPARADIGMA-SORTED) — sem gate de domínio.
            // #685: enum TAMBÉM tem ordem natural REAL (D-ENUM207: ordinal/
            // compareTo determinísticos cross-target), então entra no domínio —
            // a forma natural é rebaixada abaixo para o comparador `a.compareTo(b)`
            // (kof_list_sort_cmp/sorted_cmp), nunca para o tag de primitivo.
            boolean enumNatural = false;
            if ("kof_list_sort".equals(listFn) || "kof_list_sorted".equals(listFn)) {
                enumNatural = CompilerTypes.isEnumType(
                        elemType instanceof Type.NullableType nt0 ? nt0.inner() : elemType,
                        driver.currentUnit);
                if (!enumNatural && driver.currentDiagnostics != null
                        && !CollectionMethodGates.naturalOrderType(elemType)) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            CollectionMethodGates.sortDomainError(elemType), "SEM097");
                    return localIdx;
                }
            }
            if (enumNatural) {
                // Rewrite the natural form into the comparator form: the
                // in-place sort calls kof_list_sort_cmp, the copy sorted calls
                // kof_list_sorted_cmp — both order by the enum's ordinal via
                // compareTo (the SAME method the user/test would call).
                boolean inPlace = "kof_list_sort".equals(listFn);
                ExpressionNode cmp = CollectionLoweringSupport.enumOrderLambda(driver, mc, elemType);
                mc = new MethodCallExpr(mc.position(), mc.receiver(), mc.methodName(),
                        mc.typeArguments(), List.of(cmp));
                listFn = inPlace ? "kof_list_sort_cmp" : "kof_list_sorted_cmp";
                argTypes = new ArrayList<>();
                argTypes.add(new Type.FunctionType(List.of(elemType, elemType),
                        Type.PrimitiveType.INT, null));
            }
            // Slices 1g/1h — gates de forma lambda em CollectionMultiparadigmaLowerer.
            if (CollectionMultiparadigmaLowerer.checkLambdaForm(driver, listFn, mc)) return localIdx;
            // §122 (opção B, família SEM051/052/053/054): o índice de
            // get/set/remove é Int (learn/12: remove(0) devolve o elemento);
            // String/record/array no índice era ACEITO em silêncio e quebrava
            // feio: JVM VerifyError "not assignable to integer" na carga da
            // classe, Native usa o PONTEIRO como índice (array index out of
            // bounds — RM/RM5/IX/IX2 probes 11/09). Unknown/Nullable NÃO
            // flagados (SG-008: pode chegar Int em runtime); numéricos passam
            // (Int é o contrato; o verifier cuida do resto).
            if (("kof_list_get".equals(listFn) || "kof_list_set".equals(listFn)
                    || "kof_list_remove".equals(listFn) || "kof_list_sub_list".equals(listFn))
                    && !argTypes.isEmpty() && driver.currentDiagnostics != null) {
                Type idxT = argTypes.get(0);
                if (CollectionMethodGates.isReferenceIndexType(idxT)) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            "List." + mc.methodName() + " takes an Int INDEX; " + CollectionWrites.typeNameFor(idxT)
                                    + " is not an index (to search by value use contains)",
                            "SEM055");
                    return localIdx;
                }
                // #382: subList tem DOIS índices — o segundo também é Int.
                if ("kof_list_sub_list".equals(listFn) && argTypes.size() > 1
                        && CollectionMethodGates.isReferenceIndexType(argTypes.get(1))) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            "List.subList takes Int INDEX bounds; " + CollectionWrites.typeNameFor(argTypes.get(1))
                                    + " is not an index",
                            "SEM055");
                    return localIdx;
                }
            }
            // pagination P1 — take/drop/slice exigem Int count (SEM055).
            String countMsg = CollectionMethodGates.countDomainError(listFn, mc.methodName(), argTypes);
            if (countMsg != null && driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        countMsg, "SEM055");
                return localIdx;
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
                if (argTypes.size() > valIdx
                        && (CollectionWrites.pollutesPinned(elemType, argTypes.get(valIdx))
                            // §383/#561: escrita de primitivo em slot de
                            // REFERENCIA pinado (ex.: listOf(listOf(1))
                            // .add(true)) e seu espelho (objeto em slot
                            // primitivo) NAO sao "miss abençoado" — quebram
                            // de verdade nos dois alvos compilados (JVM
                            // VerifyError no load, Native SIGSEGV — medidos
                            // 20/09, faces F9/X3). Rejeicao universal com o
                            // mesmo SEM056 (doutina do §126: rejeitar so o
                            // que quebra; aqui quebra nos 4).
                            || CollectionWrites.breaksPinnedList(elemType, argTypes.get(valIdx)))
                        && driver.currentDiagnostics != null) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0,
                            pos != null ? pos.column() : 0, 0,
                            "List." + mc.methodName() + ": element " + CollectionWrites.typeNameFor(argTypes.get(valIdx))
                                    + " does not match the list element type (" + CollectionWrites.typeNameFor(elemType)
                                    + ") — Kof collections are homogeneous",
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
            // §121/§126 (B1): o widening numérico ABENÇOADO pelo §126
            // ("Int em Long passa") precisa da CONVERSÃO no IR antes do
            // store — sem ela o JVM boxeia pelo tipo PINADO (Long.valueOf(J))
            // sobre um int cru na pilha (arg empurrado width-1) → VerifyError
            // "integer not assignable to long_2nd" (B1a), e o unbox do get dá
            // CCE. Mesmo mecanismo do array-store §121 (emitWideningIfNeeded,
            // que SÓ promove I2L/I2F/I2D/L2F/L2D/D2F — nunca trunca). Só o
            // VALOR armazenado (add arg0, set arg1); índice (set arg0) e as
            // buscas contains/get ficam raw (família §126 miss, intocada).
            // §121/§126 (B1): widening numérico abençoado no VALOR (add arg0,
            // set arg1) precisa da conversão IR antes do store — sem ela o
            // JVM boxeia pelo tipo PINADO sobre arg cru (VerifyError/CCE).
            // Índice (set arg0) e buscas ficam raw (família §126 miss).
            int storeValIdx = "kof_list_set".equals(listFn) ? 1 : 0;
            localIdx = CompilerEmissionHelpers.emitArgsCoercingValue(driver, mc, ops, owner,
                    localIdx, locals, argTypes, elemType,
                    ("kof_list_add".equals(listFn) || "kof_list_set".equals(listFn)) ? storeValIdx : -1,
                    // §383: bool→long e conversao de store SO no site de List
                    // (Map/Set toleram heterogeneidade pelo consenso 3/4).
                    "kof_list_add".equals(listFn) || "kof_list_set".equals(listFn));
            Type retType = switch (listFn) {
                case "kof_list_add", "kof_list_set", "kof_list_clear", "kof_list_sort",
                        "kof_list_sort_cmp" -> Type.PrimitiveType.VOID;
                case "kof_list_contains", "kof_list_is_empty", "kof_list_add_all" -> Type.PrimitiveType.BOOL;
                // #382 — indexOf/lastIndexOf: Int (-1 ausente, oracle java.util);
                // subList: List do mesmo tipo de elemento.
                case "kof_list_index_of", "kof_list_last_index_of" -> Type.PrimitiveType.INT;
                case "kof_list_sub_list", "kof_list_take", "kof_list_drop", "kof_list_slice" -> recvType;
                // D-MULTIPARADIGMA-PHASE1A slice 1e — distinct returns List<E> (copy).
                // Slice 1g — sorted/sorted_cmp return List<E> (fresh copy).
                // Slice 1h — groupBy returns Map<K,List<E>> (K = lambda return).
                case "kof_list_distinct", "kof_list_sorted", "kof_list_sorted_cmp" -> recvType;
                case "kof_list_groupby" -> CollectionMultiparadigmaLowerer.groupReturnType(elemType, argTypes);
                case "kof_list_remove" -> elemType;
                default -> elemType;
            };
            if ("kof_list_contains".equals(listFn) || "kof_list_index_of".equals(listFn)
                    || "kof_list_last_index_of".equals(listFn)) {

                // §126: equals de String só quando AMBOS elemType e arg são
                // String conhecidos; senão raw cmpq (nunca deref → miss seguro
                // = false do JVM). Int-arg em String-list era SIGSEGV (E1).
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT,
                        CollectionWrites.stringTag(elemType, argTypes, 0)));
                argTypes = new ArrayList<>(argTypes);
                argTypes.add(Type.PrimitiveType.INT);
            }
            // D-MULTIPARADIGMA-PHASE1A slice 1e — distinct: tag derivada só do
            // elemType (sem arg), mesma taxonomia do contains (0=raw, 1=String
            // content, 2=object via kof_obj_equals); Unknown herda o default 1
            // da família (listas vazias nunca comparam — tag sem uso).
            if ("kof_list_distinct".equals(listFn)) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT,
                        CollectionWrites.stringTag(elemType, List.of(), 0)));
                argTypes = new ArrayList<>(argTypes);
                argTypes.add(Type.PrimitiveType.INT);
            }
            // #382 — sort: tag derivada só do elemType (sem arg):
            // 0=raw signed qword (Int/Long/Bool/Char/Unknown-vazio),
            // 1=String (kof_string_compare_to), 2=Double (ucomisd/fld+flt.d).
            // Slice 1g — a forma natural de sorted() carrega o mesmo tag
            // (reusa kof_list_cmp); a forma com comparador não precisa de
            // tag (a ordem vem da lambda — slots crus como em map/filter).
            if ("kof_list_sort".equals(listFn) || "kof_list_sorted".equals(listFn)) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT,
                        CollectionMethodGates.sortTag(elemType)));
                argTypes = new ArrayList<>(argTypes);
                argTypes.add(Type.PrimitiveType.INT);
            }
            if ("kof_list_groupby".equals(listFn)) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT,
                        CollectionMultiparadigmaLowerer.groupKeyTag(argTypes)));
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
            case "getOrDefault" -> "kof_map_get_or_default";
            case "remove" -> "kof_map_remove";
            case "containsKey", "contains" -> "kof_map_contains";
            case "size", "length", "count" -> "kof_map_size";
            case "clear" -> "kof_map_clear";
            case "isEmpty" -> "kof_map_is_empty";
            case "keys" -> "kof_map_keys";
            case "values" -> "kof_map_values";
            // #386 — containsValue/putIfAbsent
            case "containsValue" -> "kof_map_contains_value";
            case "putIfAbsent" -> "kof_map_put_if_absent";
            default -> null;
        };
        if (mapFn == null && driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + mc.methodName() + "' on type 'Map' (valid: put/get/getOrDefault/putIfAbsent/remove/containsKey/contains/containsValue/size/clear/isEmpty/keys/values)",
                    "SEM025");
            return localIdx;
        }
        if (mapFn != null) {
            // #386 — aridade (CollectionMethodGates, mesmo gate dos List).
            String mapArityMsg = CollectionMethodGates.arityError(
                    mapFn, "Map", mc.methodName(), mc.arguments().size());
            if (mapArityMsg != null && driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        mapArityMsg, "SEM025");
                return localIdx;
            }
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
            // tipo concreto (comparações e unboxing corretos).
            // #386: putIfAbsent ESCREVE como put → entra no mesmo pin
            // (sem ele, putIfAbsent("a",1) num mapa fresco deixava o JVM
            // empilhando int cru contra putIfAbsent(Object,Object)).
            if (("kof_map_put".equals(mapFn) || "kof_map_put_if_absent".equals(mapFn))
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
                // #103 caso 3: o pin acima muda o TIPO DO LOCAL, mas este
                // lowering continua usando o valueType/keyType lidos do
                // receiver ANTES do pin (Unknown). Sem alinhá-los, o
                // retType do KofCall sai Unknown e emitPrevValueUnbox é no-op
                // (put deixa 1 Object na pilha) — mas o typer da statement
                // (SemMethodCallTyper, que roda o mesmo pin) já vê Map<K,Long>
                // e descarta com POP2. 1 slot empilhado × POP2 = underflow do
                // frame (VerifyError / "frame crash"). Alinhar ao pinado casa
                // o unbox (Object→long, 2 slots) com o descarte.
                keyType = argTypes.get(0);
                valueType = argTypes.get(1);
            }
            // §126 (ii): put CHAVE ou VALOR de tipo ≠ pinado polui o mapa.
            // Chave errada = scan tag=1 sobre Int cru → SIGSEGV no Native
            // (A2/H4); valor errado = ClassCastException no JVM no get/unbox.
            // Rejeição cobre os dois lados (decisão "put heterogêneo").
            // #386: putIfAbsent escreve os dois slots → mesma parede.
            if (("kof_map_put".equals(mapFn) || "kof_map_get_or_default".equals(mapFn)
                    || "kof_map_put_if_absent".equals(mapFn))
                    && driver.currentDiagnostics != null) {
                String badSlot = null; Type badType = null, slotType = null;
                int valIdx = 1;
                if (argTypes.size() >= 1 && CollectionWrites.pollutesPinned(keyType, argTypes.get(0))) {
                    badSlot = "chave"; badType = argTypes.get(0); slotType = keyType;
                } else if (argTypes.size() > valIdx && CollectionWrites.pollutesPinned(valueType, argTypes.get(valIdx))) {
                    badSlot = "valor"; badType = argTypes.get(valIdx); slotType = valueType;
                }
                if (badSlot != null) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0,
                            pos != null ? pos.column() : 0, 0,
                            "Map." + mc.methodName() + ": " + badSlot + " " + CollectionWrites.typeNameFor(badType)
                                    + " does not match the map type (" + CollectionWrites.typeNameFor(slotType)
                                    + ") — Kof collections are homogeneous",
                            "SEM056");
                    return localIdx;
                }
            }
            Type retType = switch (mapFn) {
                // D-NULL-INTENT/I7 (#278): put/remove devolvem V? de
                // verdade — mesma razão do get logo abaixo (Java Map
                // contract: valor anterior/removido OU null quando
                // ausente). Sem isto, o KofCall ficava com o tipo ERRADO
                // embutido mesmo com os typers (SemMethodCallTyper etc.)
                // já corrigidos — o backend JS (`?? default`) e o JVM
                // (Type.isVoid guard) consultam ESTE campo, não o typer.
                case "kof_map_put", "kof_map_remove" -> new Type.NullableType(valueType);
                case "kof_map_get_or_default" -> valueType;
                // #386 — putIfAbsent: contrato Java (anterior OU null quando
                // ausente) → V? de verdade, mesma linha do put acima
                // (D-NULL-INTENT/I7). containsValue: Bool.
                case "kof_map_put_if_absent" -> new Type.NullableType(valueType);
                case "kof_map_contains_value" -> Type.PrimitiveType.BOOL;
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
            // §121/§126 (B1): widening no VALOR do put (arg1); chave (arg0)
            // fica raw (família §126 miss: Long-key em String-map → null do
            // get, nunca crash). O box JVM é guiado por parameterTypes —
            // emitArgsCoercingValue ajusta argTypes ao converter.
            localIdx = CompilerEmissionHelpers.emitArgsCoercingValue(driver, mc, ops, owner,
                    localIdx, locals, argTypes, valueType,
                    ("kof_map_put".equals(mapFn) || "kof_map_put_if_absent".equals(mapFn)) ? 1 : -1);
            // §284-map (18/09): o slot de VALOR do Map e fisicamente caixa
            // para a familia Int/Long no nativo — mesmo contrato do JVM
            // (HashMap guarda Integer/Long; JvmOpCollections unboxa no leitor;
            // os consumidores de Nullable(V) emitem kof_unbox nos dois
            // targets). Pre-§284 o par raw-write × unbox-read so fechava
            // porque o unbox era no-op; com a caixa real virou SIGSEGV
            // (rdi=1). Double/Float/Bool ficam crus: nao existe kof_unbox
            // para eles (unboxFn null) — cru × no-op continua casado.
            // List/Set nativos nao sao tocados (storage raw tipado, §253).
            if (("kof_map_put".equals(mapFn) || "kof_map_get_or_default".equals(mapFn)
                    || "kof_map_put_if_absent".equals(mapFn))
                    && argTypes.size() > 1 && driver.target.isNative()
                    && driver.needsErasureBoxing() && CollectionLoweringSupport.mapSlotAcceptsBox(valueType)
                    && (CollectionLoweringSupport.mapBoxablePrim(argTypes.get(1))
                            || CollectionLoweringSupport.referenceSlotPrim(valueType, argTypes.get(1)))
                    && !ExpressionTyper.boxesOwnBranches(driver, mc.arguments().get(1), locals)) {
                CompilerEmissionHelpers.emitErasureBox(driver, ops, argTypes.get(1));
            }
            // #386 — containsValue: extras de valor (box do arg + tag por
            // valueType×arg; §352: mapa Object usa o tag 6 dinâmico — o
            // runtime classifica com kof_value_kind) — responsabilidade em
            // CollectionValueOps; o JVM faz POP do tag, o nativo usa no scan.
            if ("kof_map_contains_value".equals(mapFn)) {
                CollectionValueOps.emitContainsValueExtras(driver, mc, ops, locals,
                        argTypes, valueType);
                argTypes = new ArrayList<>(argTypes);
                argTypes.add(Type.PrimitiveType.INT);
            }
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
                    && CollectionWrites.pollutesPinned(elemType, argTypes.get(0))
                    && driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0,
                        pos != null ? pos.column() : 0, 0,
                        "Set.add: element " + CollectionWrites.typeNameFor(argTypes.get(0))
                                + " does not match the set element type (" + CollectionWrites.typeNameFor(elemType)
                                + ") — Kof collections are homogeneous",
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
                int tag = CollectionWrites.stringTag(elemType, argTypes, 0);
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
                "method '" + mc.methodName() + "' is not supported on collections;"
                        + " use a loop with new T[n] to materialize an array",
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
                "method '" + mc.methodName() + "' is not supported on collections"
                        + " (collection return is not materializable);"
                        + " copy the elements with a loop",
                "SEM034");
    }
    for (ExpressionNode arg : mc.arguments()) {
        ExpressionTyper.inferExprType(driver, arg, locals);
    }
        return -1;
    }


}