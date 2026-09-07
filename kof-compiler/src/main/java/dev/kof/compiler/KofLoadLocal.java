package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofLoadLocal(Type type, int index) implements KofOperation {
}
