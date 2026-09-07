package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofDebugInfo(java.util.Map<KofOperation, SourcePosition> positions) {
    static final KofDebugInfo EMPTY = new KofDebugInfo(java.util.Map.of());
}
