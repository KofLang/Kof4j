package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofNewObject(Type type, List<Type> argumentTypes) implements KofOperation {
}
