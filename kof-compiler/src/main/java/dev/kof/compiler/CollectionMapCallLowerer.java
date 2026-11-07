package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * #1120 (split do gate 500): o ramo MAP de {@code CollectionCallLowerer} —
 * put/get/getOrDefault/remove/containsKey/contains/containsValue/putIfAbsent/
 * size/clear/isEmpty/keys/values. Nenhuma semântica muda; só a moradia.
 */
final class CollectionMapCallLowerer {

    private CollectionMapCallLowerer() {}

    static int lower(CompilerDriver driver, Type recvType, MethodCallExpr mc,
                     List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
        if (!BuiltinTypes.isMap(recvType)) return -1;

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
                    "Cannot resolve method '" + mc.methodName() + "' on type 'Map' (valid: put/get/getOrDefault/putIfAbsent/remove/containsKey/contains/containsValue/size/length/count/clear/isEmpty/keys/values)",
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
        return -1;
    }
}
