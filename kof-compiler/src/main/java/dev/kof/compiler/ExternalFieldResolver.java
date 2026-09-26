package dev.kof.compiler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Resolucao de CAMPOS (descritores) em classes externas — extraida VERBATIM
 * do ExternalClasspath (gate &lt;=500; rule 3 = estrutura, nao comportamento).
 * O lock continua no chamador ({@code ExternalClasspath} passa os mapas ja
 * sob {@code synchronized}); o estado e' explicito nos parametros.
 */
final class ExternalFieldResolver {

    private ExternalFieldResolver() {}

    static String resolveFieldType(boolean loaded, Map<String, byte[]> classBytes,
                                   List<String> loadWarnings, UnaryOperator<String> superOf,
                                   String ownerInternalName, String fieldName) {
        if (!loaded || ownerInternalName == null) return null;
        String direct = findFieldDeclared(classBytes, loadWarnings, ownerInternalName, fieldName, 0);
        if (direct != null) return direct;
        String sup = superOf.apply(ownerInternalName);
        int hops = 0;
        while (sup != null && !sup.equals("java/lang/Object") && hops++ < 32) {
            if (!classBytes.containsKey(sup)) {
                loadWarnings.add("superclass '" + sup + "' of '" + ownerInternalName
                        + "' is not on the external classpath — inherited field '"
                        + fieldName + "' may not resolve");
                return null;
            }
            String inherited = findFieldDeclared(classBytes, loadWarnings, sup, fieldName, 0);
            if (inherited != null) return inherited;
            sup = superOf.apply(sup);
        }
        return null;
    }

    static String resolveStaticFieldType(boolean loaded, Map<String, byte[]> classBytes,
                                         UnaryOperator<String> superOf,
                                         String ownerInternalName, String fieldName) {
        if (ownerInternalName == null) return null;
        if (loaded) {
            String direct = findFieldDeclaredStatic(classBytes, ownerInternalName, fieldName, 0);
            if (direct != null) return direct;
            String sup = superOf.apply(ownerInternalName);
            int hops = 0;
            while (sup != null && !sup.equals("java/lang/Object") && hops++ < 32) {
                if (!classBytes.containsKey(sup)) break;
                String inherited = findFieldDeclaredStatic(classBytes, sup, fieldName, 0);
                if (inherited != null) return inherited;
                sup = superOf.apply(sup);
            }
        }
        return JdkReflectionResolver.resolveStaticJdkFieldType(ownerInternalName, fieldName);
    }

    private static String findFieldDeclaredStatic(Map<String, byte[]> classBytes,
                                                  String internalName, String fieldName, int depth) {
        if (depth > 64) return null;
        byte[] bytes = classBytes.get(internalName);
        if (bytes == null) return null;
        final String[] hit = new String[1];
        try {
            new ClassReader(bytes).accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name,
                                                                 String descriptor,
                                                                 String signature, Object value) {
                    if (name.equals(fieldName) && hit[0] == null
                            && (access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0) {
                        hit[0] = descriptor;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            return hit[0];
        } catch (Exception e) {
            return null;
        }
    }

    private static String findFieldDeclared(Map<String, byte[]> classBytes, List<String> loadWarnings,
                                            String internalName, String fieldName, int depth) {
        if (depth > 64) return null;
        byte[] bytes = classBytes.get(internalName);
        if (bytes == null) return null;
        final String[] hit = new String[1];
        try {
            new ClassReader(bytes).accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name,
                                                                 String descriptor,
                                                                 String signature, Object value) {
                    if (name.equals(fieldName) && hit[0] == null) hit[0] = descriptor;
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            return hit[0];
        } catch (Exception e) {
            loadWarnings.add("class " + internalName + " could not be parsed: " + e.getMessage());
            return null;
        }
    }
}
