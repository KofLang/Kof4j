package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofConditionalJump(KofComparison comparison, Type operandType, LabelId trueLabel, LabelId falseLabel) implements KofOperation {
    KofConditionalJump(KofComparison comparison, LabelId trueLabel, LabelId falseLabel) {
        this(comparison, Type.PrimitiveType.INT, trueLabel, falseLabel);
    }
}
