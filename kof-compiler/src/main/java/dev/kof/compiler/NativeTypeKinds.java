package dev.kof.compiler;

/**
 * FASE 3 (REFACTOR-500): predicados de tipo usados pelo backend nativo
 * (x86_64 e cross). Puros sobre Type — DRY: consumidos por NativeBackend,
 * NativeX86Arith e afins.
 */
final class NativeTypeKinds {

    private NativeTypeKinds() {}

    static boolean isFloatType(Type t) {
        return t instanceof Type.PrimitiveType pt && "float".equals(Type.canonicalPrimitiveName(pt.name()));
    }
    static boolean isDoubleType(Type t) {
        return t instanceof Type.PrimitiveType pt && "double".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    /** Slots de frame que um tipo de PARAM ocupa (espelha isDoubleWidth do
     * CompilerDriver — os índices locais do IR usam essa convenção). */
    static boolean isDoubleWidthSlot(Type t) {
        if (t instanceof Type.PrimitiveType pt) {
            String n = Type.canonicalPrimitiveName(pt.name());
            return "long".equals(n) || "double".equals(n);
        }
        return false;
    }
    static boolean isInt32Type(Type t) {
        return t instanceof Type.PrimitiveType pt && "int".equals(Type.canonicalPrimitiveName(pt.name()));
    }
}
