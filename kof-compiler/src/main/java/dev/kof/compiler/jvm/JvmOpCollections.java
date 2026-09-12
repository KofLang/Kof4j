package dev.kof.compiler.jvm;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofMedia;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofUi;
import dev.kof.compiler.Type;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

/**
 * Emissão dos calls de coleção/canal/runtime do JvmBackend
 * (REFACTOR-500 FASE 8 — extraído de JvmBackend.emitOperation).
 * Sem estado próprio; só escreve em `mv` e nos flags do backend.
 */
public final class JvmOpCollections {

    private JvmOpCollections() {}

    static void emitKofRuntimeCall(JvmBackend ctx, MethodVisitor mv, KofCall kc) {
        ctx.usesJson = true;
        if (kc.methodName().startsWith("kof_vk_")
                || kc.methodName().startsWith("kof_mv64_")) {
            ctx.usesVk = true;
        }
        if (kc.methodName().startsWith("kof_ffi_")) {
            ctx.usesExtern = true;
        }
        mv.visitMethodInsn(INVOKESTATIC, "dev/kof/runtime/KofRuntime", kc.methodName(),
                JvmRuntimeCallDescriptors.callDescriptor(kc.methodName()), false);
        if ("Ljava/lang/Object;".equals(JvmRuntimeReturnDescriptors.callReturnDescriptor(kc.methodName()))) {
            if (kc.returnType() instanceof Type.ClassType ct && !BuiltinTypes.isString(kc.returnType())) {
                // handle de spawn: o runtime devolve Object (o objeto real é
                // CompletableFuture) — com Handle<T> mapeado p/ CompletableFuture
                // (GitHub #31), o checkcast é necessário e válido.
                mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toInternalName(ct.packageName(), ct.name()));
            } else if ("kof_poll".equals(kc.methodName()) && isPrimitiveType(kc.returnType())) {
                // poll pode devolver null (não pronto): unbox com guard
                String boxed = boxedClassNameFor(kc.returnType());
                Label notNull = new Label();
                Label end = new Label();
                mv.visitInsn(DUP);
                mv.visitJumpInsn(IFNONNULL, notNull);
                mv.visitInsn(POP);
                emitDefaultValue(mv, kc.returnType());
                mv.visitJumpInsn(GOTO, end);
                mv.visitLabel(notNull);
                mv.visitTypeInsn(CHECKCAST, boxed);
                mv.visitMethodInsn(INVOKEVIRTUAL, boxed, unboxMethodName(kc.returnType()),
                        unboxDescriptor(kc.returnType()), false);
                mv.visitLabel(end);
            } else if (("kof_await".equals(kc.methodName())
                    || "kof_await_timeout".equals(kc.methodName())
                    || "kof_select_any".equals(kc.methodName())) && isPrimitiveType(kc.returnType())) {
                // await/awaitTimeout/selectAny com resultado primitivo: o runtime
                // devolve Object (boxed, do CompletableFuture). §128-JVM: selectAny
                // compartilhava o destino primitivo de await mas NÃO era roteado
                // aqui → istore de Object → VerifyError "not assignable to integer".
                emitUnboxIfPrimitive(mv, kc.returnType());
            } else if ("kof_list_reduce".equals(kc.methodName()) && isPrimitiveType(kc.returnType())) {
                emitUnboxIfPrimitive(mv, kc.returnType());
            }
        }
    }

    static void emitStringCall(MethodVisitor mv, KofCall kc) {
        if ("kof_string_concat".equals(kc.methodName())) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
        } else {
            // null-safe string equality (Objects.equals tolerates null)
            mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
        }
    }

    static void emitListCall(MethodVisitor mv, KofCall kc) {
        Type elemType = listElementType(kc.ownerType());
        switch (kc.methodName()) {
            case "kof_list_new" -> {
                mv.visitTypeInsn(NEW, "java/util/ArrayList");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
            }
            case "kof_list_add" -> {
                emitBoxIfPrimitive(mv, elemType);
                // ArrayList.add empilha boolean; o emit descarta — o IR
                // não deve adicionar KofPop para add/set/clear
                // (hasReturnValue = false), senão underflow no frame.
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                mv.visitInsn(POP);
            }
            case "kof_list_get" -> {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                if (!isPrimitiveType(elemType) && !KofUi.isUiType(elemType) && !KofMedia.isHandleType(elemType)) {
                    if (elemType instanceof Type.ArrayType at) {
                        // elemento é array: cast pro tipo JVM real ([I etc)
                        // — callers esperam o componente, não Object
                        mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toDescriptor(at));
                    } else if (elemType instanceof Type.ClassType ct) {
                        mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toInternalName(ct.packageName(), ct.name()));
                    } else if (elemType instanceof Type.FunctionType ft && ft.className() != null) {
                        // elemento é lambda (bug 20): cast para a classe
                        // sintética, senão o invokevirtual seguinte falha no
                        // verifier (Object onde Lambda0 é esperado)
                        mv.visitTypeInsn(CHECKCAST, ft.className());
                    }
                    // Unknown/other: sem cast — a lista guarda Object
                }
                emitUnboxIfPrimitive(mv, elemType);
            }
            case "kof_list_set" -> {
                emitBoxIfPrimitive(mv, elemType);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "set", "(ILjava/lang/Object;)Ljava/lang/Object;", false);
                mv.visitInsn(POP);
            }
            case "kof_list_size" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
            case "kof_list_contains" -> {
                if (kc.parameterTypes().size() > 1) {
                    mv.visitInsn(POP);
                }
                // boxeia pelo tipo do ARGUMENTO (contains(Object) espera
                // Object): elemType de `listOf()` é Unknown e não boxeia o
                // int do argumento → VerifyError (bug 35).
                Type argT = kc.parameterTypes().isEmpty() ? elemType
                        : kc.parameterTypes().get(0);
                emitBoxIfPrimitive(mv, argT);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "contains", "(Ljava/lang/Object;)Z", false);
            }
            case "kof_list_is_empty" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "isEmpty", "()Z", false);
            case "kof_list_remove" -> {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "remove", "(I)Ljava/lang/Object;", false);
                emitUnboxIfPrimitive(mv, elemType);
            }
            case "kof_list_clear" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "clear", "()V", false);
            default -> {}
        }
    }

    static void emitChannelCall(MethodVisitor mv, KofCall kc) {
        // Canais tipados: LinkedBlockingQueue (FIFO thread-safe; put/take
        // bloqueiam — com virtual threads o bloqueio é barato).
        Type elemType = BuiltinTypes.channelElement(kc.ownerType());
        switch (kc.methodName()) {
            case "kof_channel_new" -> {
                mv.visitTypeInsn(NEW, "java/util/concurrent/LinkedBlockingQueue");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/concurrent/LinkedBlockingQueue",
                        "<init>", "()V", false);
            }
            case "kof_channel_send" -> {
                emitBoxIfPrimitive(mv, elemType);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/LinkedBlockingQueue",
                        "put", "(Ljava/lang/Object;)V", false);
            }
            case "kof_channel_receive" -> {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/LinkedBlockingQueue",
                        "take", "()Ljava/lang/Object;", false);
                if (!isPrimitiveType(elemType) && !KofUi.isUiType(elemType) && !KofMedia.isHandleType(elemType)
                        && !(elemType instanceof Type.UnknownType)) {
                    if (elemType instanceof Type.ArrayType at) {
                        mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toDescriptor(at));
                    } else if (elemType instanceof Type.ClassType ct) {
                        mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toInternalName(ct.packageName(), ct.name()));
                    }
                }
                emitUnboxIfPrimitive(mv, elemType);
            }
            default -> {}
        }
    }

    static void emitMapCall(MethodVisitor mv, KofCall kc) {
        Type keyType = Type.UnknownType.UNKNOWN;
        Type valueType = Type.UnknownType.UNKNOWN;
        if (kc.ownerType() instanceof Type.ClassType ct && ct.typeArguments().size() == 2
                && !(ct.typeArguments().get(0) instanceof Type.UnknownType)) {
            keyType = ct.typeArguments().get(0);
            valueType = ct.typeArguments().get(1);
        }
        // tipos reais dos argumentos no call-site (mapOf() nasce Unknown)
        if (!kc.parameterTypes().isEmpty()) {
            keyType = kc.parameterTypes().get(0);
            if (kc.parameterTypes().size() > 1 && !BuiltinTypes.isList(kc.parameterTypes().get(1))) {
                valueType = kc.parameterTypes().get(1);
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
                emitBoxIfPrimitive(mv, valueType);          // [m,k,V]
                if (isPrimitiveType(keyType)) {
                    mv.visitInsn(SWAP);                     // [m,V,k]
                    emitBoxIfPrimitive(mv, keyType);        // [m,V,K]
                    mv.visitInsn(SWAP);                     // [m,K,V]
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
                // VOID no call-site (ex.: pares do mapOf): o valor anterior é descartado
                if (Type.isVoid(kc.returnType())) {
                    mv.visitInsn(POP);
                } else {
                    // §112: HashMap.put devolve Object (prev, possivelmente
                    // null). O typer declara o retorno como V — quando V é
                    // primitivo o stack ficava Object entrando em uso
                    // primitivo → VerifyError (println(m.put(...)) emitia
                    // valueOf(int) sobre Object). Unbox com guard, espelhando
                    // o kof_map_get (null → default do primitivo).
                    emitPrevValueUnbox(mv, kc.returnType());
                }
            }
            case "kof_map_get" -> {
                emitBoxIfPrimitive(mv, keyType);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                // SG-008 (bug 87): get() devolve V? — ausência é null comparável
                // (`x == null` dá true, nunca NPE). Quando o USE espera o
                // primitivo (slot `Int a` / aritmética), o unbox é com GUARD
                // (null → default), espelhando o kof_poll.
                Type valueNullable = kc.returnType() instanceof Type.NullableType nt
                        ? nt.inner() : null;
                Type unboxGuardType = valueNullable;
                if (unboxGuardType != null && boxedClassNameFor(unboxGuardType) != null) {
                    String boxed = boxedClassNameFor(unboxGuardType);
                    Label notNull = new Label();
                    Label end = new Label();
                    mv.visitInsn(DUP);
                    mv.visitJumpInsn(IFNONNULL, notNull);
                    mv.visitInsn(POP);
                    emitDefaultValue(mv, unboxGuardType);
                    mv.visitJumpInsn(GOTO, end);
                    mv.visitLabel(notNull);
                    mv.visitTypeInsn(CHECKCAST, boxed);
                    mv.visitMethodInsn(INVOKEVIRTUAL, boxed, unboxMethodName(unboxGuardType),
                            unboxDescriptor(unboxGuardType), false);
                    mv.visitLabel(end);
                } else if (valueNullable != null) {
                    // valor de referência (String? etc.): só o cast
                    if (!KofUi.isUiType(valueType) && !KofMedia.isHandleType(valueType) && !(valueType instanceof Type.UnknownType)) {
                        String internal = JvmTypeMapper.toInternalName(valueType instanceof Type.ClassType ct ? ct.packageName() : "", valueType instanceof Type.ClassType ct ? ct.name() : "java/lang/Object");
                        mv.visitTypeInsn(CHECKCAST, internal);
                    }
                } else {
                    if (!isPrimitiveType(valueType) && !KofUi.isUiType(valueType) && !KofMedia.isHandleType(valueType) && !(valueType instanceof Type.UnknownType)) {
                        String internal = JvmTypeMapper.toInternalName(valueType instanceof Type.ClassType ct ? ct.packageName() : "", valueType instanceof Type.ClassType ct ? ct.name() : "java/lang/Object");
                        mv.visitTypeInsn(CHECKCAST, internal);
                    }
                    emitUnboxIfPrimitive(mv, valueType);
                }
            }
            case "kof_map_remove" -> {
                emitBoxIfPrimitive(mv, keyType);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                if (!isPrimitiveType(valueType) && !(valueType instanceof Type.UnknownType)) {
                    String internal = JvmTypeMapper.toInternalName(valueType instanceof Type.ClassType ct ? ct.packageName() : "", valueType instanceof Type.ClassType ct ? ct.name() : "java/lang/Object");
                    mv.visitTypeInsn(CHECKCAST, internal);
                }
                // §112: remove de chave AUSENTE devolve null — o unbox cru de
                // primitivo dava NullPointerException (NPE não é exceção-as-
                // String do contrato Kof). Guard com default, como get/put.
                if (isPrimitiveType(valueType) && !KofUi.isUiType(valueType) && !KofMedia.isHandleType(valueType)) {
                    emitPrevValueUnbox(mv, valueType);
                }
            }
            case "kof_map_contains" -> {
                emitBoxIfPrimitive(mv, keyType);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "containsKey", "(Ljava/lang/Object;)Z", false);
            }
            case "kof_map_size" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "size", "()I", false);
            case "kof_map_is_empty" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "isEmpty", "()Z", false);
            case "kof_map_clear" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "clear", "()V", false);
            case "kof_map_keys" -> {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "keySet", "()Ljava/util/Set;", false);
                mv.visitTypeInsn(NEW, "java/util/ArrayList");
                mv.visitInsn(DUP_X1);
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V", false);
            }
            case "kof_map_values" -> {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "values", "()Ljava/util/Collection;", false);
                mv.visitTypeInsn(NEW, "java/util/ArrayList");
                mv.visitInsn(DUP_X1);
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V", false);
            }
            default -> {}
        }
    }

    static void emitSetCall(MethodVisitor mv, KofCall kc) {
        Type elemType = Type.UnknownType.UNKNOWN;
        if (kc.ownerType() instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()
                && !(ct.typeArguments().get(0) instanceof Type.UnknownType)) {
            elemType = ct.typeArguments().get(0);
        }
        // tipo real do argumento no call-site (setOf() nasce Unknown)
        if (!kc.parameterTypes().isEmpty()) {
            elemType = kc.parameterTypes().get(0);
        }
        switch (kc.methodName()) {
            case "kof_set_new" -> {
                mv.visitTypeInsn(NEW, "java/util/HashSet");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashSet", "<init>", "()V", false);
            }
            case "kof_set_add" -> {
                emitBoxIfPrimitive(mv, elemType);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "add", "(Ljava/lang/Object;)Z", false);
                if (Type.isVoid(kc.returnType())) {
                    mv.visitInsn(POP);
                }
            }
            case "kof_set_contains" -> {
                emitBoxIfPrimitive(mv, elemType);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "contains", "(Ljava/lang/Object;)Z", false);
            }
            case "kof_set_remove" -> {
                emitBoxIfPrimitive(mv, elemType);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "remove", "(Ljava/lang/Object;)Z", false);
            }
            case "kof_set_size" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "size", "()I", false);
            case "kof_set_is_empty" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "isEmpty", "()Z", false);
            case "kof_set_clear" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "clear", "()V", false);
            default -> {}
        }
    }

    // ── helpers de box/unbox e predicados de tipo (compartilhados) ──

    /** Valor default do primitivo (0/false/0.0) na pilha, com width correto. */
    static void emitDefaultValue(MethodVisitor mv, Type type) {
        String n = type instanceof Type.PrimitiveType pt ? pt.name() : "";
        switch (Type.canonicalPrimitiveName(n)) {
            case "long" -> { mv.visitInsn(LCONST_0); }
            case "float" -> { mv.visitInsn(FCONST_0); }
            case "double" -> { mv.visitInsn(DCONST_0); }
            default -> mv.visitInsn(ICONST_0);
        }
    }

    static String unboxMethodName(Type boxed) {
        // bug 109: o GUARD do kof_map_get (Nullable(V) com V primitivo) chama
        // esta função com o tipo PRIMITIVO interno (Bool/Int/...), não com a
        // Classe boxada — antes só o ramo ClassType era tratado e um Bool
        // caía no `return "intValue"` → `Boolean.intValue()Z` →
        // NoSuchMethodError em runtime (mapOf(k, true).get(k) CRASHAVA no JVM;
        // só o path ClassType (await/poll) acertava). Espelha o dispatch de
        // nome de boxedClassNameFor (mesma tabela, método correto por tipo).
        if (boxed instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "long", "Long" -> "longValue";
                case "float", "Float" -> "floatValue";
                case "double", "Double" -> "doubleValue";
                case "boolean", "bool", "Bool" -> "booleanValue";
                case "byte", "Byte" -> "byteValue";
                case "short", "Short" -> "shortValue";
                // char é guardado BOXED AS Integer (boxedClassNameFor default →
                // java/lang/Integer; emitBoxIfPrimitive → valueOf(I)). §104b-ii
                // face JVM: o unbox derivava method+desc do primitivo DECLARADO
                // (charValue/()C) → `Integer.charValue()C` inexistente →
                // NoSuchMethodError em `mapOf(k,'a').get(k)` / `listOf('a').get`.
                // A caixa é Integer, então o unbox é intValue/()I (char Kof é
                // int-width no JVM — o print dá o codepoint, oracle 97).
                case "char", "Char" -> "intValue";
                default -> "intValue";
            };
        }
        if (boxed instanceof Type.ClassType ct) {
            return switch (ct.name()) {
                case "Integer" -> "intValue";
                case "Long" -> "longValue";
                case "Boolean" -> "booleanValue";
                case "Float" -> "floatValue";
                case "Double" -> "doubleValue";
                case "Character" -> "charValue";
                case "Byte" -> "byteValue";
                case "Short" -> "shortValue";
                default -> "intValue";
            };
        }
        return "intValue";
    }

    /**
     * Descritor do método de unbox — sempre coerente com a CLASSE boxada real
     * (`boxedClassNameFor`). Nunca o tipo do primitivo DECLARADO: char é
     * guardado como `Integer` (não `Character`), e `toDescriptor(CHAR)="C"`
     * produzia `Integer.charValue()C` / `Integer.intValue()C` inexistentes
     * (§104b-ii face JVM — NoSuchMethodError). Nullable desembrulha (o guard
     * faz CHECKCAST na caixa do INNER).
     */
    static String unboxDescriptor(Type primitive) {
        Type prim = primitive instanceof Type.NullableType nt ? nt.inner() : primitive;
        if (prim instanceof Type.PrimitiveType pt) {
            String n = Type.canonicalPrimitiveName(pt.name());
            if ("char".equals(n)) return "()I";
        }
        return "()" + JvmTypeMapper.toDescriptor(prim);
    }

    static String boxedClassNameFor(Type primitive) {
        if (primitive instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "long", "Long" -> "java/lang/Long";
                case "float", "Float" -> "java/lang/Float";
                case "double", "Double" -> "java/lang/Double";
                case "boolean", "bool", "Bool" -> "java/lang/Boolean";
                case "byte", "Byte" -> "java/lang/Byte";
                case "short", "Short" -> "java/lang/Short";
                default -> "java/lang/Integer";
            };
        }
        // kof.ui handles (Color, Theme, Label, Button, Input, Column, Row,
        // View, Style, Window) are Int values on every target; on the JVM
        // they must be boxed when stored in Object slots (e.g. List<Label>).
        if (dev.kof.compiler.KofUi.isUiType(primitive) || KofMedia.isHandleType(primitive)) {
            return "java/lang/Integer";
        }
        return null;
    }

    static void emitBoxIfPrimitive(MethodVisitor mv, Type type) {
        String boxed = boxedClassNameFor(type);
        if (boxed != null) {
            String desc = JvmTypeMapper.toDescriptor(type);
            if ("char".equals(typeName(type)) || "Char".equals(typeName(type))) desc = "I";
            if (KofUi.isUiType(type) || KofMedia.isHandleType(type)) desc = "I";
            mv.visitMethodInsn(INVOKESTATIC, boxed, "valueOf", "(" + desc + ")L" + boxed + ";", false);
        }
    }

    static public boolean isPrimitiveType(Type type) {
        if (type instanceof Type.NullableType nt) return isPrimitiveType(nt.inner());
        return type instanceof Type.PrimitiveType pt && !"void".equals(pt.name());
    }

    /**
     * §112: unbox com guard para o VALOR ANTERIOR devolvido por
     * `HashMap.put`/`HashMap.remove` quando o tipo declarado do retorno é
     * primitivo. Esses métodos devolvem `Object` (o prev), que pode ser
     * **null** (primeiro put / remove de chave ausente). O unbox cru de
     * primitivo (`checkcast Integer; intValue`) estourava NullPointerException
     * (não é exceção-as-String do contrato Kof) e, no put, o Object entrando em
     * uso primitivo dava VerifyError. Espelha o guard do `kof_map_get`:
     * null → default do primitivo (0/false/0.0). Recebe o Object no topo.
     */
    static void emitPrevValueUnbox(MethodVisitor mv, Type declared) {
        Type prim = declared instanceof Type.NullableType nt ? nt.inner() : declared;
        String boxed = boxedClassNameFor(prim);
        if (boxed == null) {
            // valor de referência: só o cast (null-safe); Unknown/UI/Media
            // não cast (null é comparável, sem NPE).
            if (!(prim instanceof Type.UnknownType)
                    && !KofUi.isUiType(prim) && !KofMedia.isHandleType(prim)
                    && prim instanceof Type.ClassType ct) {
                mv.visitTypeInsn(CHECKCAST,
                        JvmTypeMapper.toInternalName(ct.packageName(), ct.name()));
            }
            return;
        }
        Label notNull = new Label();
        Label end = new Label();
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNONNULL, notNull);
        mv.visitInsn(POP);
        emitDefaultValue(mv, prim);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(notNull);
        mv.visitTypeInsn(CHECKCAST, boxed);
        mv.visitMethodInsn(INVOKEVIRTUAL, boxed, unboxMethodName(prim),
                unboxDescriptor(prim), false);
        mv.visitLabel(end);
    }

    static void emitUnboxIfPrimitive(MethodVisitor mv, Type type) {
        String boxed = boxedClassNameFor(type);
        if (boxed != null) {
            mv.visitTypeInsn(CHECKCAST, boxed);
            String method = boxed.endsWith("Integer") ? "intValue"
                    : boxed.endsWith("Long") ? "longValue"
                    : boxed.endsWith("Boolean") ? "booleanValue"
                    : boxed.endsWith("Float") ? "floatValue"
                    : boxed.endsWith("Double") ? "doubleValue"
                    : boxed.endsWith("Byte") ? "byteValue"
                    : boxed.endsWith("Short") ? "shortValue" : "intValue";
            mv.visitMethodInsn(INVOKEVIRTUAL, boxed, method, unboxDescriptor(type), false);
        }
    }

    public static boolean isPrimitiveOf(Type type, String name) {
        if (type instanceof Type.NullableType nt) return isPrimitiveOf(nt.inner(), name);
        return type instanceof Type.PrimitiveType pt && (pt.name().equals(name) || pt.name().equals(capitalize(name)));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String typeName(Type type) {
        return type instanceof Type.PrimitiveType pt ? pt.name() : "";
    }

    private static Type listElementType(Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }
}
