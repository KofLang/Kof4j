package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofCall(Type ownerType, String methodName, List<Type> parameterTypes,
               Type returnType, KofCallKind kind) implements KofOperation {
}
