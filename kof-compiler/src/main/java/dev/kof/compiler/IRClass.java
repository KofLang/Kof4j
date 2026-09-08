package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record IRClass(String name, String superName, List<String> interfaces,
               int accessFlags, List<IRField> fields, List<IRMethod> methods,
               List<String> innerClasses, String signature, int typeId,
               List<IRAnnotation> annotations) {

    IRClass(String name, String superName, List<String> interfaces,
            int accessFlags, List<IRField> fields, List<IRMethod> methods,
            List<String> innerClasses, String signature, int typeId) {
        this(name, superName, interfaces, accessFlags, fields, methods,
                innerClasses, signature, typeId, List.of());
    }
}
