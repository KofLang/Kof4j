package dev.kof.compiler;

import java.util.List;

/**
 * {@code interfaceOwner} marks a STATIC call whose external owner is an
 * interface ({@code Path.of}): the JVM requires an InterfaceMethodref there.
 */
public record KofCall(Type ownerType, String methodName, List<Type> parameterTypes,
               Type returnType, KofCallKind kind, boolean interfaceOwner) implements KofOperation {

    public KofCall(Type ownerType, String methodName, List<Type> parameterTypes,
                   Type returnType, KofCallKind kind) {
        this(ownerType, methodName, parameterTypes, returnType, kind, false);
    }
}
