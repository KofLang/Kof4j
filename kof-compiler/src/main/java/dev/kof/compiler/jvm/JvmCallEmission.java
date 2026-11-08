package dev.kof.compiler.jvm;

import dev.kof.compiler.ExternalClasspath;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.JdkReflectionResolver;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.TypeMetrics;
import dev.kof.compiler.Type;
import static org.objectweb.asm.Opcodes.*;

/**
 * JvmCallEmission — a emissão do `case KofCall` do JvmOpEmitter, extraída no
 * MESMO commit que a flag itf de estático-em-interface JDK (#1093 fatia 1:
 * o arquivo cruzou 597→605 no gate ≤600; a família "chamada" é autocontida:
 * box/unbox, runtime fns, coleções e o ramo externo com o alinhamento
 * erasure+CHECKCAST da #1124 e a flag de dono-interface).
 */
final class JvmCallEmission {

    private JvmCallEmission() {}

    static void emit(JvmBackend ctx, JvmOpEmitter.OpContext c, KofCall kc) {
            if (("kof_box".equals(kc.methodName()) || "kof_unbox".equals(kc.methodName()))) {
                if ("kof_box".equals(kc.methodName())) {
                    Type boxed = kc.ownerType();
                    String boxedName = JvmTypeMapper.toInternalName(
                            boxed instanceof Type.ClassType ct ? ct.packageName() : "java.lang",
                            boxed instanceof Type.ClassType ct ? ct.name() : "Object");
                    c.mv().visitMethodInsn(INVOKESTATIC, boxedName, "valueOf",
                            "(" + JvmTypeMapper.toDescriptor(kc.parameterTypes().get(0)) + ")L" + boxedName + ";", false);
                } else {
                    Type boxed = kc.parameterTypes().get(0);
                    String boxedName = JvmTypeMapper.toInternalName(
                            boxed instanceof Type.ClassType ct ? ct.packageName() : "java.lang",
                            boxed instanceof Type.ClassType ct ? ct.name() : "Object");
                    c.mv().visitTypeInsn(CHECKCAST, boxedName);
                    c.mv().visitMethodInsn(INVOKEVIRTUAL, boxedName, JvmOpCollections.unboxMethodName(boxed),
                            "()" + JvmTypeMapper.toDescriptor(kc.returnType()), false);
                }
            }
            else if (JvmRuntime.hasRuntimeFn(kc.methodName())) {
                JvmOpCollections.emitKofRuntimeCall(ctx, c.mv(), kc);
            }
            else if (BuiltinTypes.isString(kc.ownerType())
            && ("kof_string_concat".equals(kc.methodName()) || "kof_string_equals".equals(kc.methodName()))) {
                JvmOpCollections.emitStringCall(c.mv(), kc);
            }
            else if (BuiltinTypes.isList(kc.ownerType())) {
                JvmOpCollections.emitListCall(c.mv(), kc);
            }
            else if (BuiltinTypes.isChannel(kc.ownerType())) {
                JvmOpCollections.emitChannelCall(c.mv(), kc);
            }
            else if (BuiltinTypes.isMap(kc.ownerType())) {
                JvmOpMap.emitMapCall(ctx, c.mv(), kc);
            }
            else if (BuiltinTypes.isSet(kc.ownerType())) {
                JvmOpCollections.emitSetCall(c.mv(), kc);
            }
            else {
                // #368 (família §355): dono TypeVariable (T cru num parâmetro
                // genérico — `item.toString()` com item: T) chegava cru e
                // virava `invokevirtual "".toString` → ClassFormatError
                // (Illegal class name ""). Erasure JVM: dono = bound
                // (`T: Animal` → Animal, dispatch virtual real) ou Object.
                String tvOwner = kc.ownerType() instanceof Type.TypeVariable
                        || kc.ownerType() instanceof Type.WildcardType
                        ? JvmTypeMapper.erasureInternalName(kc.ownerType()) : null;
                String owner = "";
                if (kc.ownerType() instanceof Type.ClassType ct) {
                    owner = JvmTypeMapper.toInternalName(ct.packageName(), ct.name());
                } else if (tvOwner != null) {
                    owner = tvOwner;
                }
                String desc = JvmTypeMapper.toMethodDescriptor(kc.returnType(), kc.parameterTypes());
                // #1124: chamada de instância sobre dono JDK com retorno
                // SUBSTITUÍDO (Optional<String>.get() → String): o descritor
                // emitido tem de ser o APAGADO da classe JDK (get() possui
                // ()Ljava/lang/Object; — espelho do §706 das classes Kof),
                // senão o resolution do invokevirtual não encontra o método
                // e a chamada morre em runtime. A referência volta com
                // CHECKCAST ao tipo substituído — que é o que o CONSUMIDOR
                // semântico (store, chamada em cadeia, boxing do println)
                // já espera na pilha.
                String castAfter = null;
                String unboxAfter = null;
                var extCp = ctx.externalTypes();
                boolean jdkOrExternalOwner = owner.contains("/")
                        && (JdkReflectionResolver.isJdkClass(owner)
                                || (extCp != null && extCp.knows(owner)));
                if ((kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE)
                        && jdkOrExternalOwner) {
                    var erasedSig = JdkReflectionResolver.resolveJdkMethod(
                            owner, kc.methodName(), kc.parameterTypes().size());
                    if (erasedSig == null && extCp != null) {
                        // #1093 fatia 2: dono do CLASSPATH EXTERNO — o descritor
                        // apagado vem do parse de bytes/reflexão do classpath.
                        erasedSig = extCp.resolveMethod(
                                owner, kc.methodName(), kc.parameterTypes().size());
                    }
                    if (erasedSig != null && !erasedSig.returnDescriptor().equals(
                            JvmTypeMapper.toDescriptor(kc.returnType()))
                            && kc.returnType() instanceof Type.ClassType subCt
                            && subCt.typeArguments().isEmpty()) {
                        Type erasedRet = ExternalClasspath.typeFromDescriptor(
                                erasedSig.returnDescriptor());
                        desc = JvmTypeMapper.toMethodDescriptor(erasedRet, kc.parameterTypes());
                        castAfter = JvmTypeMapper.toInternalName(subCt.packageName(), subCt.name());
                    }
                    // #1124 fatia 3: retorno substituído PRIMITIVO sobre um sig
                    // apagado de REFERÊNCIA (Optional<Int>.get() → Int; o
                    // runtime devolve o box Integer) — o descritor é o APAGADO
                    // e o box é aberto NO PONTO DE CHAMADA (CHECKCAST boxed +
                    // unbox): a pilha sai int CRU, o tipo semântico e o da
                    // pilha CONCORDAM em todo consumidor
                    // (aritmética/comparação/store/println — as faces padrão
                    // de Int).
                    if (erasedSig != null && !erasedSig.returnDescriptor().equals(
                            JvmTypeMapper.toDescriptor(kc.returnType()))
                            && kc.returnType() instanceof Type.PrimitiveType unPt
                            && (erasedSig.returnDescriptor().startsWith("L")
                                    || erasedSig.returnDescriptor().startsWith("["))) {
                        Type erasedRet = ExternalClasspath.typeFromDescriptor(
                                erasedSig.returnDescriptor());
                        desc = JvmTypeMapper.toMethodDescriptor(erasedRet, kc.parameterTypes());
                        Type boxedT = TypeMetrics.boxedTypeFor(unPt);
                        castAfter = boxedT instanceof Type.ClassType bc
                                ? JvmTypeMapper.toInternalName(bc.packageName(), bc.name()) : null;
                        unboxAfter = JvmOpCollections.unboxMethodName(unPt);
                    }
                }
                boolean isInterfaceOwner = false;
                if (c.module() != null) {
                    for (IRClass irc : c.module().classes()) {
                        if (irc.name().equals(owner)) {
                            isInterfaceOwner = (irc.accessFlags() & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0;
                            break;
                        }
                    }
                }
                // Família #1093: um estático de INTERFACE JDK externa
                // (Path.of — Path é interface) exige a flag itf do
                // invokestatic (senão IncompatibleClassChangeError
                // "must be InterfaceMethodref"). O scan acima só enxerga
                // classes Kof-locais; o dono JDK interface completa.
                if (!isInterfaceOwner && JdkReflectionResolver.isJdkInterface(owner)) {
                    isInterfaceOwner = true;
                }
                // #1093 fatia 2: interface do CLASSPATH EXTERNO (S3Builder
                // implements Builder) — a flag itf vale igual.
                if (!isInterfaceOwner && extCp != null && extCp.isInterface(owner)) {
                    isInterfaceOwner = true;
                }
                switch (kc.kind()) {
                    case INSTANCE -> c.mv().visitMethodInsn(INVOKEVIRTUAL, owner, kc.methodName(), desc, false);
                    case STATIC -> c.mv().visitMethodInsn(INVOKESTATIC, owner, kc.methodName(), desc, isInterfaceOwner);
                    case CONSTRUCTOR -> c.mv().visitMethodInsn(INVOKESPECIAL, owner, kc.methodName(), desc, false);
                    case FUNCTION -> c.mv().visitMethodInsn(INVOKESTATIC, owner, kc.methodName(), desc, false);
                    case INTERFACE -> c.mv().visitMethodInsn(INVOKEINTERFACE, owner, kc.methodName(), desc, true);
                    case SUPER -> c.mv().visitMethodInsn(INVOKESPECIAL, owner, kc.methodName(), desc, false);
                }
                if (castAfter != null) {
                    c.mv().visitTypeInsn(CHECKCAST, castAfter);
                    if (unboxAfter != null) {
                        c.mv().visitMethodInsn(INVOKEVIRTUAL, castAfter, unboxAfter,
                                "()" + JvmTypeMapper.toDescriptor(kc.returnType()), false);
                    }
                }
            }

    }
}
