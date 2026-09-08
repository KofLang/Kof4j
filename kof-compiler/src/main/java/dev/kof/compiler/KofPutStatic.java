package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofPutStatic(Type ownerType, String name, Type fieldType) implements KofOperation {
}
