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
        if (kc.methodName().startsWith("kof_ffi")) {
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
                    || "kof_select_any".equals(kc.methodName()))
                    && isPrimitiveType(kc.returnType())) {
                // await/awaitTimeout/selectAny com resultado primitivo: o runtime
                // devolve Object (boxed, do CompletableFuture). §128-JVM: selectAny
                // compartilhava o destino primitivo de await mas NÃO era roteado
                // aqui → istore de Object → VerifyError "not assignable to integer".
                emitUnboxIfPrimitive(mv, kc.returnType());
            } else if (("kof_await".equals(kc.methodName())
                    || "kof_await_timeout".equals(kc.methodName())
                    || "kof_select_any".equals(kc.methodName()))
                    && Type.PrimitiveType.VOID.equals(kc.returnType())) {
                // await/awaitTimeout/selectAny de task VAZIA: o descritor do
                // runtime devolve Object SEMPRE (null quando a task não tem
                // valor), mas o modelo Kof da expressão é void → o discard de
                // statement não emite POP e o Object ficava na pilha: qualquer
                // try/catch DEPOIS do await ganhava um handler frame órfão
                // ("Inconsistent stackmap frames at branch target", medido no
                // pouso da fatia 3 do motor — §527). Descartar o Object aqui,
                // no único ponto onde a pilha JVM e o modelo divergem.
                mv.visitInsn(POP);
            } else if ("kof_list_reduce".equals(kc.methodName()) && isPrimitiveType(kc.returnType())) {
                emitUnboxIfPrimitive(mv, kc.returnType());
            } else if ("kof_list_reduce".equals(kc.methodName())
                    && kc.returnType() instanceof Type.ClassType rt) {
                // #394: reduce de String (ou qualquer classe) num método de
                // retorno declarado — o generic :35 exclui String (outros
                // hoists dependem disso), e o `areturn` recebia o Object cru
                // do runtime → VerifyError "Bad return type". CHECKCAST só no
                // reduce: valor String real passa, valor errado morre alto.
                mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toInternalName(rt.packageName(), rt.name()));
            } else if (kc.methodName().startsWith("kof_ffi") && isPrimitiveType(kc.returnType())) {
                emitUnboxIfPrimitive(mv, kc.returnType());
            } else if (kc.methodName().startsWith("kof_ffi") && BuiltinTypes.isString(kc.returnType())) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/String");
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
                // §374/#553 — coleção BARE (local `List xs = listOf(1)` ou
                // campo `List xs` pos-§373): elemType e Unknown (receiver sem
                // type-args) e o `int` cru do argumento chegava ao
                // ArrayList.add(Object) → VerifyError no LOAD (mesma raiz do
                // bug 35). MESMO fallback do contains (:139): boxear pelo
                // tipo do ARGUMENTO; com elemType tipado o resultado e
                // identico (typer garante compatibilidade, SEM056) — colecoes
                // tipadas intocadas.
                Type addT = elemType instanceof Type.UnknownType && !kc.parameterTypes().isEmpty()
                        ? kc.parameterTypes().get(0) : elemType;
                emitBoxIfPrimitive(mv, addT);
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
            // #1120: insert(index, value) — the positional insert the 05/11
            // batch approved (ArrayList.add(int,E) shifts the tail). Box by
            // ARG type; void return (nothing to pop — the emitter knows).
            case "kof_list_insert" -> {
                Type valT = kc.parameterTypes().size() > 1 ? kc.parameterTypes().get(1) : elemType;
                emitBoxIfPrimitive(mv, valT);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add",
                        "(ILjava/lang/Object;)V", false);
            }
            case "kof_list_set" -> {
                // §374/#553 — box-by-arg no VALOR (ultimo parametro; o
                // indice e sempre int). Guardado em Unknown: colecao tipada
                // mantem exatamente o emit de antes.
                Type setT = elemType instanceof Type.UnknownType && kc.parameterTypes().size() > 1
                        ? kc.parameterTypes().get(1) : elemType;
                emitBoxIfPrimitive(mv, setT);
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
            // #382 — indexOf/lastIndexOf: java.util.List busca por conteúdo
            // (box pelo tipo do ARG, bug 35 como kof_list_contains — o tag
            // extra do call-site é descartado: no JVM equals já é conteúdo).
            case "kof_list_index_of", "kof_list_last_index_of" -> {
                if (kc.parameterTypes().size() > 1) {
                    mv.visitInsn(POP);
                }
                Type argT = kc.parameterTypes().isEmpty() ? elemType
                        : kc.parameterTypes().get(0);
                emitBoxIfPrimitive(mv, argT);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "kof_list_index_of".equals(kc.methodName()) ? "indexOf" : "lastIndexOf",
                        "(Ljava/lang/Object;)I", false);
            }
            // #382 — addAll(Collection): bool "mudou?". O argumento é a outra
            // ArrayList (List do mesmo elemento — homogeneidade §126).
            case "kof_list_add_all" ->
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "addAll",
                            "(Ljava/util/Collection;)Z", false);
            // #382 — subList(from,to): ArrayList.subList devolve VIEW; o IR
            // declara List materializada (get/set/iteration em todos os
            // alvos) — embrulha em ArrayList cópia (mesmo shape do
            // kof_map_keys) para o verifier nunca ver SubList num slot
            // tipado como ArrayList.
            case "kof_list_sub_list" -> {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "subList",
                        "(II)Ljava/util/List;", false);
                mv.visitTypeInsn(NEW, "java/util/ArrayList");
                mv.visitInsn(DUP_X1);
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>",
                        "(Ljava/util/Collection;)V", false);
            }
            // #382 — sort(): ordem natural (Comparator null), mesma
            // restrição SEM097/NAT001 do lowerer compartilhado; o tag
            // (destino nativo) é descartada aqui.
            case "kof_list_sort" -> {
                if (!kc.parameterTypes().isEmpty()) {
                    mv.visitInsn(POP);
                }
                mv.visitInsn(ACONST_NULL);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "sort",
                        "(Ljava/util/Comparator;)V", false);
            }
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
                // §374/#553 — mesma familia: canal BARE (`Channel ch = ...`
                // sem type-args) + send de primitivo = int cru no
                // LinkedBlockingQueue.put(Object).
                Type sendT = elemType instanceof Type.UnknownType && !kc.parameterTypes().isEmpty()
                        ? kc.parameterTypes().get(0) : elemType;
                emitBoxIfPrimitive(mv, sendT);
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
                // #1120: the call site may carry the Native type tag as a
                // second parameter (the same convention kof_list_contains
                // uses) — the JVM's HashSet.equals needs no tag, so pop it.
                if (kc.parameterTypes().size() > 1) {
                    mv.visitInsn(POP);
                }
                Type argT = kc.parameterTypes().isEmpty() ? elemType
                        : kc.parameterTypes().get(0);
                emitBoxIfPrimitive(mv, argT);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "contains", "(Ljava/lang/Object;)Z", false);
            }
            case "kof_set_remove" -> {
                emitBoxIfPrimitive(mv, elemType);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "remove", "(Ljava/lang/Object;)Z", false);
            }
            // #1120: containsAll(other) — HashSet.containsAll(Collection) is
            // exactly the contract (every element of other is present).
            case "kof_set_contains_all" ->
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "containsAll",
                            "(Ljava/util/Collection;)Z", false);
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
        // #632: the same erasure survives a nullable wrapper, so `List<View?>`
        // still stores an int that needs `Integer.valueOf`.
        if (isHandleType(primitive)) {
            return "java/lang/Integer";
        }
        return null;
    }

    private static boolean isHandleType(Type type) {
        Type inner = type instanceof Type.NullableType nt ? nt.inner() : type;
        return KofUi.isUiType(inner) || KofMedia.isHandleType(inner);
    }

    static void emitBoxIfPrimitive(MethodVisitor mv, Type type) {
        String boxed = boxedClassNameFor(type);
        if (boxed != null) {
            String desc = JvmTypeMapper.toDescriptor(type);
            if ("char".equals(typeName(type)) || "Char".equals(typeName(type))) desc = "I";
            if (isHandleType(type)) desc = "I";
            mv.visitMethodInsn(INVOKESTATIC, boxed, "valueOf", "(" + desc + ")L" + boxed + ";", false);
        }
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

    static public boolean isPrimitiveType(Type type) {
        if (type instanceof Type.NullableType nt) return isPrimitiveType(nt.inner());
        return type instanceof Type.PrimitiveType pt && !"void".equals(pt.name());
    }

    public static boolean isPrimitiveOf(Type type, String name) {
        if (type instanceof Type.NullableType nt) return isPrimitiveOf(nt.inner(), name);
        return type instanceof Type.PrimitiveType pt && (pt.name().equals(name) || pt.name().equals(capitalize(name)));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String typeName(Type type) {
        return type instanceof Type.PrimitiveType pt ? pt.name() : "";
    }

    private static Type listElementType(Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }
}
