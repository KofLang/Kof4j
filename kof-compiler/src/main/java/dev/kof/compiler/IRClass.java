package dev.kof.compiler;

import java.util.List;
import java.util.Map;

/**
 * Classe da IR. {@code isRecord} (#818) registra que a DECLARAÇÃO de origem era
 * um `record` — antes essa informação só existia implicitamente na superclasse
 * `java/lang/Record`, e um `record` que ESTENDE uma classe do usuário (forma
 * aceita pela gramática) a perdia, fazendo o backend tratá-lo como classe comum
 * (toString/equals de identidade, em silêncio).
 */
public record IRClass(String name, String superName, List<String> interfaces,
               int accessFlags, List<IRField> fields, List<IRMethod> methods,
               List<String> innerClasses, String signature, int typeId,
               List<IRAnnotation> annotations, boolean isRecord,
               boolean isValueRecord) {

    /** Compatibilidade: as fábricas que não são de record passam isRecord=false. */
    public IRClass(String name, String superName, List<String> interfaces,
            int accessFlags, List<IRField> fields, List<IRMethod> methods,
            List<String> innerClasses, String signature, int typeId,
            List<IRAnnotation> annotations) {
        this(name, superName, interfaces, accessFlags, fields, methods,
                innerClasses, signature, typeId, annotations, false, false);
    }

    /** Compatibilidade: os sites de `record` que ainda não consultam a flag. */
    public IRClass(String name, String superName, List<String> interfaces,
            int accessFlags, List<IRField> fields, List<IRMethod> methods,
            List<String> innerClasses, String signature, int typeId,
            List<IRAnnotation> annotations, boolean isRecord) {
        this(name, superName, interfaces, accessFlags, fields, methods,
                innerClasses, signature, typeId, annotations, isRecord, false);
    }

    public IRClass(String name, String superName, List<String> interfaces,
            int accessFlags, List<IRField> fields, List<IRMethod> methods,
            List<String> innerClasses, String signature, int typeId) {
        this(name, superName, interfaces, accessFlags, fields, methods,
                innerClasses, signature, typeId, List.of(), false);
    }
}
