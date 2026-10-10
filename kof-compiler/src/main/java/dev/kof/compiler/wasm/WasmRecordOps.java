package dev.kof.compiler.wasm;

import dev.kof.compiler.Type;

import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

/** Despacho de largura de campo de record no bump heap WASI (15.3d inc2). */
final class WasmRecordOps {

    /** Shape-only: campo de classe = handle i32 (aninhamento; o guard do
     * KofNewObject valida que a classe e um record conhecido). */
    static boolean isRecordClassField(Type t) {
        return t instanceof Type.ClassType;
    }


    private WasmRecordOps() {
    }

    static boolean isStringField(Type t) {
        if (t instanceof Type.ClassType ct) {
            String n = ct.name().replace('/', '.').replace("$", ".");
            return "String".equals(n) || "java.lang.String".equals(n) || n.endsWith(".String");
        }
        return false;
    }

    static boolean isI32Field(Type t) {
        String n = typeName(t);
        return "bool".equalsIgnoreCase(n) || "boolean".equalsIgnoreCase(n) || isStringField(t);
    }

    /** Int/Long/Char empilham i64 (emitLiteral 15.2 medido); Bool/handle-String = i32. */
    static boolean isWideField(Type t) {
        return isI64Field(t) || "char".equalsIgnoreCase(typeName(t));
    }

    static boolean isF64Field(Type t) {
        return "double".equalsIgnoreCase(typeName(t));
    }

    static boolean isI64Field(Type t) {
        return t instanceof Type.PrimitiveType pt
                && ("int".equalsIgnoreCase(pt.name()) || "long".equalsIgnoreCase(pt.name()));
    }


}
