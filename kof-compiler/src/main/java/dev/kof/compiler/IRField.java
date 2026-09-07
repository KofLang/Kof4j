package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record IRField(String name, Type type, int accessFlags, Object initialValue,
               List<IRAnnotation> annotations) {

    IRField(String name, Type type, int accessFlags, Object initialValue) {
        this(name, type, accessFlags, initialValue, List.of());
    }
}
