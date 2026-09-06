package dev.kof.compiler;

import java.util.List;

/**
 * Emissão de annotations JVM (REFACTOR-500 FASE 8 — extraído de JvmBackend).
 * Sem estado: a escolha do visitor vem de fora (class/field/method).
 */
final class JvmAnnotations {

    private JvmAnnotations() {}

    /**
     * Pacotes com retenção CLASS/SOURCE (não visíveis em runtime).
     * Tudo que não estiver aqui é emitido como RuntimeVisible — a escolha
     * conservadora para interop (frameworks Android/JUnit leem em runtime).
     */
    private static final List<String> INVISIBLE_PREFIXES = List.of(
            "java/lang/Override",
            "java/lang/SuppressWarnings",
            "androidx/annotation/",
            "javax/annotation/",
            "org/jetbrains/annotations/",
            "edu/umd/cs/findbugs/annotations/"
    );

    private static boolean retentionIsVisible(String internalName) {
        if (internalName == null) return true;
        for (String prefix : INVISIBLE_PREFIXES) {
            if (internalName.equals(prefix) || internalName.startsWith(prefix)) return false;
        }
        return true;
    }

    interface AnnotationVisitorFactory {
        org.objectweb.asm.AnnotationVisitor create(String descriptor, boolean visible);
    }

    static void emitAnnotations(AnnotationVisitorFactory factory, List<IRAnnotation> annotations) {
        if (annotations == null) return;
        for (IRAnnotation anno : annotations) {
            String desc = "L" + anno.name() + ";";
            var av = factory.create(desc, retentionIsVisible(anno.name()));
            if (av != null) {
                for (var e : anno.values().entrySet()) {
                    // forma curta @Name("x"): chave null → elemento "value"
                    String key = e.getKey() != null ? e.getKey() : "value";
                    emitAnnotationValues(av, key, e.getValue());
                }
                av.visitEnd();
            }
        }
    }

    /**
     * Emite um valor de annotation: constante simples ou array {v1, v2}.
     */
    private static void emitAnnotationValues(org.objectweb.asm.AnnotationVisitor av,
                                             String key, Object value) {
        if (value instanceof List<?> items) {
            var arr = av.visitArray(key);
            for (Object item : items) {
                if (item instanceof IRClassConstant cc) {
                    arr.visit(null, org.objectweb.asm.Type.getType("L" + cc.internalName() + ";"));
                } else if (item instanceof IREnumConstant ec) {
                    arr.visitEnum(null, "L" + ec.internalName() + ";", ec.constant());
                } else {
                    arr.visit(null, asmValue(item));
                }
            }
            arr.visitEnd();
            return;
        }
        if (value instanceof IRClassConstant cc) {
            av.visit(key, org.objectweb.asm.Type.getType("L" + cc.internalName() + ";"));
            return;
        }
        if (value instanceof IREnumConstant ec) {
            av.visitEnum(key, "L" + ec.internalName() + ";", ec.constant());
            return;
        }
        av.visit(key, asmValue(value));
    }

    private static Object asmValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double
                || value instanceof Character) return value;
        return String.valueOf(value);
    }
}
