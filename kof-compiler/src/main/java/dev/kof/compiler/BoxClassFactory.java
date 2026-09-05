package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cria as classes de box mutável (usadas para captura de variáveis mutadas
 * por lambdas). Encapsula o contador de nomes e os mapas de tipo por box.
 */
final class BoxClassFactory {

    private final Set<String> boxClassNames = new HashSet<>();
    private final Map<String, Type> boxValueTypes = new HashMap<>();
    private int boxCounter = 0;

    String createBoxClass(Type valueType, List<IRClass> syntheticClasses, int lambdaCounter) {
        String boxName = "Box" + (boxCounter++);
        boxClassNames.add(boxName);
        boxValueTypes.put(boxName, valueType);
        Type boxType = new Type.ClassType("", boxName, List.of());
        List<IRField> fields = List.of(new IRField("value", valueType, AccessFlags.PUBLIC, null));
        List<KofOperation> ctorOps = new ArrayList<>();
        ctorOps.add(new KofReturnVoid());
        List<IRLocalVariable> ctorLocals = List.of(new IRLocalVariable(0, "this", boxType));
        IRMethod ctor = new IRMethod("<init>", Type.PrimitiveType.VOID, List.of(),
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ctorOps)), ctorLocals);
        IRClass cls = new IRClass(boxName, "java/lang/Object", List.of(),
                AccessFlags.PUBLIC | AccessFlags.SUPER, fields,
                List.of(ctor), List.of(), null, 300 + lambdaCounter);
        syntheticClasses.add(cls);
        return boxName;
    }

    boolean isBoxType(Type type) {
        return type instanceof Type.ClassType ct && boxClassNames.contains(ct.name());
    }

    Type boxValueType(Type boxType) {
        if (boxType instanceof Type.ClassType ct) {
            return boxValueTypes.get(ct.name());
        }
        return null;
    }
}