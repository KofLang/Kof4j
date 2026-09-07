package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofJump(LabelId target) implements KofOperation {
}
