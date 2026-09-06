package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de anotações (AnnotationNode -> IRAnnotation) e dobra de valores.
 */
final class CompilerAnnotations {

    private CompilerAnnotations() {}

    static final class ParseSentinel {
        static final ParseSentinel INSTANCE = new ParseSentinel();
    }



    static List<IRAnnotation> lowerAnnotations(CompilerDriver driver, List<AnnotationNode> annos) {
        if (annos == null || annos.isEmpty()) return List.of();
        List<IRAnnotation> out = new ArrayList<>();
        for (AnnotationNode anno : annos) {
            java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
            for (AnnotationPair pair : anno.pairs()) {
                String key = pair.key() != null ? pair.key()
                        : (anno.pairs().size() == 1 ? "value" : null);
                if (key != null) {
                    Object folded = CompilerAnnotations.foldAnnotationValue(driver, pair.value());
                    if (!(folded instanceof ParseSentinel)) values.put(key, folded);
                }
            }
            out.add(new IRAnnotation(CompilerAnnotations.resolveAnnotationInternalName(driver, anno.name()), values));
        }
        return out;
    }

    static String externalOrLocalInternalName(CompilerDriver driver, String name) {
        Type q = CompilerTypes.qualifyViaImports(name, driver.currentUnit);
        if (q instanceof Type.ClassType qt && !qt.packageName().isEmpty()) {
            return qt.internalName();
        }
        return driver.toInternalName("", name);
    }

    static Object foldAnnotationValue(CompilerDriver driver, Object value) {
        if (value instanceof AnnotationClassRef ref) {
            return new IRClassConstant(CompilerAnnotations.resolveAnnotationInternalName(driver, ref.typeName()));
        }
        if (value instanceof AnnotationEnumRef ref) {
            int lastDot = ref.qualifiedConstant().lastIndexOf('.');
            if (lastDot > 0 && driver.externalClasspath != null) {
                String internal = CompilerAnnotations.resolveAnnotationInternalName(driver,
                        ref.qualifiedConstant().substring(0, lastDot));
                String constant = ref.qualifiedConstant().substring(lastDot + 1);
                if (driver.externalClasspath.knows(internal)
                        && driver.externalClasspath.isEnum(internal)
                        && driver.externalClasspath.hasEnumConstant(internal, constant)) {
                    return new IREnumConstant(internal, constant);
                }
            }
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error("", 0, 0, 0,
                        "enum constant '" + ref.qualifiedConstant()
                                + "' could not be resolved from the external classpath",
                        "ANNOT001");
            }
            return ParseSentinel.INSTANCE;
        }
        if (value instanceof List<?> items) {
            List<Object> folded = new ArrayList<>();
            for (Object item : items) folded.add(CompilerAnnotations.foldAnnotationValue(driver, item));
            return folded;
        }
        return value;
    }

    static String resolveAnnotationInternalName(CompilerDriver driver, String name) {
        if (name.contains(".")) return name.replace('.', '/');
        switch (name) {
            case "Override": return "java/lang/Override";
            case "Deprecated": return "java/lang/Deprecated";
            case "FunctionalInterface": return "java/lang/FunctionalInterface";
            case "SafeVarargs": return "java/lang/SafeVarargs";
            case "SuppressWarnings": return "java/lang/SuppressWarnings";
        }
        if (driver.currentUnit != null) {
            for (String imp : driver.currentUnit.imports()) {
                if (!"*.kof".equals(imp) && imp.endsWith("." + name)) {
                    return imp.replace('.', '/');
                }
            }
        }
        return name;
    }

    static List<List<IRAnnotation>> lowerParameterAnnotations(CompilerDriver driver,
                                           List<FormalParameterNode> params) {
        List<List<IRAnnotation>> out = new ArrayList<>();
        boolean any = false;
        for (FormalParameterNode p : params) {
            List<IRAnnotation> annos = CompilerAnnotations.lowerAnnotations(driver, p.annotations());
            if (!annos.isEmpty()) any = true;
            out.add(annos);
        }
        return any ? out : List.of();
    }
}