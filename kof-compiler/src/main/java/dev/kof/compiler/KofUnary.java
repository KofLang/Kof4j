package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofUnary(KofUnaryOp op, Type operandType) implements KofOperation {
}
