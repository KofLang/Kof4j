package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record IRMethod(String name, Type returnType, List<Type> parameterTypes, int accessFlags,
                List<String> thrownExceptions, List<IRBasicBlock> basicBlocks,
                List<IRLocalVariable> localVariables, KofDebugInfo debugInfo,
                List<IRAnnotation> annotations, List<List<IRAnnotation>> parameterAnnotations) {

    IRMethod(String name, Type returnType, List<Type> parameterTypes, int accessFlags,
             List<String> thrownExceptions, List<IRBasicBlock> basicBlocks,
             List<IRLocalVariable> localVariables, KofDebugInfo debugInfo) {
        this(name, returnType, parameterTypes, accessFlags, thrownExceptions,
                basicBlocks, localVariables, debugInfo, List.of(), List.of());
    }

    IRMethod(String name, Type returnType, List<Type> parameterTypes, int accessFlags,
             List<String> thrownExceptions, List<IRBasicBlock> basicBlocks,
             List<IRLocalVariable> localVariables) {
        this(name, returnType, parameterTypes, accessFlags, thrownExceptions,
                basicBlocks, localVariables, KofDebugInfo.EMPTY);
    }
}
