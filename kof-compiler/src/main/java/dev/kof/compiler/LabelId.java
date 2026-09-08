package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record LabelId(int id) {
    private static int counter = 0;
    static LabelId create() { return new LabelId(counter++); }
    static void reset() { counter = 0; }
}
