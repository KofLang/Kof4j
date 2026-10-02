package dev.kof.compiler.jvm;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofMedia;
import dev.kof.compiler.KofUi;
import dev.kof.compiler.Type;

import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

/**
 * Emissão dos calls de `Map` do JvmBackend (extraído de JvmOpCollections
 * pelo gate §500/§441). Sem estado próprio; usa os helpers de box/unbox
 * de {@link JvmOpCollections}.
 */
public final class JvmOpMap {

    private JvmOpMap() {}

    static void emitMapCall(JvmBackend ctx, MethodVisitor mv, KofCall kc) {
        Type keyType = Type.UnknownType.UNKNOWN;
        Type valueType = Type.UnknownType.UNKNOWN;      // slot V — governa o RESULTADO
        if (kc.ownerType() instanceof Type.ClassType ct && ct.typeArguments().size() == 2
                && !(ct.typeArguments().get(0) instanceof Type.UnknownType)) {
            keyType = ct.typeArguments().get(0);
            valueType = ct.typeArguments().get(1);
        }
        // Tipos REAIS dos argumentos no call-site (mapOf() nasce Unknown).
        // §432: o V do SLOT (dono) e o tipo do valor ESCRITO podem divergir
        // (`Map<String,Object>.put(k, 2.5)` / `.getOrDefault(k, 9.5)` — o
        // default é `Double` mas o slot é `Object`). `writtenValueType` governa
        // o BOX do valor/default; `valueType` (o V do dono) governa o
        // RESULTADO. Antes os dois eram o mesmo e o V virava `Double`, então o
        // resultado saía cru (`double`) onde o consumidor esperava `Object` →
        // VerifyError (bad type on operand stack). Só cai no tipo do argumento
        // quando o dono não informa V (mapOf() sem pin).
        Type writtenValueType = valueType;
        if (!kc.parameterTypes().isEmpty()) {
            keyType = kc.parameterTypes().get(0);
            if (kc.parameterTypes().size() > 1 && !BuiltinTypes.isList(kc.parameterTypes().get(1))) {
                writtenValueType = kc.parameterTypes().get(1);
                if (valueType instanceof Type.UnknownType) valueType = writtenValueType;
            }
        }
        switch (kc.methodName()) {
            case "kof_map_new" -> {
                mv.visitTypeInsn(NEW, "java/util/HashMap");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
            }
            case "kof_map_put" -> {
                // stack: map, key, value — box ambos antes do put(Object,Object)
                // §441: chave larga (Long/Double) não pode passar por `swap`.
                emitBoxValueUnderKey(ctx, mv, writtenValueType, keyType);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
                // VOID no call-site (ex.: pares do mapOf): o valor anterior é descartado
                if (Type.isVoid(kc.returnType())) {
                    mv.visitInsn(POP);
                } else {
                    // D-NULL-INTENT/I7 (supersede §112): HashMap.put devolve
                    // Object (prev, possivelmente null) — o typer agora
                    // declara V? de verdade (não V), então só falta fixar o
                    // tipo estático. Nunca substitui ausência por default.
                    emitNullablyBoxedMapResult(mv, valueType);
                }
            }
            case "kof_map_get" -> {
                JvmOpCollections.emitBoxIfPrimitive(mv, keyType);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
                // D-NULL-INTENT/I7 (supersede SG-008/bug-87 default-guard):
                // get() devolve V? de verdade — ausência é null observável,
                // NUNCA substituído pelo default do primitivo. O unbox, para
                // quando o consumidor pede o valor cru, é responsabilidade
                // de quem CONSOME (guiado pelo tipo do slot/expressão), não
                // deste ponto de chamada.
                emitNullablyBoxedMapResult(mv, valueType);
            }
            case "kof_map_remove" -> {
                JvmOpCollections.emitBoxIfPrimitive(mv, keyType);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
                // D-NULL-INTENT/I7 (supersede §112): mesma razão do get/put
                // acima — remove de chave ausente devolve null de verdade.
                emitNullablyBoxedMapResult(mv, valueType);
            }
            case "kof_map_get_or_default" -> {
                // stack: map, key, default — o DEFAULT está no topo e a CHAVE
                // embaixo: boxa o default primeiro e a chave depois (§441,
                // mesmo helper do put/putIfAbsent). A ordem anterior
                // (chave→default) empilhava Integer.valueOf DEPOIS do default,
                // consumindo o default como int → VerifyError quando a chave
                // era primitiva.
                emitBoxValueUnderKey(ctx, mv, writtenValueType, keyType);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "getOrDefault",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
                if (!JvmOpCollections.isPrimitiveType(valueType) && !KofUi.isUiType(valueType) && !KofMedia.isHandleType(valueType) && !(valueType instanceof Type.UnknownType)) {
                    String internal = JvmTypeMapper.toInternalName(valueType instanceof Type.ClassType ct ? ct.packageName() : "", valueType instanceof Type.ClassType ct ? ct.name() : "java/lang/Object");
                    mv.visitTypeInsn(CHECKCAST, internal);
                }
                JvmOpCollections.emitUnboxIfPrimitive(mv, valueType);
            }
            case "kof_map_contains" -> {
                JvmOpCollections.emitBoxIfPrimitive(mv, keyType);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "containsKey", "(Ljava/lang/Object;)Z", true);
            }
            // #386 — containsValue(Object): box pelo tipo do ARG (o arg é o
            // valor candidato; keyType aqui resolvido do parameterTypes[0] é
            // exatamente esse tipo — java.util usa equals, o tag extra do
            // call-site nativo é descartado).
            case "kof_map_contains_value" -> {
                if (kc.parameterTypes().size() > 1) {
                    mv.visitInsn(POP);
                }
                if (!kc.parameterTypes().isEmpty()) {
                    JvmOpCollections.emitBoxIfPrimitive(mv, kc.parameterTypes().get(0));
                }
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "containsValue",
                        "(Ljava/lang/Object;)Z", true);
            }
            // #386 — putIfAbsent: mesmo contrato do put (anterior OU null);
            // box key+value como getOrDefault, resultado V? como put
            // (emitNullablyBoxedMapResult — CHECKCAST, nunca unbox: null
            // ausente tem que sobreviver, D-NULL-INTENT/I7).
            case "kof_map_put_if_absent" -> {
                // §441: mesma ordenação do put — chave larga não passa por `swap`.
                emitBoxValueUnderKey(ctx, mv, writtenValueType, keyType);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "putIfAbsent",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
                if (Type.isVoid(kc.returnType())) {
                    mv.visitInsn(POP);
                } else {
                    emitNullablyBoxedMapResult(mv, valueType);
                }
            }
            case "kof_map_size" -> mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "size", "()I", true);
            case "kof_map_is_empty" -> mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "isEmpty", "()Z", true);
            case "kof_map_clear" -> mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "clear", "()V", true);
            case "kof_map_keys" -> {
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "keySet", "()Ljava/util/Set;", true);
                mv.visitTypeInsn(NEW, "java/util/ArrayList");
                mv.visitInsn(DUP_X1);
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V", false);
            }
            case "kof_map_values" -> {
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "values", "()Ljava/util/Collection;", true);
                mv.visitTypeInsn(NEW, "java/util/ArrayList");
                mv.visitInsn(DUP_X1);
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V", false);
            }
            default -> {}
        }
    }

    /**
     * D-NULL-INTENT/I7 (#278, supersede §112): resultado de
     * get/put/remove de Map — o valor já chega BOXED (ou null) de
     * {@code java.util.Map}; aqui só se fixa o tipo ESTÁTICO via
     * CHECKCAST, nunca se desempacota nem se substitui ausência por
     * default. Ausência (null) e {@code Present(0)}/{@code Present(false)}
     * ficam observáveis e distintos — o unbox primitivo, quando o
     * consumidor pede o valor cru, é responsabilidade de quem CONSOME
     * (guiado pelo tipo do slot), não deste ponto de chamada.
     */
    static void emitNullablyBoxedMapResult(MethodVisitor mv, Type valueType) {
        String boxed = JvmOpCollections.boxedClassNameFor(valueType);
        if (boxed != null) {
            mv.visitTypeInsn(CHECKCAST, boxed);
            return;
        }
        if (!KofUi.isUiType(valueType) && !KofMedia.isHandleType(valueType) && !(valueType instanceof Type.UnknownType)) {
            String internal = JvmTypeMapper.toInternalName(
                    valueType instanceof Type.ClassType ct ? ct.packageName() : "",
                    valueType instanceof Type.ClassType ct ? ct.name() : "java/lang/Object");
            mv.visitTypeInsn(CHECKCAST, internal);
        }
    }

    // §441: [map, chave, valor] -> [map, K(box), V(box)] para Map.put/
    // putIfAbsent. Uma chave LARGA (long/double, categoria 2) não pode
    // participar de `swap` (bytecode inválido) — a boxagem da chave usa o
    // slot de rascunho do método. Chave estreita mantém o `swap` provado.
    private static void emitBoxValueUnderKey(JvmBackend ctx, MethodVisitor mv, Type valueType, Type keyType) {
        JvmOpCollections.emitBoxIfPrimitive(mv, valueType);   // [m, k, V]
        if (!JvmOpCollections.isPrimitiveType(keyType)) return;
        if (JvmLiteralEmitter.isDoubleWidth(keyType)) {
            int slot = ctx.scratchLocalIndex();
            mv.visitVarInsn(ASTORE, slot);                    // [m, k]
            JvmOpCollections.emitBoxIfPrimitive(mv, keyType); // [m, K]
            mv.visitVarInsn(ALOAD, slot);                     // [m, K, V]
        } else {
            mv.visitInsn(SWAP);                               // [m, V, k]
            JvmOpCollections.emitBoxIfPrimitive(mv, keyType); // [m, V, K]
            mv.visitInsn(SWAP);                               // [m, K, V]
        }
    }
}
